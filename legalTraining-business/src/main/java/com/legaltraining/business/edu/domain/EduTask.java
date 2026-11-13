package com.legaltraining.business.edu.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 实训任务 edu_task（TC-03-01~06，状态机 6.1）
 *
 * 状态：0 草稿 / 1 进行中 / 2 已截止 / 3 已取消 / 4 已归档
 * 转变：草稿 -发布-> 进行中 -到期或手动截止-> 已截止 -归档-> 已归档
 *       进行中 -撤回-> 已取消（有提交时须填原因，学生端收到通知）
 *
 * 评价方式 score_mode：0 百分制 / 1 等级制 / 2 多维度加权（此时评分项权重合计必须 =100）
 * 完成方式 finish_mode：0 个人 / 1 教师分组 / 2 自由组队
 * 截止策略 deadline_policy：0 严格（截止即禁止提交）/ 1 宽限（grace_days 内可交，标记逾期）
 *                          2 审批补交（须教师审批）/ 3 不限
 */
public class EduTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Excel(name = "任务ID", cellType = Excel.ColumnType.NUMERIC)
    private Long taskId;

    @NotNull(message = "所属课程不能为空")
    private Long courseId;

    /** 课程名称（关联查询） */
    @Excel(name = "课程")
    private String courseName;

    @NotBlank(message = "任务标题不能为空")
    @Excel(name = "任务标题")
    private String title;

    /** 富文本要求 */
    private String requirement;

    /** 附件模板 */
    private String templateFile;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @NotNull(message = "截止时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "截止时间")
    private Date deadline;

    /** 0严格 1宽限 2审批补交 3不限 */
    private Integer deadlinePolicy;

    /** 宽限天数 */
    private Integer graceDays;

    /** 0个人 1教师分组 2自由组队 */
    private Integer finishMode;

    private Integer groupMin;

    private Integer groupMax;

    /** 0百分制 1等级制 2多维度加权 */
    private Integer scoreMode;

    /** 满分 */
    private BigDecimal totalScore;

    /** 评分项权重是否合计 100 */
    private String weightCheck;

    /** 0草稿 1进行中 2已截止 3已取消 4已归档 */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=进行中,2=已截止,3=已取消,4=已归档")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    /** 撤回原因 */
    private String cancelReason;

    /** 是否归档时未批 */
    private String archiveFlag;

    /** 评分项（多维度加权时必填） */
    private List<EduTaskScoreItem> scoreItems;

    /** 关联资源 */
    private List<Long> resourceIds;

    /** 关联展项 */
    private List<EduTaskExhibit> exhibits;

    /** 应参与人数（列表统计） */
    private Integer participantCount;

    /** 已提交人数（列表统计） */
    private Integer submittedCount;

    /** 待批阅人数（列表统计） */
    private Integer pendingCount;

    /** 是否需记分到成绩（成绩发布后置 1，避免重复发布） */
    private String scoredFlag;

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getRequirement()
    {
        return requirement;
    }

    public void setRequirement(String requirement)
    {
        this.requirement = requirement;
    }

    public String getTemplateFile()
    {
        return templateFile;
    }

    public void setTemplateFile(String templateFile)
    {
        this.templateFile = templateFile;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getDeadline()
    {
        return deadline;
    }

    public void setDeadline(Date deadline)
    {
        this.deadline = deadline;
    }

    public Integer getDeadlinePolicy()
    {
        return deadlinePolicy;
    }

    public void setDeadlinePolicy(Integer deadlinePolicy)
    {
        this.deadlinePolicy = deadlinePolicy;
    }

    public Integer getGraceDays()
    {
        return graceDays;
    }

    public void setGraceDays(Integer graceDays)
    {
        this.graceDays = graceDays;
    }

    public Integer getFinishMode()
    {
        return finishMode;
    }

    public void setFinishMode(Integer finishMode)
    {
        this.finishMode = finishMode;
    }

    public Integer getGroupMin()
    {
        return groupMin;
    }

    public void setGroupMin(Integer groupMin)
    {
        this.groupMin = groupMin;
    }

    public Integer getGroupMax()
    {
        return groupMax;
    }

    public void setGroupMax(Integer groupMax)
    {
        this.groupMax = groupMax;
    }

    public Integer getScoreMode()
    {
        return scoreMode;
    }

    public void setScoreMode(Integer scoreMode)
    {
        this.scoreMode = scoreMode;
    }

    public BigDecimal getTotalScore()
    {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore)
    {
        this.totalScore = totalScore;
    }

    public String getWeightCheck()
    {
        return weightCheck;
    }

    public void setWeightCheck(String weightCheck)
    {
        this.weightCheck = weightCheck;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Date getPublishTime()
    {
        return publishTime;
    }

    public void setPublishTime(Date publishTime)
    {
        this.publishTime = publishTime;
    }

    public String getCancelReason()
    {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason)
    {
        this.cancelReason = cancelReason;
    }

    public String getArchiveFlag()
    {
        return archiveFlag;
    }

    public void setArchiveFlag(String archiveFlag)
    {
        this.archiveFlag = archiveFlag;
    }

    public List<EduTaskScoreItem> getScoreItems()
    {
        return scoreItems;
    }

    public void setScoreItems(List<EduTaskScoreItem> scoreItems)
    {
        this.scoreItems = scoreItems;
    }

    public List<Long> getResourceIds()
    {
        return resourceIds;
    }

    public void setResourceIds(List<Long> resourceIds)
    {
        this.resourceIds = resourceIds;
    }

    public List<EduTaskExhibit> getExhibits()
    {
        return exhibits;
    }

    public void setExhibits(List<EduTaskExhibit> exhibits)
    {
        this.exhibits = exhibits;
    }

    public Integer getParticipantCount()
    {
        return participantCount;
    }

    public void setParticipantCount(Integer participantCount)
    {
        this.participantCount = participantCount;
    }

    public Integer getSubmittedCount()
    {
        return submittedCount;
    }

    public void setSubmittedCount(Integer submittedCount)
    {
        this.submittedCount = submittedCount;
    }

    public Integer getPendingCount()
    {
        return pendingCount;
    }

    public void setPendingCount(Integer pendingCount)
    {
        this.pendingCount = pendingCount;
    }

    public String getScoredFlag()
    {
        return scoredFlag;
    }

    public void setScoredFlag(String scoredFlag)
    {
        this.scoredFlag = scoredFlag;
    }
}
