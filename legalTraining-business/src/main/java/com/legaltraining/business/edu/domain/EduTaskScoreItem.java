package com.legaltraining.business.edu.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 评分项 edu_task_score_item（TC-03-01 多维度加权评价）
 *
 * 所有评分项的 weight 合计必须等于 100，否则任务不可发布。
 */
public class EduTaskScoreItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long itemId;

    private Long taskId;

    @NotBlank(message = "评分项名称不能为空")
    private String itemName;

    /** 权重（%） */
    @NotNull(message = "权重不能为空")
    private BigDecimal weight;

    /** 该项满分 */
    private BigDecimal maxScore;

    private Integer sortNo;

    private String remark;

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public String getItemName()
    {
        return itemName;
    }

    public void setItemName(String itemName)
    {
        this.itemName = itemName;
    }

    public BigDecimal getWeight()
    {
        return weight;
    }

    public void setWeight(BigDecimal weight)
    {
        this.weight = weight;
    }

    public BigDecimal getMaxScore()
    {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore)
    {
        this.maxScore = maxScore;
    }

    public Integer getSortNo()
    {
        return sortNo;
    }

    public void setSortNo(Integer sortNo)
    {
        this.sortNo = sortNo;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
