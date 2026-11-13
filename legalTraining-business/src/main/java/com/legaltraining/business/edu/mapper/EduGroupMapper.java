package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduGroup;
import com.legaltraining.business.edu.domain.EduGroupMember;

/**
 * 分组 数据层
 */
public interface EduGroupMapper
{
    public EduGroup selectEduGroupById(Long groupId);

    public List<EduGroup> selectEduGroupList(EduGroup eduGroup);

    public int insertEduGroup(EduGroup eduGroup);

    public int updateEduGroup(EduGroup eduGroup);

    public int deleteEduGroupByIds(Long[] groupIds);

    /** 成员明细 */
    public List<EduGroupMember> selectMembers(Long groupId);

    public int deleteMembersByGroupId(Long groupId);

    public int batchInsertMembers(@Param("list") List<EduGroupMember> list);

    /** 该学生是否属于指定班级（当前归属） */
    public int countClassStudent(@Param("classId") Long classId, @Param("userId") Long userId);

    /** 同班同任务下组名是否重复 */
    public int countByGroupName(@Param("classId") Long classId, @Param("taskId") Long taskId,
                                @Param("groupName") String groupName, @Param("excludeId") Long excludeId);
}
