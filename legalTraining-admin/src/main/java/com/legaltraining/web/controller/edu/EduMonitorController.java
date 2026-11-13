package com.legaltraining.web.controller.edu;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.scope.BusinessScopeChecker;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;

/**
 * 教学监管只读聚合（MG-03-01 课程任务总览 / MG-03-02 批阅进度）
 */
@RestController
@RequestMapping("/edu/monitor")
public class EduMonitorController extends BaseController
{
    private final JdbcTemplate jdbc;
    private final BusinessScopeChecker scope;

    public EduMonitorController(JdbcTemplate jdbc, BusinessScopeChecker scope)
    {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    /**
     * 课程任务总览（MG-03-01）：按课程聚合任务数、提交率、批阅率、平均分
     * termId 可选筛选；教师只看自己有权限的课程。
     */
    @PreAuthorize("@ss.hasPermi('edu:monitor:course')")
    @GetMapping("/course")
    public AjaxResult course(@RequestParam(required = false) Long termId)
    {
        StringBuilder sql = new StringBuilder();
        sql.append("select c.course_id as courseId, c.course_code as courseCode, c.course_name as courseName, ")
           .append("t.term_name as termName, c.status as courseStatus, ")
           .append("(select count(1) from edu_task tk where tk.course_id = c.course_id and tk.del_flag = '0') as taskCount, ")
           .append("(select count(1) from edu_task tk where tk.course_id = c.course_id and tk.del_flag = '0' and tk.status = 1) as runningCount, ")
           .append("(select count(1) from edu_task tk where tk.course_id = c.course_id and tk.del_flag = '0' and tk.status = 4) as archivedCount, ")
           .append("(select count(1) from edu_task_participant pp ")
           .append("      join edu_task tk2 on tk2.task_id = pp.task_id ")
           .append("      where tk2.course_id = c.course_id and tk2.del_flag = '0' and tk2.status in (1, 2, 4)) as expectedCount, ")
           .append("(select count(1) from edu_submission s join edu_task tk on tk.task_id = s.task_id ")
           .append("      where tk.course_id = c.course_id and tk.del_flag = '0' and s.status in (2, 3)) as submittedCount, ")
           .append("(select count(1) from edu_submission s join edu_task tk on tk.task_id = s.task_id ")
           .append("      where tk.course_id = c.course_id and tk.del_flag = '0' and s.status = 3) as reviewedCount, ")
           .append("(select round(avg(sc.final_score), 1) from edu_score sc join edu_task tk on tk.task_id = sc.task_id ")
           .append("      where tk.course_id = c.course_id and sc.status = 1) as avgScore ")
           .append("from edu_course c join edu_term t on t.term_id = c.term_id ")
           .append("where c.del_flag = '0'");
        if (termId != null)
        {
            sql.append(" and c.term_id = ").append(termId);
        }
        sql.append(" order by c.course_id desc");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString());
        // 教师数据范围过滤（教务/管理员全量）
        rows.removeIf(row -> !scope.canAccessCourse(((Number) row.get("courseId")).longValue()));
        for (Map<String, Object> row : rows)
        {
            long expected = ((Number) row.get("expectedCount")).longValue();
            long submitted = ((Number) row.get("submittedCount")).longValue();
            long reviewed = ((Number) row.get("reviewedCount")).longValue();
            row.put("submitRate", expected == 0 ? null : Math.round(submitted * 1000.0 / expected) / 10.0);
            row.put("reviewRate", submitted == 0 ? null : Math.round(reviewed * 1000.0 / submitted) / 10.0);
        }
        return success(rows);
    }

    /**
     * 批阅进度（MG-03-02）：按教师聚合待批、已批、逾期未批（提交超 72 小时未批）、平均批阅时长
     */
    @PreAuthorize("@ss.hasPermi('edu:monitor:review')")
    @GetMapping("/review")
    public AjaxResult review(@RequestParam(required = false) Long courseId)
    {
        String base =
            " from edu_course c join sys_user u on u.user_id = c.teacher_id " +
            "where c.del_flag = '0' and c.status = 1" +
            (courseId != null ? " and c.course_id = " + courseId : "");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select c.course_id as courseId, c.course_name as courseName, u.user_id as teacherId, " +
            "u.nick_name as teacherName, " +
            "(select count(1) from edu_submission s join edu_task tk on tk.task_id = s.task_id " +
            "   where tk.course_id = c.course_id and tk.del_flag = '0' and s.status = 2) as pendingCount, " +
            "(select count(1) from edu_submission s join edu_task tk on tk.task_id = s.task_id " +
            "   where tk.course_id = c.course_id and tk.del_flag = '0' and s.status = 4) as returnedCount, " +
            "(select count(1) from edu_submission s join edu_task tk on tk.task_id = s.task_id " +
            "   where tk.course_id = c.course_id and tk.del_flag = '0' and s.status = 2 " +
            "   and s.submit_time < date_sub(now(), interval 72 hour)) as overdueCount, " +
            "(select round(avg(timestampdiff(hour, s.submit_time, rv.review_time)), 1) " +
            "   from edu_review rv join edu_submission s on s.submission_id = rv.submission_id " +
            "   join edu_task tk on tk.task_id = s.task_id " +
            "   where tk.course_id = c.course_id and rv.status = 1 and rv.review_time is not null) as avgReviewHours " +
            base + " order by c.course_id desc");
        rows.removeIf(row -> !scope.canAccessCourse(((Number) row.get("courseId")).longValue()));
        return success(rows);
    }
}
