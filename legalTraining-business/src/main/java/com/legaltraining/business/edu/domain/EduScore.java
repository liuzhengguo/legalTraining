package com.legaltraining.business.edu.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.legaltraining.common.annotation.Excel;

/**
 * 任务成绩 edu_score（TC-04-05 / TC-05-04）
 *
 * 一个学生一个任务一条记录；分组任务默认"组得分=成员得分"，个人加减分记在 group_adjust。
 * final_score = task_score + group_adjust，是计入课程总评的分。
 * status 0 待发布 / 1 已发布 / 2 已更正（更正留痕见 edu_score_adjust）。
 */
public class EduScore implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long scoreId;

    private Long taskId;

    private Long courseId;

    private Long userId;

    private Long groupId;

    /** 任务得分（批阅合计） */
    @Excel(name = "任务得分")
    private BigDecimal taskScore;

    /** 分组加减分 */
    @Excel(name = "加减分")
    private BigDecimal groupAdjust;

    /** 计入课程总评的分 */
    @Excel(name = "最终得分")
    private BigDecimal finalScore;

    /** 等级制时的等级 */
    @Excel(name = "等级")
    private String levelCode;

    /** 0待发布 1已发布 2已更正 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    // 展示字段
    private String studentName;

    private String studentNo;

    private String taskTitle;

    private String courseName;

    private String groupName;

    /** 满分 */
    private BigDecimal totalScoreFull;

    /** 评价方式 0百分制 1等级制 2加权 */
    private Integer scoreMode;

    public Long getScoreId()
    {
        return scoreId;
    }

    public void setScoreId(Long scoreId)
    {
        this.scoreId = scoreId;
    }

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

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getGroupId()
    {
        return groupId;
    }

    public void setGroupId(Long groupId)
    {
        this.groupId = groupId;
    }

    public BigDecimal getTaskScore()
    {
        return taskScore;
    }

    public void setTaskScore(BigDecimal taskScore)
    {
        this.taskScore = taskScore;
    }

    public BigDecimal getGroupAdjust()
    {
        return groupAdjust;
    }

    public void setGroupAdjust(BigDecimal groupAdjust)
    {
        this.groupAdjust = groupAdjust;
    }

    public BigDecimal getFinalScore()
    {
        return finalScore;
    }

    public void setFinalScore(BigDecimal finalScore)
    {
        this.finalScore = finalScore;
    }

    public String getLevelCode()
    {
        return levelCode;
    }

    public void setLevelCode(String levelCode)
    {
        this.levelCode = levelCode;
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

    public String getGroupName()
    {
        return groupName;
    }

    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
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
