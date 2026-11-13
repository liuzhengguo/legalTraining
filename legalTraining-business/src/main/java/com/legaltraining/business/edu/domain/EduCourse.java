package com.legaltraining.business.edu.domain;

import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 课程 edu_course
 *
 * 对应需求 TC-01-01（课程维护）、TC-01-02（复制课程）、TC-01-03（关联展区/展项）
 * 一门课在学期内编码唯一；可带多个班级（edu_course_class），class_id 只记录主班。
 * 课程状态：0 草稿 / 1 已发布 / 2 已归档。已归档课程不可再改任务与资源。
 */
public class EduCourse extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 课程ID */
    @Excel(name = "课程ID", cellType = Excel.ColumnType.NUMERIC)
    private Long courseId;

    /** 课程编码（学期内唯一） */
    @Excel(name = "课程编码")
    private String courseCode;

    /** 课程名称 */
    @Excel(name = "课程名称")
    private String courseName;

    /** 学期ID */
    private Long termId;

    /** 学期名称（关联查询） */
    @Excel(name = "学期")
    private String termName;

    /** 授课教师 user_id */
    private Long teacherId;

    /** 授课教师姓名（关联查询） */
    @Excel(name = "授课教师")
    private String teacherName;

    /** 归属主班 */
    private Long classId;

    /** 主班名称（关联查询） */
    @Excel(name = "班级")
    private String className;

    /** 封面 */
    private String cover;

    /** 课程简介 */
    private String intro;

    /** 0草稿 1已发布 2已归档 */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已发布,2=已归档")
    private Integer status;

    /** 复制来源课程ID */
    private Long copyFrom;

    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    /** 课程带班ID列表（保存时覆盖式提交） */
    private List<Long> classIds;

    /** 关联展区/展项（TC-01-03） */
    private List<EduCourseExhibit> exhibits;

    /** 课程下任务数（列表统计） */
    private Integer taskCount;

    /** 课程下资源数（列表统计） */
    private Integer resourceCount;

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    @NotBlank(message = "课程编码不能为空")
    public String getCourseCode()
    {
        return courseCode;
    }

    public void setCourseCode(String courseCode)
    {
        this.courseCode = courseCode;
    }

    @NotBlank(message = "课程名称不能为空")
    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
    }

    @NotNull(message = "所属学期不能为空")
    public Long getTermId()
    {
        return termId;
    }

    public void setTermId(Long termId)
    {
        this.termId = termId;
    }

    public String getTermName()
    {
        return termName;
    }

    public void setTermName(String termName)
    {
        this.termName = termName;
    }

    @NotNull(message = "授课教师不能为空")
    public Long getTeacherId()
    {
        return teacherId;
    }

    public void setTeacherId(Long teacherId)
    {
        this.teacherId = teacherId;
    }

    public String getTeacherName()
    {
        return teacherName;
    }

    public void setTeacherName(String teacherName)
    {
        this.teacherName = teacherName;
    }

    public Long getClassId()
    {
        return classId;
    }

    public void setClassId(Long classId)
    {
        this.classId = classId;
    }

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Long getCopyFrom()
    {
        return copyFrom;
    }

    public void setCopyFrom(Long copyFrom)
    {
        this.copyFrom = copyFrom;
    }

    public Date getPublishTime()
    {
        return publishTime;
    }

    public void setPublishTime(Date publishTime)
    {
        this.publishTime = publishTime;
    }

    public List<Long> getClassIds()
    {
        return classIds;
    }

    public void setClassIds(List<Long> classIds)
    {
        this.classIds = classIds;
    }

    public List<EduCourseExhibit> getExhibits()
    {
        return exhibits;
    }

    public void setExhibits(List<EduCourseExhibit> exhibits)
    {
        this.exhibits = exhibits;
    }

    public Integer getTaskCount()
    {
        return taskCount;
    }

    public void setTaskCount(Integer taskCount)
    {
        this.taskCount = taskCount;
    }

    public Integer getResourceCount()
    {
        return resourceCount;
    }

    public void setResourceCount(Integer resourceCount)
    {
        this.resourceCount = resourceCount;
    }
}
