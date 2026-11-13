package com.legaltraining.business.edu.domain;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 分组 edu_group
 *
 * 对应需求 MG-01-03（分组维护与跨班分组开关）
 * task_id 为空表示班级固定分组；不为空表示某次分组任务下临时组建。
 * 跨班分组受参数 biz.group.crossClass 控制，默认关闭。
 */
public class EduGroup extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Excel(name = "分组ID", cellType = Excel.ColumnType.NUMERIC)
    private Long groupId;

    /** 所属班级 */
    @NotNull(message = "所属班级不能为空")
    private Long classId;

    /** 班级名称（关联查询） */
    @Excel(name = "所属班级")
    private String className;

    /** 所属任务（可为空） */
    private Long taskId;

    @Excel(name = "组名")
    private String groupName;

    /** 组长 user_id */
    private Long leaderId;

    /** 组长姓名（关联查询） */
    @Excel(name = "组长")
    private String leaderName;

    /** 人数下限 */
    private Integer memberMin;

    /** 人数上限 */
    private Integer memberMax;

    /** 0教师指定 1自由组队 */
    private Integer groupType;

    /** 0组建中 1已确认 2已解散 */
    private Integer status;

    /** 成员ID列表（保存时覆盖式提交） */
    private List<Long> memberIds;

    /** 成员明细（查询时回填） */
    private List<EduGroupMember> members;

    public Long getGroupId()
    {
        return groupId;
    }

    public void setGroupId(Long groupId)
    {
        this.groupId = groupId;
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

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    @NotBlank(message = "组名不能为空")
    public String getGroupName()
    {
        return groupName;
    }

    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
    }

    public Long getLeaderId()
    {
        return leaderId;
    }

    public void setLeaderId(Long leaderId)
    {
        this.leaderId = leaderId;
    }

    public String getLeaderName()
    {
        return leaderName;
    }

    public void setLeaderName(String leaderName)
    {
        this.leaderName = leaderName;
    }

    public Integer getMemberMin()
    {
        return memberMin;
    }

    public void setMemberMin(Integer memberMin)
    {
        this.memberMin = memberMin;
    }

    public Integer getMemberMax()
    {
        return memberMax;
    }

    public void setMemberMax(Integer memberMax)
    {
        this.memberMax = memberMax;
    }

    public Integer getGroupType()
    {
        return groupType;
    }

    public void setGroupType(Integer groupType)
    {
        this.groupType = groupType;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public List<Long> getMemberIds()
    {
        return memberIds;
    }

    public void setMemberIds(List<Long> memberIds)
    {
        this.memberIds = memberIds;
    }

    public List<EduGroupMember> getMembers()
    {
        return members;
    }

    public void setMembers(List<EduGroupMember> members)
    {
        this.members = members;
    }
}
