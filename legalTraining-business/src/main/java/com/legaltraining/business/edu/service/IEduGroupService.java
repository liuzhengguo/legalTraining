package com.legaltraining.business.edu.service;

import java.util.List;
import com.legaltraining.business.edu.domain.EduGroup;

/**
 * 分组 服务层
 */
public interface IEduGroupService
{
    public EduGroup selectEduGroupById(Long groupId);

    public List<EduGroup> selectEduGroupList(EduGroup eduGroup);

    public int insertEduGroup(EduGroup eduGroup);

    public int updateEduGroup(EduGroup eduGroup);

    public int deleteEduGroupByIds(Long[] groupIds);

    /**
     * 跨班分组开关（biz.group.crossClass）
     */
    public boolean isCrossClassEnabled();
}
