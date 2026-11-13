package com.legaltraining.business.edu.domain;

import java.util.Date;
import com.legaltraining.common.annotation.Excel;

/**
 * 转班/转组记录 edu_transfer_log（MG-01-06）
 */
public class EduTransferLog
{
    private Long id;

    private Long userId;

    @Excel(name = "学号")
    private String studentNo;

    @Excel(name = "姓名")
    private String studentName;

    /** class=转班 group=转组 */
    @Excel(name = "类型", readConverterExp = "class=转班,group=转组")
    private String transferType;

    private Long fromId;

    @Excel(name = "原班级/分组")
    private String fromName;

    private Long toId;

    @Excel(name = "新班级/分组")
    private String toName;

    private Long termId;

    private Long operatorId;

    @Excel(name = "操作人")
    private String operatorName;

    @Excel(name = "操作时间")
    private Date operateTime;

    @Excel(name = "备注")
    private String remark;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getStudentNo()
    {
        return studentNo;
    }

    public void setStudentNo(String studentNo)
    {
        this.studentNo = studentNo;
    }

    public String getStudentName()
    {
        return studentName;
    }

    public void setStudentName(String studentName)
    {
        this.studentName = studentName;
    }

    public String getTransferType()
    {
        return transferType;
    }

    public void setTransferType(String transferType)
    {
        this.transferType = transferType;
    }

    public Long getFromId()
    {
        return fromId;
    }

    public void setFromId(Long fromId)
    {
        this.fromId = fromId;
    }

    public String getFromName()
    {
        return fromName;
    }

    public void setFromName(String fromName)
    {
        this.fromName = fromName;
    }

    public Long getToId()
    {
        return toId;
    }

    public void setToId(Long toId)
    {
        this.toId = toId;
    }

    public String getToName()
    {
        return toName;
    }

    public void setToName(String toName)
    {
        this.toName = toName;
    }

    public Long getTermId()
    {
        return termId;
    }

    public void setTermId(Long termId)
    {
        this.termId = termId;
    }

    public Long getOperatorId()
    {
        return operatorId;
    }

    public void setOperatorId(Long operatorId)
    {
        this.operatorId = operatorId;
    }

    public String getOperatorName()
    {
        return operatorName;
    }

    public void setOperatorName(String operatorName)
    {
        this.operatorName = operatorName;
    }

    public Date getOperateTime()
    {
        return operateTime;
    }

    public void setOperateTime(Date operateTime)
    {
        this.operateTime = operateTime;
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
