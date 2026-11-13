package com.legaltraining.web.controller.edu;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Validator;
import com.legaltraining.common.utils.bean.BeanValidators;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduCourse;
import com.legaltraining.business.edu.domain.EduCourseExhibit;
import com.legaltraining.business.edu.service.IEduCourseService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;

/**
 * 课程（TC-01-01~03）
 *
 * 数据范围：授课教师本人可见自己的课；管理员/教务按角色 data_scope 判定。
 */
@RestController
@RequestMapping("/edu/course")
public class EduCourseController extends BaseController
{
    @Autowired
    private IEduCourseService eduCourseService;

    @Autowired
    private Validator validator;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private com.legaltraining.business.edu.scope.BusinessScopeChecker scope;

    @PreAuthorize("@ss.hasPermi('edu:course:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return success(java.util.Map.of(
                "terms", jdbc.queryForList("select term_id as termId,term_name as termName from edu_term "
                        + "where del_flag='0' and status<>2 order by is_current desc,start_date desc"),
                "classes", jdbc.queryForList("select class_id as classId,class_name as className,term_id as termId "
                        + "from edu_class where del_flag='0' and status=0 order by class_name")));
    }

    @PreAuthorize("@ss.hasPermi('edu:course:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduCourse eduCourse)
    {
        if (!scope.hasAnyRole("registrar")) eduCourse.setTeacherId(getUserId());
        startPage();
        List<EduCourse> list = eduCourseService.selectEduCourseList(eduCourse);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('edu:course:query')")
    @GetMapping("/{courseId}")
    public AjaxResult getInfo(@PathVariable Long courseId)
    {
        scope.requireCourseAccess(courseId);
        return success(eduCourseService.selectEduCourseById(courseId));
    }

    @Log(title = "课程", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:course:add')")
    @PostMapping
    public AjaxResult add(@RequestBody EduCourse eduCourse)
    {
        if (!scope.hasAnyRole("registrar") || eduCourse.getTeacherId() == null) eduCourse.setTeacherId(getUserId());
        BeanValidators.validateWithException(validator, eduCourse);
        validateClasses(eduCourse, eduCourse.getTermId());
        eduCourse.setStatus(0);
        return toAjax(eduCourseService.insertEduCourse(eduCourse));
    }

    /**
     * 修改（允许部分字段更新，故不做整体 Bean 校验，必填项在服务层按需校验）
     */
    @Log(title = "课程", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:course:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody EduCourse eduCourse)
    {
        scope.requireCourseAccess(eduCourse.getCourseId());
        EduCourse current = eduCourseService.selectEduCourseById(eduCourse.getCourseId());
        if (current == null) return error("课程不存在");
        validateClasses(eduCourse, current.getTermId());
        eduCourse.setTermId(null);
        eduCourse.setStatus(null);
        if (!scope.hasAnyRole("registrar")) eduCourse.setTeacherId(null);
        return toAjax(eduCourseService.updateEduCourse(eduCourse));
    }

    private void validateClasses(EduCourse course, Long termId)
    {
        if (course.getClassIds() == null) return;
        if (course.getClassIds().isEmpty()) throw new com.legaltraining.common.exception.ServiceException("请选择授课班级");
        List<Long> eligible = jdbc.queryForList("select class_id from edu_class where term_id=? and del_flag='0' and status=0",
                Long.class, termId);
        if (!eligible.containsAll(course.getClassIds()))
        {
            throw new com.legaltraining.common.exception.ServiceException("授课班级必须属于所选学期且处于启用状态");
        }
        course.setClassId(course.getClassIds().get(0));
    }

    @Log(title = "课程", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:course:remove')")
    @DeleteMapping("/{courseId}")
    public AjaxResult remove(@PathVariable Long courseId)
    {
        scope.requireCourseAccess(courseId);
        return toAjax(eduCourseService.deleteEduCourseById(courseId));
    }

    /**
     * 发布课程
     */
    @Log(title = "课程发布", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:course:edit')")
    @PutMapping("/{courseId}/publish")
    public AjaxResult publish(@PathVariable Long courseId)
    {
        scope.requireCourseAccess(courseId);
        return toAjax(eduCourseService.publish(courseId));
    }

    /**
     * 归档课程
     */
    @Log(title = "课程归档", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:course:edit')")
    @PutMapping("/{courseId}/archive")
    public AjaxResult archive(@PathVariable Long courseId)
    {
        scope.requireCourseAccess(courseId);
        return toAjax(eduCourseService.archive(courseId));
    }

    /**
     * 复制课程（TC-01-02）
     * 只复制课程结构与资源关联，不复制学生提交与成绩；副本为草稿态。
     */
    @Log(title = "课程复制", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:course:add')")
    @PostMapping("/{courseId}/copy")
    public AjaxResult copy(@PathVariable Long courseId,
            @RequestParam(required = false) String courseCode,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) Long termId)
    {
        scope.requireCourseAccess(courseId);
        return success(eduCourseService.copyCourse(courseId, courseCode, courseName, termId));
    }

    /**
     * 课程关联的展区/展项（TC-01-03 延伸学习入口）
     */
    @PreAuthorize("@ss.hasPermi('edu:course:query')")
    @GetMapping("/{courseId}/exhibits")
    public AjaxResult exhibits(@PathVariable Long courseId)
    {
        scope.requireCourseAccess(courseId);
        return success(eduCourseService.selectExhibits(courseId));
    }

    @PreAuthorize("@ss.hasPermi('edu:course:query')")
    @GetMapping("/exhibit-options")
    public AjaxResult exhibitOptions()
    {
        return success(jdbc.queryForList("select 'zone' as targetType,zone_id as targetId,zone_name as title "
                + "from exh_zone where del_flag='0' union all select 'item',item_id,title "
                + "from exh_item where del_flag='0' order by targetType,title"));
    }

    /**
     * 覆盖式保存展项关联
     */
    @Log(title = "课程展项关联", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:course:edit')")
    @PutMapping("/{courseId}/exhibits")
    public AjaxResult saveExhibits(@PathVariable Long courseId, @RequestBody List<EduCourseExhibit> exhibits)
    {
        scope.requireCourseAccess(courseId);
        for (EduCourseExhibit exhibit : exhibits)
        {
            if (exhibit == null || exhibit.getTargetId() == null
                    || !java.util.Set.of("item", "zone").contains(java.util.Objects.toString(exhibit.getTargetType(), "")))
            {
                throw new com.legaltraining.common.exception.ServiceException("请选择有效展区或展项");
            }
            String lookup = "zone".equals(exhibit.getTargetType())
                    ? "select zone_id from exh_zone where zone_id=? and del_flag='0'"
                    : "select item_id from exh_item where item_id=? and del_flag='0'";
            if (jdbc.queryForList(lookup, exhibit.getTargetId()).isEmpty())
            {
                throw new com.legaltraining.common.exception.ServiceException("关联的展区或展项已不存在");
            }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("saved", eduCourseService.saveExhibits(courseId, exhibits));
        return success(result);
    }
}
