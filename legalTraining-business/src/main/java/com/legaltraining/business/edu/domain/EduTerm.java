package com.legaltraining.business.edu.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 学年学期 edu_term
 *
 * 对应需求 MG-01-01、P10（学期归档与新学期切换）
 * 状态：0 筹备 / 1 进行中 / 2 已归档
 */
public class EduTerm extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 学期ID */
    @Excel(name = "学期ID", cellType = Excel.ColumnType.NUMERIC)
    private Long termId;

    /** 学年，如 2026-2027 */
    @Excel(name = "学年")
    private String schoolYear;

    /** 学期序号 1第一学期 2第二学期 3短学期 */
    @Excel(name = "学期序号", readConverterExp = "1=第一学期,2=第二学期,3=短学期")
    private Integer termNo;

    /** 学期显示名 */
    @Excel(name = "学期名称")
    private String termName;

    /** 开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "开始日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date startDate;

    /** 结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "结束日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date endDate;

    /** 是否当前学期（0否 1是，全局唯一） */
    @Excel(name = "当前学期", readConverterExp = "0=否,1=是")
    private String isCurrent;

    /** 状态 0筹备 1进行中 2已归档 */
    @Excel(name = "状态", readConverterExp = "0=筹备,1=进行中,2=已归档")
    private Integer status;

    /** 归档时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "归档时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date archiveTime;

    /** 归档人 */
    @Excel(name = "归档人")
    private String archiveBy;

    /** 归档前置检查报告（JSON） */
    private String archiveReport;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getTermId()
    {
        return termId;
    }

    public void setTermId(Long termId)
    {
        this.termId = termId;
    }

    @NotBlank(message = "学年不能为空")
    @Size(min = 0, max = 16, message = "学年长度不能超过16个字符")
    public String getSchoolYear()
    {
        return schoolYear;
    }

    public void setSchoolYear(String schoolYear)
    {
        this.schoolYear = schoolYear;
    }

    @NotNull(message = "学期序号不能为空")
    public Integer getTermNo()
    {
        return termNo;
    }

    public void setTermNo(Integer termNo)
    {
        this.termNo = termNo;
    }

    @NotBlank(message = "学期名称不能为空")
    @Size(min = 0, max = 64, message = "学期名称长度不能超过64个字符")
    public String getTermName()
    {
        return termName;
    }

    public void setTermName(String termName)
    {
        this.termName = termName;
    }

    @NotNull(message = "开始日期不能为空")
    public Date getStartDate()
    {
        return startDate;
    }

    public void setStartDate(Date startDate)
    {
        this.startDate = startDate;
    }

    @NotNull(message = "结束日期不能为空")
    public Date getEndDate()
    {
        return endDate;
    }

    public void setEndDate(Date endDate)
    {
        this.endDate = endDate;
    }

    public String getIsCurrent()
    {
        return isCurrent;
    }

    public void setIsCurrent(String isCurrent)
    {
        this.isCurrent = isCurrent;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Date getArchiveTime()
    {
        return archiveTime;
    }

    public void setArchiveTime(Date archiveTime)
    {
        this.archiveTime = archiveTime;
    }

    public String getArchiveBy()
    {
        return archiveBy;
    }

    public void setArchiveBy(String archiveBy)
    {
        this.archiveBy = archiveBy;
    }

    public String getArchiveReport()
    {
        return archiveReport;
    }

    public void setArchiveReport(String archiveReport)
    {
        this.archiveReport = archiveReport;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
