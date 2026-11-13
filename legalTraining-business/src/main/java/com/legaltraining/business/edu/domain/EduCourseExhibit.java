package com.legaltraining.business.edu.domain;

import java.io.Serializable;

/**
 * 课程关联展区/展项 edu_course_exhibit（TC-01-03 延伸学习入口）
 *
 * target_type：zone 展区 / item 展项。展项数据在 legal_exhibit 库，
 * 此处仅存 ID 引用，跨域不做物理外键，也不做跨库 join。
 */
public class EduCourseExhibit implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long courseId;

    /** zone=展区 item=展项 */
    private String targetType;

    private Long targetId;

    /** 展区/展项名称（前端回填展示，非本表字段） */
    private String targetName;

    private Integer sortNo;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getTargetName()
    {
        return targetName;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public Integer getSortNo()
    {
        return sortNo;
    }

    public void setSortNo(Integer sortNo)
    {
        this.sortNo = sortNo;
    }
}
