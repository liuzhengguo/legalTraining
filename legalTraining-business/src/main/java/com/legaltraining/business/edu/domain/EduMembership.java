package com.legaltraining.business.edu.domain;

import java.util.Date;
import com.legaltraining.common.annotation.Excel;

/**
 * 班级归属记录 edu_class_student（MG-01-06 历史归属查询）
 *
 * is_current='1' 即"当前归属"，置 0 后仍保留 join_time / leave_time，
 * 供"按发生时归属"统计与转班历史展示。
 */
public class EduMembership
{
    private Long id;

    private Long classId;

    @Excel(name = "班级")
    private String className;

    private Long termId;

    @Excel(name = "学期")
    private String termName;

    @Excel(name = "加入时间")
    private Date joinTime;

    @Excel(name = "离开时间")
    private Date leaveTime;

    /** 0 历史归属 1 当前归属 */
    @Excel(name = "是否当前", readConverterExp = "0=历史,1=当前")
    private String isCurrent;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
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

    public Date getJoinTime()
    {
        return joinTime;
    }

    public void setJoinTime(Date joinTime)
    {
        this.joinTime = joinTime;
    }

    public Date getLeaveTime()
    {
        return leaveTime;
    }

    public void setLeaveTime(Date leaveTime)
    {
        this.leaveTime = leaveTime;
    }

    public String getIsCurrent()
    {
        return isCurrent;
    }

    public void setIsCurrent(String isCurrent)
    {
        this.isCurrent = isCurrent;
    }
}
