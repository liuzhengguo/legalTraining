package com.legaltraining.business.edu.domain;

/**
 * 课程-资源关联维护入参（TC-02-01：教师投放资源到课程）
 *
 * 仅承载关联表字段，资源本体信息仍由 edu_resource 维护。
 */
public class EduCourseResourceRef
{
    private Long resourceId;

    private Integer sortNo;

    public Long getResourceId()
    {
        return resourceId;
    }

    public void setResourceId(Long resourceId)
    {
        this.resourceId = resourceId;
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
