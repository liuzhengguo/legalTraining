package com.legaltraining.business.edu.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import com.legaltraining.common.annotation.Excel;

/**
 * 资源分类 edu_resource_category（TC-02-02）
 *
 * 两级即可满足教学资源组织；owner_id 为空表示全校公共分类，教师不可删改公共分类。
 */
public class EduResourceCategory implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long categoryId;

    /** 父分类，0 为根 */
    private Long parentId;

    @Excel(name = "分类名称")
    private String categoryName;

    private Integer orderNum;

    /** 归属教师，空=全校公共 */
    private Long ownerId;

    /** 0正常 1停用 */
    private String status;

    /** 子分类（树形返回） */
    private List<EduResourceCategory> children = new ArrayList<>();

    /** 分类下资源数 */
    private Integer resourceCount;

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    @NotBlank(message = "分类名称不能为空")
    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    public Long getOwnerId()
    {
        return ownerId;
    }

    public void setOwnerId(Long ownerId)
    {
        this.ownerId = ownerId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public List<EduResourceCategory> getChildren()
    {
        return children;
    }

    public void setChildren(List<EduResourceCategory> children)
    {
        this.children = children;
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
