package com.legaltraining.web.controller.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/backup")
@EnableConfigurationProperties(OpsBackupController.Settings.class)
public class OpsBackupController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(OpsBackupController.class);
    private final JdbcTemplate jdbc;
    private final Settings settings;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @ConfigurationProperties("legal-training.backup")
    public record Settings(String root, String scriptDirectory, String mysqlBin, String powershell,
            String database, String clientOptions, String uploads,
            String restoreDatabase, String restoreClientOptions, String restoreUploads, boolean managedMedia)
    {
        boolean configured()
        {
            return present(root) && present(scriptDirectory) && present(mysqlBin)
                    && present(database) && present(clientOptions) && present(uploads);
        }

        boolean restoreConfigured()
        {
            return configured() && present(restoreDatabase) && present(restoreClientOptions)
                    && present(restoreUploads) && !database.equalsIgnoreCase(restoreDatabase);
        }

        private static boolean present(String value) { return value != null && !value.isBlank(); }
    }

    public record Policy(boolean enabled, @Min(1) @Max(8760) int fullHours,
            @Min(1) @Max(8760) int incrementalHours, @Min(1) @Max(3650) int retainDays) {}
    public record RestoreForm(@NotBlank @Pattern(regexp = "\\d{8}T\\d{6}Z-[a-f0-9]{12}") String id,
            @Pattern(regexp = "\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ") String recoverBeforeUtc) {}

    public OpsBackupController(JdbcTemplate jdbc, Settings settings)
    {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize("@ss.hasRole('admin')")
    public AjaxResult overview()
    {
        return success(Map.of("configured", settings.configured(), "restoreConfigured", settings.restoreConfigured(),
                "policy", readPolicy(), "backups", settings.configured() ? backups() : List.of(),
                "operations", jdbc.queryForList("""
                        select id,backup_type as type,status,start_time as startTime,end_time as endTime,
                               operator_id as operatorId,fail_reason as failure,file_path as backupId
                        from ops_backup_record order by id desc limit 100
                        """)));
    }

    @PutMapping("/policy")
    @PreAuthorize("@ss.hasRole('admin')")
    public AjaxResult policy(@Valid @RequestBody Policy policy)
    {
        if (policy.enabled() && !settings.configured()) throw new ServiceException("请先配置备份运行环境");
        jdbc.update("""
                update ops_backup_policy set enabled=?,full_hours=?,incremental_hours=?,retain_days=? where id=1
                """, policy.enabled(), policy.fullHours(), policy.incrementalHours(), policy.retainDays());
        return success();
    }

    @PostMapping("/{type:full|incr}")
    @PreAuthorize("@ss.hasRole('admin')")
    public AjaxResult backup(@PathVariable String type)
    {
        if (!settings.configured()) throw new ServiceException("请先配置备份运行环境");
        submit(type, getUserId(), null);
        return success();
    }

    @PostMapping("/restore")
    @PreAuthorize("@ss.hasRole('admin')")
    public AjaxResult restore(@Valid @RequestBody RestoreForm form)
    {
        if (!settings.restoreConfigured()) throw new ServiceException("请先配置独立恢复目标");
        boolean available = backups().stream().anyMatch(item -> form.id().equals(item.getString("id")));
        if (!available) throw new ServiceException("备份不存在");
        submit("restore", getUserId(), form);
        return success();
    }

    @Scheduled(fixedDelayString = "${legal-training.backup.schedule-delay-ms:60000}")
    public void schedule()
    {
        if (!settings.configured() || busy.get()) return;
        try
        {
            Policy policy = readPolicy();
            if (!policy.enabled()) return;
            if (due("full", policy.fullHours())) submit("full", null, null);
            else if (due("incr", policy.incrementalHours())) submit("incr", null, null);
        }
        catch (RuntimeException ex)
        {
            log.warn("Unable to schedule backup: {}", ex.getClass().getSimpleName());
        }
    }

    private boolean due(String type, int hours)
    {
        Long found = jdbc.queryForObject("""
                select count(*) from ops_backup_record where backup_type=? and scope=?
                and start_time>=timestampadd(hour,-?,now())
                """, Long.class, type, settings.database(), hours);
        return found == null || found == 0;
    }

    private Policy readPolicy()
    {
        return jdbc.queryForObject("""
                select enabled,full_hours,incremental_hours,retain_days from ops_backup_policy where id=1
                """, (rs, row) -> new Policy(rs.getBoolean(1),rs.getInt(2),rs.getInt(3),rs.getInt(4)));
    }

    private void submit(String type, Long operator, RestoreForm request)
    {
        if (!busy.compareAndSet(false, true)) throw new ServiceException("已有备份或恢复任务运行中");
        try
        {
            var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        insert into ops_backup_record(backup_type,scope,operator_id,file_path,status,start_time)
                        values(?,?,?,?,0,now())
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, type);
                statement.setString(2, settings.database());
                statement.setObject(3, operator);
                statement.setString(4, request == null ? null : request.id());
                return statement;
            }, holder);
            long id = java.util.Objects.requireNonNull(holder.getKey()).longValue();
            worker.submit(() -> execute(id, type, request));
        }
        catch (RuntimeException ex)
        {
            busy.set(false);
            throw ex;
        }
    }

    private void execute(long id, String type, RestoreForm request)
    {
        try
        {
            if (request == null)
            {
                boolean full = "full".equals(type);
                var args = command(full ? "v3-backup.ps1" : "v3-backup-increment.ps1", full ? "Backup" : "Increment",
                        settings.database(), settings.clientOptions(), settings.uploads());
                if (full) args.add("-WithBinlog");
                if (settings.managedMedia()) args.add("-WithMediaJournal");
                run(args);
                var retain = command("v3-backup-increment.ps1", "Prune", settings.database(),
                        settings.clientOptions(), settings.uploads());
                retain.addAll(List.of("-RetainDays", Integer.toString(readPolicy().retainDays())));
                run(retain);
            }
            else
            {
                JSONObject backup = backups().stream().filter(item -> request.id().equals(item.getString("id"))).findFirst().orElseThrow();
                boolean incremental = "incr".equals(backup.getString("type"));
                var args = command(incremental ? "v3-backup-increment.ps1" : "v3-backup.ps1", incremental ? "Replay" : "Restore",
                        settings.restoreDatabase(), settings.restoreClientOptions(), settings.restoreUploads());
                args.addAll(List.of("-BackupId", request.id()));
                if (request.recoverBeforeUtc() != null && !request.recoverBeforeUtc().isBlank())
                {
                    if (!incremental) throw new IllegalArgumentException("Recovery time requires an incremental backup");
                    args.addAll(List.of("-RecoverBeforeUtc", request.recoverBeforeUtc()));
                }
                run(args);
            }
            jdbc.update("update ops_backup_record set status=1,end_time=now() where id=?", id);
        }
        catch (Exception ex)
        {
            log.warn("Backup operation {} failed: {}", id, ex.toString());
            jdbc.update("update ops_backup_record set status=2,end_time=now(),fail_reason=? where id=?",
                    "执行失败，请检查服务器备份日志与恢复目标", id);
        }
        finally
        {
            busy.set(false);
        }
    }

    private List<String> command(String script, String action, String database, String options, String uploads)
    {
        String shell = settings.powershell() == null || settings.powershell().isBlank() ? "pwsh" : settings.powershell();
        return new ArrayList<>(List.of(shell, "-NoProfile", "-NonInteractive", "-File",
                Path.of(settings.scriptDirectory(), script).toAbsolutePath().toString(),
                "-Action", action, "-BackupRoot", settings.root(), "-MysqlBin", settings.mysqlBin(),
                "-ClientOptions", options, "-Database", database, "-UploadPath", uploads));
    }

    private void run(List<String> args) throws IOException, InterruptedException
    {
        Path output = Files.createTempFile(Path.of(settings.root()), "backup-op-", ".log");
        Process process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try
        {
            if (!process.waitFor(Duration.ofHours(2).toSeconds(), TimeUnit.SECONDS))
                throw new IOException("Backup command timed out: " + output.getFileName());
            if (process.exitValue() != 0) throw new IOException("Backup command failed: " + output.getFileName());
            Files.deleteIfExists(output);
        }
        finally
        {
            if (process.isAlive())
            {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    private List<JSONObject> backups()
    {
        try (var entries = Files.list(Path.of(settings.root())))
        {
            return entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    && path.getFileName().toString().matches("\\d{8}T\\d{6}Z-[a-f0-9]{12}"))
                    .map(this::readManifest).filter(item -> item != null && settings.database().equals(item.getString("database")))
                    .sorted(Comparator.comparing((JSONObject item) -> item.getString("id")).reversed()).toList();
        }
        catch (IOException ex)
        {
            throw new ServiceException("无法读取备份目录");
        }
    }

    private JSONObject readManifest(Path path)
    {
        try
        {
            Path manifest = path.resolve("manifest.json");
            if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) return null;
            return JSON.parseObject(Files.readString(manifest));
        }
        catch (IOException | RuntimeException ex)
        {
            return null;
        }
    }

    @PreDestroy
    void shutdown() { worker.shutdownNow(); }
}
