package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduMembership;
import com.legaltraining.business.edu.domain.EduTransferLog;

/**
 * 转班/转组与历史归属 数据层（MG-01-06）
 */
public interface EduTransferMapper
{
    /** 学生归属记录，currentOnly=true 只看当前，false 看全部历史 */
    public List<EduMembership> selectMemberships(@Param("userId") Long userId,
                                                 @Param("currentOnly") boolean currentOnly);

    public int insertTransferLog(EduTransferLog log);

    public List<EduTransferLog> selectTransferLogs(EduTransferLog query);

    /**
     * 该生在指定班级的分组任务中占用的分组数（转班前提示教师处理）
     */
    public int countGroupTasksInClass(@Param("classId") Long classId, @Param("userId") Long userId);

    /** 把成员从一个分组移到另一个分组（转组） */
    public int moveGroupMember(@Param("fromGroupId") Long fromGroupId, @Param("toGroupId") Long toGroupId,
                               @Param("userId") Long userId);

    public int deleteGroupMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    public int insertGroupMember(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
