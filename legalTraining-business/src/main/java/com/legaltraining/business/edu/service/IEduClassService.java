package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduClass;

/**
 * 班级 服务层
 */
public interface IEduClassService
{
    public EduClass selectEduClassById(Long classId);

    public List<EduClass> selectEduClassList(EduClass eduClass);

    public int insertEduClass(EduClass eduClass);

    public int updateEduClass(EduClass eduClass);

    public int deleteEduClassByIds(Long[] classIds);

    /**
     * 重算班级在册人数（成员变更后调用）
     */
    public int recountStudentCount(Long classId);

    /**
     * 归档/启用切换：归档前校验是否存在进行中的任务（走教学域统计）
     */
    public Map<String, Object> changeStatus(Long classId, Integer status);
}
