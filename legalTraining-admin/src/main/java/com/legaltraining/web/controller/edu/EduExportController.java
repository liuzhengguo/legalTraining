package com.legaltraining.web.controller.edu;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.uuid.IdUtils;
import com.legaltraining.business.edu.scope.BusinessScopeChecker;

/**
 * 数据导出（MG-04-01 成绩汇总与导出 / MG-04-02 成果包与过程台账导出）
 * 全部落 edu_export_log 留痕（MG-04 反查）；> ASYNC_THRESHOLD 行转异步线程生成。
 */
@RestController
@RequestMapping("/edu/export")
public class EduExportController extends BaseController
{
    /** 超过该行数转异步生成（MG-04-01 需求：> 5 万行转异步） */
    private static final int ASYNC_THRESHOLD = 50000;

    private final JdbcTemplate jdbc;
    private final ThreadPoolExecutor exportExecutor;
    private final BusinessScopeChecker scope;

    @Value("${legal-training.profile}")
    private String profile;

    public EduExportController(JdbcTemplate jdbc, BusinessScopeChecker scope)
    {
        this.jdbc = jdbc;
        this.scope = scope;
        this.exportExecutor = new ThreadPoolExecutor(1, 2, 60, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(100),
                r -> new Thread(r, "edu-export"));
    }

    /**
     * 成绩导出（MG-04-01）：按 taskId 或 courseId 导出成绩 CSV
     */
    @PreAuthorize("@ss.hasPermi('edu:score:export')")
    @PostMapping("/score")
    public AjaxResult score(@RequestBody Map<String, Object> filter)
    {
        Long taskId = asLong(filter.get("taskId"));
        Long courseId = asLong(filter.get("courseId"));
        if (taskId == null && courseId == null)
        {
            return error("请指定 taskId 或 courseId");
        }
        requireExportScope(taskId, courseId);
        String where = taskId != null ? " sc.task_id = " + taskId
                : " tk.course_id = " + courseId;
        String sql =
            "select tk.task_id, tk.title as taskTitle, sc.user_id, u.nick_name as studentName, " +
            "p.student_no, sc.final_score, sc.status, sc.publish_time " +
            "from edu_score sc join edu_task tk on tk.task_id = sc.task_id " +
            "join sys_user u on u.user_id = sc.user_id " +
            "left join edu_user_profile p on p.user_id = sc.user_id where" + where +
            " order by p.student_no, tk.task_id";
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        String[] header = {"任务ID", "任务名称", "学号", "姓名", "总分", "成绩状态(0待发布1已发布2已更正)", "发布时间"};
        String[] fields = {"task_id", "taskTitle", "student_no", "studentName", "final_score", "status", "publish_time"};
        return doExport("score", filter, header, fields, rows);
    }

    /**
     * 成果包导出（MG-04-02）：已批阅成果清单（正文 + 附件清单），CSV 打包清单
     */
    @PreAuthorize("@ss.hasPermi('edu:export:package')")
    @PostMapping("/package")
    public AjaxResult pkg(@RequestBody Map<String, Object> filter)
    {
        Long courseId = asLong(filter.get("courseId"));
        Long taskId = asLong(filter.get("taskId"));
        if (courseId == null && taskId == null)
        {
            return error("请指定 courseId 或 taskId");
        }
        requireExportScope(taskId, courseId);
        String where = taskId != null ? " tk.task_id = " + taskId
                : " tk.course_id = " + courseId;
        String sql =
            "select tk.title as taskTitle, p.student_no, u.nick_name as studentName, " +
            "s.version, s.content, s.status, s.submit_time, " +
            "(select group_concat(concat(f.file_name, '(', f.file_ext, ')') separator '; ') " +
            "   from edu_submission_file f where f.submission_id = s.submission_id) as files " +
            "from edu_submission s join edu_task tk on tk.task_id = s.task_id " +
            "join sys_user u on u.user_id = s.user_id " +
            "left join edu_user_profile p on p.user_id = s.user_id " +
            "where" + where + " and s.status in (2, 3) order by p.student_no";
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        String[] header = {"任务名称", "学号", "姓名", "版本", "正文", "状态(2已交3已批)", "提交时间", "附件清单"};
        String[] fields = {"taskTitle", "student_no", "studentName", "version", "content", "status", "submit_time", "files"};
        return doExport("result_package", filter, header, fields, rows);
    }

