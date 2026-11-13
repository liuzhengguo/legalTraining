package com.legaltraining.business.edu.domain;

import java.io.Serializable;

/**
 * 任务关联展项 edu_task_exhibit（ST-04-01 一键跳转展项）
 *
 * target_type：zone 展区 / item 展项；展项数据在 legal_exhibit 库，只存引用。
 */
public class EduTaskExhibit implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long taskId;

    private String targetType;

    private Long targetId;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
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
}
