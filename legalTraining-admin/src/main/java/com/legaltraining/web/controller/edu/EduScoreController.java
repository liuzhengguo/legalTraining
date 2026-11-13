package com.legaltraining.web.controller.edu;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduScore;
import com.legaltraining.business.edu.service.IEduScoreService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;

/**
 * 成绩（TC-04-05 / TC-05-04）
 */
@RestController
@RequestMapping("/edu/score")
public class EduScoreController extends BaseController
{
    @Autowired
    private IEduScoreService eduScoreService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @PreAuthorize("@ss.hasPermi('edu:score:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduScore query)
    {
        startPage();
        List<EduScore> list = eduScoreService.selectScoreList(query);
        return getDataTable(list);
    }

    /**
     * 学生端：我的成绩（H5 成绩页）
     */
    @PreAuthorize("@ss.hasPermi('edu:score:query')")
    @GetMapping("/my")
    public AjaxResult my(@RequestParam(required = false) Long courseId)
    {
        return success(eduScoreService.selectMyScores(courseId));
    }

    /**
     * 整任务成绩发布（TC-04-05）
     */
    @Log(title = "成绩发布", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:score:publish')")
    @PostMapping("/publish/{taskId}")
    public AjaxResult publish(@PathVariable Long taskId)
    {
        return success(eduScoreService.publishTask(taskId));
    }

    /**
     * 成绩更正（TC-04-06）：必须填原因，留痕并通知学生
     */
    @Log(title = "成绩更正", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:score:adjust')")
    @PutMapping("/{scoreId}/adjust")
    public AjaxResult adjust(@PathVariable Long scoreId,
            @RequestParam BigDecimal newScore,
            @RequestParam String reason)
    {
        return success(eduScoreService.adjust(scoreId, newScore, reason));
    }

    /**
     * 更正记录
     */
    @PreAuthorize("@ss.hasPermi('edu:score:query')")
    @GetMapping("/{scoreId}/adjust-log")
    public AjaxResult adjustLog(@PathVariable Long scoreId)
    {
        return success(eduScoreService.adjustLog(scoreId));
    }

    /**
     * 课程总评计算（TC-05-04）
     */
    @Log(title = "课程总评", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:score:publish')")
    @PostMapping("/course/{courseId}/calc")
    public AjaxResult calcCourse(@PathVariable Long courseId)
    {
        return success(eduScoreService.calcCourseScore(courseId));
    }

    /**
     * 课程总评查询（教师看全班，学生传 userId 看自己由 /my 覆盖）
     */
    @PreAuthorize("@ss.hasPermi('edu:score:query')")
    @GetMapping("/course/{courseId}")
    public AjaxResult courseScore(@PathVariable Long courseId,
            @RequestParam(required = false) Long userId)
    {
        return success(eduScoreService.courseScore(courseId, userId));
    }

    /**
     * 全校成绩汇总（MG-04-01）：按 课程 × 班级 交叉统计均分/最高/最低/及格率
     */
    @PreAuthorize("@ss.hasPermi('edu:score:summary')")
    @GetMapping("/summary")
    public AjaxResult summary(@RequestParam(required = false) Long termId,
            @RequestParam(required = false) Long courseId)
    {
        String sql =
            "select c.course_id as courseId, c.course_name as courseName, cl.class_id as classId, " +
            "cl.class_name as className, t.term_name as termName, " +
            "count(sc.score_id) as scoredCount, " +
            "round(avg(sc.final_score), 1) as avgScore, " +
            "max(sc.final_score) as maxScore, min(sc.final_score) as minScore, " +
            "round(sum(case when sc.final_score >= 60 then 1 else 0 end) * 100.0 / count(sc.score_id), 1) as passRate, " +
            "sum(case when sc.final_score >= 90 then 1 else 0 end) as excellentCount, " +
            "sum(case when sc.final_score < 60 then 1 else 0 end) as failCount " +
            "from edu_score sc " +
            "join edu_task tk on tk.task_id = sc.task_id " +
            "join edu_course c on c.course_id = tk.course_id " +
            "join edu_term t on t.term_id = c.term_id " +
            "join edu_class_student cs on cs.user_id = sc.user_id and cs.is_current = 1 " +
            "join edu_class cl on cl.class_id = cs.class_id " +
            "where sc.status = 1";
        if (termId != null)
        {
            sql += " and c.term_id = " + termId;
        }
        if (courseId != null)
        {
            sql += " and c.course_id = " + courseId;
        }
        sql += " group by c.course_id, cl.class_id order by c.course_id, cl.class_id";
        return success(jdbc.queryForList(sql));
    }
}
