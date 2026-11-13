package com.legaltraining.business.edu.domain;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 作业提交 edu_submission（ST-02-01~05，状态机 6.2）
 *
 * 状态：0 未提交 / 1 草稿 / 2 已提交 / 3 已批阅 / 4 已退回 / 5 逾期未交 / 6 任务取消
 * 唯一键 (task_id, user_id)，同一学生对同一任务只有一条提交记录，历史内容走版本表。
 *
 * 补交申请（ST-02-05）：
 *   late_apply_status 0 未申请 / 1 待审批 / 2 已通过 / 3 已驳回
 *   审批通过后 resubmit_deadline 作为补交截止时间，逾期仍未交则不允许再提交。
 */
public class EduSubmission extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long submissionId;

    private Long taskId;

    /** 任务标题（关联查询） */
    @Excel(name = "任务")
    private String taskTitle;

    /** 课程名称（关联查询） */
    private String courseName;

    /** 0个人 1分组 */
    private Integer subjectType;

    /** 学生 user_id */
    private Long userId;

    /** 学生姓名（关联查询） */
    @Excel(name = "学生")
    private String studentName;

    /** 学号（关联查询） */
    @Excel(name = "学号")
    private String studentNo;

    private Long groupId;

    private String groupName;

    /** 文字说明（≤2000 字） */
    private String content;

    private String remark;

    /** 当前版本号 */
    private Integer version;

    /** 状态机 6.2 */
    @Excel(name = "提交状态", readConverterExp = "0=未提交,1=草稿,2=已提交,3=已批阅,4=已退回,5=逾期未交,6=任务取消")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date draftTime;

    /** 是否逾期提交 */
    private String isOverdue;

    /** 退回/补交的重交截止 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date resubmitDeadline;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date withdrawTime;

    /** 补交申请状态 0未申请 1待审批 2已通过 3已驳回 */
    private Integer lateApplyStatus;

    /** 补交申请理由 */
    private String lateReason;

    /** 审批人 */
    private Long lateApproveBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lateApproveTime;

    /** 附件（当前版本） */
    private List<EduSubmissionFile> files;

    /** 最近一次退回原因（关联批阅记录，非本表字段） */
    private String returnReason;

    /** 是否允许提交（服务端判定结果，便于学生端置灰按钮） */
    private Boolean submittable;

    /** 当前是否可撤回 */
    private Boolean withdrawable;

    public Long getSubmissionId()
    {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId)
    {
        this.submissionId = submissionId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public String getTaskTitle()
    {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle)
    {
        this.taskTitle = taskTitle;
    }

    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
    }

    public Integer getSubjectType()
    {
        return subjectType;
    }

    public void setSubjectType(Integer subjectType)
    {
        this.subjectType = subjectType;
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

    public Long getGroupId()
    {
        return groupId;
    }

    public void setGroupId(Long groupId)
    {
        this.groupId = groupId;
    }

    public String getGroupName()
    {
        return groupName;
    }

    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    @Override
    public String getRemark()
    {
        return remark;
    }

    @Override
    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Date getSubmitTime()
    {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime)
    {
        this.submitTime = submitTime;
    }

    public Date getDraftTime()
    {
        return draftTime;
    }

    public void setDraftTime(Date draftTime)
    {
        this.draftTime = draftTime;
    }

    public String getIsOverdue()
    {
        return isOverdue;
    }

    public void setIsOverdue(String isOverdue)
    {
        this.isOverdue = isOverdue;
    }

    public Date getResubmitDeadline()
    {
        return resubmitDeadline;
    }

    public void setResubmitDeadline(Date resubmitDeadline)
    {
        this.resubmitDeadline = resubmitDeadline;
    }

    public Date getWithdrawTime()
    {
        return withdrawTime;
    }

    public void setWithdrawTime(Date withdrawTime)
    {
        this.withdrawTime = withdrawTime;
    }

    public Integer getLateApplyStatus()
    {
        return lateApplyStatus;
    }

    public void setLateApplyStatus(Integer lateApplyStatus)
    {
        this.lateApplyStatus = lateApplyStatus;
    }

    public String getLateReason()
    {
        return lateReason;
    }

    public void setLateReason(String lateReason)
    {
        this.lateReason = lateReason;
    }

    public Long getLateApproveBy()
    {
        return lateApproveBy;
    }

    public void setLateApproveBy(Long lateApproveBy)
    {
        this.lateApproveBy = lateApproveBy;
    }

    public Date getLateApproveTime()
    {
        return lateApproveTime;
    }

    public void setLateApproveTime(Date lateApproveTime)
    {
        this.lateApproveTime = lateApproveTime;
    }

    public List<EduSubmissionFile> getFiles()
    {
        return files;
    }

    public void setFiles(List<EduSubmissionFile> files)
    {
        this.files = files;
    }

    public String getReturnReason()
    {
        return returnReason;
    }

    public void setReturnReason(String returnReason)
    {
        this.returnReason = returnReason;
    }

    public Boolean getSubmittable()
    {
        return submittable;
    }

    public void setSubmittable(Boolean submittable)
    {
        this.submittable = submittable;
    }

    public Boolean getWithdrawable()
    {
        return withdrawable;
    }

    public void setWithdrawable(Boolean withdrawable)
    {
        this.withdrawable = withdrawable;
    }
}
