package com.legaltraining.web.controller.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import com.legaltraining.common.config.LegalTrainingConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.LoggerFactory;

/** The current file image is stored as rows so MySQL binary-log recovery also recovers the file image. */
@Service
public class MediaRecoveryJournal
{
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public MediaRecoveryJournal(JdbcTemplate jdbc, @Value("${legal-training.backup.managed-media:false}") boolean enabled)
    {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Transactional(rollbackFor = Exception.class)
    public void captureUpload(String publicPath) throws IOException
    {
        if (!enabled) return;
        String prefix = "/profile/";
        if (!publicPath.startsWith(prefix)) throw new IOException("Unexpected profile file path");
        Path root = profile();
        Path file = root.resolve(publicPath.substring(prefix.length())).normalize();
        try
        {
            capture(root, file, "");
        }
        catch (IOException | RuntimeException ex)
        {
            if (file.startsWith(root)) Files.deleteIfExists(file);
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void captureAuthorization(Path file) throws IOException
    {
        if (!enabled) return;
        Path root = profile();
        capture(root.resolveSibling(root.getFileName() + "-authorizations"), file, "@authorization/");
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProfileFile(Path file) throws IOException
    {
        Path root = profile();
        Path target = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!enabled) return Files.deleteIfExists(target);
        byte[] path = relative(root, target, "");
        byte[] key = digest(path);
        writeState(key, path, true, 0);
        Path staged = Files.createTempFile(target.getParent(), ".media-delete-", ".tmp");
        try
        {
            Files.move(target, staged, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex)
        {
            Files.deleteIfExists(staged);
            throw ex;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(int status)
            {
                try
                {
                    if (status == STATUS_COMMITTED) Files.deleteIfExists(staged);
                    else Files.move(staged, target);
                }
                catch (IOException ex)
                {
                    LoggerFactory.getLogger(MediaRecoveryJournal.class).error("Could not finalize staged media deletion {}", staged, ex);
                }
            }
        });
        return true;
    }

    private void capture(Path root, Path target, String prefix) throws IOException
    {
        Path file = target.toAbsolutePath().normalize();
        byte[] path = relative(root, file, prefix);
        byte[] key = digest(path);
        long length = Files.size(file);
        writeState(key, path, false, length);
        try (var input = Files.newInputStream(file))
        {
            int part = 0;
            long captured = 0;
            byte[] buffer;
            while ((buffer = input.readNBytes(64 * 1024)).length > 0)
            {
                jdbc.update("insert into ops_media_chunk(path_hash,part,content) values(?,?,?)", key, part++, buffer);
                captured += buffer.length;
            }
            if (captured != length) throw new IOException("File changed while being journaled");
        }
    }

    private void writeState(byte[] key, byte[] path, boolean deleted, long length)
    {
        jdbc.update("""
                insert into ops_media_state(path_hash,relative_path,deleted,byte_length) values(?,?,?,?)
                on duplicate key update deleted=values(deleted),byte_length=values(byte_length)
                """, key, path, deleted, length);
        jdbc.update("delete from ops_media_chunk where path_hash=?", key);
    }

    private static Path profile()
    {
        return Path.of(LegalTrainingConfig.getProfile()).toAbsolutePath().normalize();
    }

    private static byte[] relative(Path root, Path file, String prefix) throws IOException
    {
        if (!file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath()))
            throw new IOException("File is outside its profile directory");
        String relative = root.relativize(file).toString().replace('\\', '/');
        byte[] bytes = (prefix + relative).getBytes(StandardCharsets.UTF_8);
        if (relative.isBlank() || bytes.length > 2048) throw new IOException("Profile file path is invalid");
        return bytes;
    }

    private static byte[] digest(byte[] value)
    {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
