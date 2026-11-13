package com.legaltraining.business.edu.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 专业 edu_major
 *
 * 对应需求 MG-01-02（班级维护的院系/专业维度）
 * 专业挂在 sys_dept 之下，班级再引用专业；删除前置检查"是否被班级引用"。
 */
public class EduMajor extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 专业ID */
    @Excel(name = "专业ID", cellType = Excel.ColumnType.NUMERIC)
    private Long majorId;

    /** 所属院系（sys_dept.dept_id） */
    private Long deptId;

    /** 院系名称（关联查询，非表字段） */
    @Excel(name = "所属院系")
    private String deptName;

    /** 专业编码 */
    @Excel(name = "专业编码")
    private String majorCode;

    /** 专业名称 */
    @Excel(name = "专业名称")
    private String majorName;

    /** 培养层次 本科/专科/研究生 */
    @Excel(name = "培养层次")
    private String degreeType;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getMajorId()
    {
        return majorId;
    }

    public void setMajorId(Long majorId)
    {
        this.majorId = majorId;
    }

    @NotNull(message = "所属院系不能为空")
    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
    }

    @NotBlank(message = "专业编码不能为空")
    @Size(min = 0, max = 32, message = "专业编码长度不能超过32个字符")
    public String getMajorCode()
    {
        return majorCode;
    }

    public void setMajorCode(String majorCode)
    {
        this.majorCode = majorCode;
    }

    @NotBlank(message = "专业名称不能为空")
    @Size(min = 0, max = 64, message = "专业名称长度不能超过64个字符")
    public String getMajorName()
    {
        return majorName;
    }

    public void setMajorName(String majorName)
    {
        this.majorName = majorName;
    }

    public String getDegreeType()
    {
        return degreeType;
    }

    public void setDegreeType(String degreeType)
    {
        this.degreeType = degreeType;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
