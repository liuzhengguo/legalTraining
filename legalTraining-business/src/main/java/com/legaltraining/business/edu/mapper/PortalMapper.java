package com.legaltraining.business.edu.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 门户待办聚合 数据层（OP-01-03 / E8）
 */
public interface PortalMapper
{
    /** 待批：我教的课程下已提交未批阅 */
    public int countPendingReview(@Param("teacherId") Long teacherId);

    /** 待审：待审核成果，排除自己推荐的（推荐与审核互斥） */
    public int countPendingAudit(@Param("auditorId") Long auditorId);

    /** 待交：我应参与且任务进行中、尚未提交 */
    public int countPendingSubmit(@Param("userId") Long userId);

    /** 待授权：推荐到我的成果，等待确认署名与范围 */
    public int countPendingAuthorize(@Param("userId") Long userId);
}
