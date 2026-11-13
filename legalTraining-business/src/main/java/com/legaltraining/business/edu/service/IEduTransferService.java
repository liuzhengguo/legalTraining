package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduMembership;
import com.legaltraining.business.edu.domain.EduTransferLog;

/**
 * 转班、转组与历史归属（MG-01-06）
 */
public interface IEduTransferService
{
    /**
     * 转班：只影响此后产生的数据，历史提交/成绩仍按原班级统计
     *
     * @param remark 转班原因（留痕）
     */
    public Map<String, Object> transferClass(Long userId, Long toClassId, String remark);

    /**
     * 转组：在同一个班级内更换分组
     */
    public Map<String, Object> transferGroup(Long userId, Long fromGroupId, Long toGroupId, String remark);

    /**
     * 归属记录：currentOnly=true 只看当前，false 返回全部历史
     */
    public List<EduMembership> selectMemberships(Long userId, boolean currentOnly);

    public List<EduTransferLog> selectTransferLogs(EduTransferLog query);
}
