package com.legaltraining.web.controller.edu;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.utils.uuid.IdUtils;

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

    @Value("${legal-training.profile}")
    private String profile;

    public EduExportController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
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
        return success(jdbc.queryForList(
            "select id, export_type, row_count, file_path, status, operator_id, operate_time, " +
            "left(filter_json, 200) as filter_json from edu_export_log order by id desc limit 200"));
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
        final Map<String, Object> filterJson = filter;
        final AtomicInteger logId = new AtomicInteger(0);

        // 先登记「生成中」，同步/异步生成后回写
        jdbc.update("insert into edu_export_log(export_type, filter_json, row_count, file_path, operator_id, operate_time, status) "
                + "values(?,?,?,?,?,now(),0)", type, toJson(filterJson), rowCount, path, getUserId());
        Long id = jdbc.queryForObject("select max(id) from edu_export_log", Long.class);
        logId.set(id.intValue());

        if (async)
        {
            exportExecutor.execute(() -> {
                try
                {
                    writeCsv(path, header, fields, rows);
                    jdbc.update("update edu_export_log set status = 1 where id = ?", logId.get());
                }
                catch (Exception e)
                {
                    jdbc.update("update edu_export_log set status = 2 where id = ?", logId.get());
                }
            });
            return success(Map.of("exportId", logId.get(), "async", true, "rowCount", rowCount,
                    "msg", "数据量较大，已转异步生成，可通过 /edu/export/tasks 查询进度"));
        }
        try
        {
            writeCsv(path, header, fields, rows);
            jdbc.update("update edu_export_log set status = 1 where id = ?", logId.get());
        }
        catch (IOException e)
        {
            jdbc.update("update edu_export_log set status = 2 where id = ?", logId.get());
            return error("导出文件生成失败：" + e.getMessage());
        }
        return success(Map.of("exportId", logId.get(), "async", false, "rowCount", rowCount, "filePath", path));
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