    /**
     * 过程台账导出（MG-04-02）：学生 × 任务的发布/提交/批阅/成绩全过程时间线
     */
    @PreAuthorize("@ss.hasPermi('edu:export:ledger')")
    @PostMapping("/ledger")
    public AjaxResult ledger(@RequestBody Map<String, Object> filter)
    {
        Long courseId = asLong(filter.get("courseId"));
        Long classId = asLong(filter.get("classId"));
        if (courseId == null)
        {
            return error("请指定 courseId");
        }
        requireExportScope(null, courseId);
        String sql =
            "select p.student_no, u.nick_name as studentName, tk.title as taskTitle, " +
            "tk.publish_time, par.submit_status, s.submit_time, s.version, " +
            "rv.review_time, rv.total_score, sc.status as scoreStatus, sc.publish_time as scorePublishTime, " +
            "cs.is_current " +
            "from edu_task tk " +
            "join edu_task_participant par on par.task_id = tk.task_id " +
            "join sys_user u on u.user_id = par.user_id " +
            "left join edu_user_profile p on p.user_id = par.user_id " +
            "left join edu_class_student cs on cs.user_id = par.user_id and cs.is_current = 1 " +
            "left join edu_submission s on s.task_id = par.task_id and s.user_id = par.user_id " +
            "left join edu_review rv on rv.submission_id = s.submission_id and rv.status = 1 " +
            "left join edu_score sc on sc.task_id = par.task_id and sc.user_id = par.user_id " +
            "where tk.course_id = " + courseId + " and tk.del_flag = '0'";
        if (classId != null)
        {
            sql += " and cs.class_id = " + classId;
        }
        sql += " order by p.student_no, tk.task_id";
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        String[] header = {"学号", "姓名", "任务", "任务发布时间", "参与提交状态", "提交时间", "版本",
                "批阅时间", "总分", "成绩状态(0待发布1已发布2已更正)", "成绩发布时间", "在读标记"};
        String[] fields = {"student_no", "studentName", "taskTitle", "publish_time", "submit_status",
                "submit_time", "version", "review_time", "total_score", "scoreStatus", "scorePublishTime", "is_current"};
        return doExport("process_ledger", filter, header, fields, rows);
    }

    /**
     * 导出任务状态（MG-04-01）：edu_export_log 留痕反查
     */
    @PreAuthorize("@ss.hasPermi('edu:export:query')")
    @GetMapping("/tasks")
    public AjaxResult tasks()
    {
        String fields = "select id, export_type, row_count, status, operator_id, operate_time, "
                + "left(filter_json, 200) as filter_json from edu_export_log";
        String order = " order by id desc limit 200";
        return success(SecurityUtils.isAdmin(getUserId())
                ? jdbc.queryForList(fields + order)
                : jdbc.queryForList(fields + " where operator_id=?" + order, getUserId()));
    }

