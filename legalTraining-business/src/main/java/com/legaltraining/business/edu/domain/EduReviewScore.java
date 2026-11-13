package com.legaltraining.business.edu.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 逐项得分 edu_review_score
 */
public class EduReviewScore implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long reviewId;

    /** 评分项ID */
    private Long itemId;

    /** 该项得分 */
    private BigDecimal score;

    /** 评分项名称（关联查询） */
    private String itemName;

    /** 权重（关联查询） */
    private BigDecimal weight;

    /** 该项满分（关联查询） */
    private BigDecimal maxScore;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public BigDecimal getScore()
    {
        return score;
    }

    public void setScore(BigDecimal score)
    {
        this.score = score;
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
}
