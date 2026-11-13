package com.legaltraining.business.edu.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.legaltraining.common.annotation.Excel;

/**
 * 批阅 edu_review（TC-04-01~06）
 *
 * 一次提交可能有多条批阅记录（每次退回/重批都新增），submission_version 指明批的是哪一版。
 * status 0 草稿 / 1 已发布；is_return='1' 表示本次操作是退回而非评分。
 * 退回原因不少于 20 字，且必须给重交截止时间（TC-04-04）。
 */
public class EduReview implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long reviewId;

    private Long submissionId;

    /** 批阅针对的提交版本 */
    private Integer submissionVersion;

    /** 批阅教师 */
    private Long teacherId;

    /** 教师姓名（关联查询） */
    private String teacherName;

    /** 合计得分 */
    @Excel(name = "得分")
    private BigDecimal totalScore;

    /** 评语 */
    private String reviewComment;

    /** 0草稿 1已发布 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    /** 退回原因（≥20 字） */
    private String returnReason;

    /** 重交截止 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date returnDeadline;

    /** 本次是否退回 */
    private String isReturn;

    /** 逐项得分 */
    private List<EduReviewScore> scores;

    // 以下为批阅列表展示用的关联字段
    private Long taskId;

    private String taskTitle;

    private String studentName;

    private String studentNo;

    private BigDecimal totalScoreFull;

    private Integer scoreMode;

    /** 课程ID（待批列表按课程筛选） */
    private Long courseId;

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public Long getSubmissionId()
    {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId)
    {
        this.submissionId = submissionId;
    }

    public Integer getSubmissionVersion()
    {
        return submissionVersion;
    }

    public void setSubmissionVersion(Integer submissionVersion)
    {
        this.submissionVersion = submissionVersion;
    }

    public Long getTeacherId()
    {
        return teacherId;
    }

    public void setTeacherId(Long teacherId)
    {
        this.teacherId = teacherId;
    }

    public String getTeacherName()
    {
        return teacherName;
    }

    public void setTeacherName(String teacherName)
    {
        this.teacherName = teacherName;
    }

    public BigDecimal getTotalScore()
    {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore)
    {
        this.totalScore = totalScore;
    }

    public String getReviewComment()
    {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment)
    {
        this.reviewComment = reviewComment;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Date getReviewTime()
    {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime)
    {
        this.reviewTime = reviewTime;
    }

    public Date getPublishTime()
    {
        return publishTime;
    }

    public void setPublishTime(Date publishTime)
    {
        this.publishTime = publishTime;
    }

    public String getReturnReason()
    {
        return returnReason;
    }

    public void setReturnReason(String returnReason)
    {
        this.returnReason = returnReason;
    }

    public Date getReturnDeadline()
    {
        return returnDeadline;
    }

    public void setReturnDeadline(Date returnDeadline)
    {
        this.returnDeadline = returnDeadline;
    }

    public String getIsReturn()
    {
        return isReturn;
    }

    public void setIsReturn(String isReturn)
    {
        this.isReturn = isReturn;
    }

    public List<EduReviewScore> getScores()
    {
        return scores;
    }

    public void setScores(List<EduReviewScore> scores)
    {
        this.scores = scores;
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

    public BigDecimal getTotalScoreFull()
    {
        return totalScoreFull;
    }

    public void setTotalScoreFull(BigDecimal totalScoreFull)
    {
        this.totalScoreFull = totalScoreFull;
    }

    public Integer getScoreMode()
    {
        return scoreMode;
    }

    public void setScoreMode(Integer scoreMode)
    {
        this.scoreMode = scoreMode;
    }
}