    @PreAuthorize("@ss.hasAnyPermi('edu:export:query,edu:score:export,edu:export:package,edu:export:ledger')")
    @GetMapping("/tasks/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable long id) throws IOException
    {
        var records = jdbc.queryForList("select file_path, operator_id, export_type from edu_export_log "
                + "where id=? and status=1 and file_path is not null", id);
        if (records.isEmpty()) return ResponseEntity.notFound().build();
        var record = records.get(0);
        String permission = switch (String.valueOf(record.get("export_type")))
        {
            case "score" -> "edu:score:export";
            case "result_package" -> "edu:export:package";
            case "process_ledger" -> "edu:export:ledger";
            default -> "";
        };
        if (!SecurityUtils.hasPermi(permission)
                || (!SecurityUtils.isAdmin(getUserId())
                    && !getUserId().equals(((Number) record.get("operator_id")).longValue())))
        {
            return ResponseEntity.status(403).build();
        }
        Path candidate = Path.of((String) record.get("file_path"));
        if (!Files.isRegularFile(candidate)) return ResponseEntity.notFound().build();
        Path file = candidate.toRealPath();
        if (!file.startsWith(Path.of(profile, "export").toRealPath()))
        {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export-" + id + ".csv\"")
                .body(new FileSystemResource(file));
    }

    private void requireExportScope(Long taskId, Long courseId)
    {
        if (taskId != null)
        {
            var courses = jdbc.queryForList("select course_id from edu_task where task_id=? and del_flag='0'",
                    Long.class, taskId);
            scope.requireCourseAccess(courses.isEmpty() ? null : courses.get(0));
            return;
        }
        scope.requireCourseAccess(courseId);
    }

    // ------------------------------------------------------------------

    private AjaxResult doExport(String type, Map<String, Object> filter,
            String[] header, String[] fields, List<Map<String, Object>> rows)
    {
        int rowCount = rows.size();
        boolean async = rowCount > ASYNC_THRESHOLD;
        final String fileId = IdUtils.fastSimpleUUID();
        final String dir = profile + "/export/" + type;
        final String path = dir + "/" + fileId + ".csv";
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into edu_export_log(export_type, filter_json, "
                    + "row_count, file_path, operator_id, operate_time, status) values(?,?,?,?,?,now(),0)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, type);
            statement.setString(2, toJson(filter));
            statement.setInt(3, rowCount);
            statement.setString(4, path);
            statement.setLong(5, getUserId());
            return statement;
        }, key);
        long logId = key.getKey().longValue();

        if (async)
        {
            exportExecutor.execute(() -> {
                try
                {
                    writeCsv(path, header, fields, rows);
                    jdbc.update("update edu_export_log set status = 1 where id = ?", logId);
                }
                catch (Exception e)
                {
                    jdbc.update("update edu_export_log set status = 2 where id = ?", logId);
                }
            });
            return success(Map.of("exportId", logId, "async", true, "rowCount", rowCount,
                    "msg", "数据量较大，已转异步生成，可通过 /edu/export/tasks 查询进度"));
        }
        try
        {
            writeCsv(path, header, fields, rows);
            jdbc.update("update edu_export_log set status = 1 where id = ?", logId);
        }
        catch (IOException e)
        {
            jdbc.update("update edu_export_log set status = 2 where id = ?", logId);
            return error("导出文件生成失败：" + e.getMessage());
        }
        return success(Map.of("exportId", logId, "async", false, "rowCount", rowCount));
    }

    private void writeCsv(String path, String[] header, String[] fields, List<Map<String, Object>> rows)
            throws IOException
    {
        File f = new File(path);
        if (!f.getParentFile().exists() && !f.getParentFile().mkdirs())
        {
            throw new IOException("无法创建目录 " + f.getParent());
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))
        {
            w.write('\ufeff'); // BOM，Excel 直接打开不乱码
            w.write(String.join(",", header));
            w.write('\n');
            for (Map<String, Object> row : rows)
            {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < fields.length; i++)
                {
                    if (i > 0)
                    {
                        sb.append(',');
                    }
                    Object v = row.get(fields[i]);
                    String s = v == null ? "" : String.valueOf(v).replace("\"", "\"\"").replace("\r", "").replace("\n", " ");
                    if (s.contains(",") || s.contains("\""))
                    {
                        sb.append('"').append(s).append('"');
                    }
                    else
                    {
                        sb.append(s);
                    }
                }
                w.write(sb.toString());
                w.write('\n');
            }
        }
    }

    private static String toJson(Map<String, Object> filter)
    {
        try
        {
            return com.alibaba.fastjson2.JSON.toJSONString(filter);
        }
        catch (Exception e)
        {
            return String.valueOf(filter);
        }
    }

    private static Long asLong(Object v)
    {
        if (v == null)
        {
            return null;
        }
        if (v instanceof Number)
        {
            return ((Number) v).longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(v));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
