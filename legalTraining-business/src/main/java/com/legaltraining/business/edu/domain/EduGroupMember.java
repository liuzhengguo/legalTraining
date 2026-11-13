package com.legaltraining.business.edu.domain;

import java.util.Date;
import com.legaltraining.common.annotation.Excel;

/**
 * 分组成员 edu_group_member
 */
public class EduGroupMember
{
    private Long id;

    private Long groupId;

    private Long userId;

    /** 姓名（关联 sys_user.nick_name） */
    @Excel(name = "姓名")
    private String studentName;

    /** 学号（关联 edu_user_profile.student_no） */
    @Excel(name = "学号")
    private String studentNo;

    /** 0待确认 1已确认 2已拒绝 */
    private Integer confirmStatus;

    /** 成员贡献说明 */
    private String contribution;

    private Date joinTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getGroupId()
    {
        return groupId;
    }

    public void setGroupId(Long groupId)
    {
        this.groupId = groupId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getStudentName()
    {
        return studentName;
    }

    public void setStudentName(String studentName)
    {
        this.studentName = studentName;
    }

    public String getStudentNo()
    {
        return studentNo;
    }

    public void setStudentNo(String studentNo)
    {
        this.studentNo = studentNo;
    }

    public Integer getConfirmStatus()
    {
        return confirmStatus;
    }

    public void setConfirmStatus(Integer confirmStatus)
    {
        this.confirmStatus = confirmStatus;
    }

    public String getContribution()
    {
        return contribution;
    }

    public void setContribution(String contribution)
    {
        this.contribution = contribution;
    }

    public Date getJoinTime()
    {
        return joinTime;
    }

    public void setJoinTime(Date joinTime)
    {
        this.joinTime = joinTime;
    }
}
