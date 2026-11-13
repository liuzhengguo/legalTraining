package com.legaltraining.business.edu.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduClass;
import com.legaltraining.business.edu.domain.EduGroup;
import com.legaltraining.business.edu.domain.EduGroupMember;
import com.legaltraining.business.edu.mapper.EduClassMapper;
import com.legaltraining.business.edu.mapper.EduGroupMapper;
import com.legaltraining.business.edu.service.IEduGroupService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;
import com.legaltraining.system.service.ISysConfigService;

/**
 * 分组 服务实现
 */
@Service
public class EduGroupServiceImpl implements IEduGroupService
{
    /** 跨班分组开关，默认关闭（MG-01-03） */
    private static final String CFG_CROSS_CLASS = "biz.group.crossClass";

    @Autowired
    private EduGroupMapper eduGroupMapper;

    @Autowired
    private EduClassMapper eduClassMapper;

    @Autowired
    private ISysConfigService sysConfigService;

    @Override
    public EduGroup selectEduGroupById(Long groupId)
    {
        EduGroup group = eduGroupMapper.selectEduGroupById(groupId);
        if (group != null)
        {
            group.setMembers(eduGroupMapper.selectMembers(groupId));
        }
        return group;
    }

    @Override
    public List<EduGroup> selectEduGroupList(EduGroup eduGroup)
    {
        List<EduGroup> list = eduGroupMapper.selectEduGroupList(eduGroup);
        for (EduGroup group : list)
        {
            group.setMembers(eduGroupMapper.selectMembers(group.getGroupId()));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertEduGroup(EduGroup eduGroup)
    {
        prepare(eduGroup, null);
        eduGroup.setCreateBy(currentUsername());
        int rows = eduGroupMapper.insertEduGroup(eduGroup);
        saveMembers(eduGroup, eduGroup.getClassId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateEduGroup(EduGroup eduGroup)
    {
        EduGroup exist = eduGroupMapper.selectEduGroupById(eduGroup.getGroupId());
        if (exist == null)
        {
            throw new ServiceException("分组不存在");
        }
        if (eduGroup.getClassId() == null)
        {
            eduGroup.setClassId(exist.getClassId());
        }
        prepare(eduGroup, eduGroup.getGroupId());
        int rows = eduGroupMapper.updateEduGroup(eduGroup);
        // 成员为覆盖式提交：未传 memberIds 表示不改动成员
        if (eduGroup.getMemberIds() != null)
        {
            eduGroupMapper.deleteMembersByGroupId(eduGroup.getGroupId());
            saveMembers(eduGroup, eduGroup.getClassId());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEduGroupByIds(Long[] groupIds)
    {
        if (groupIds == null || groupIds.length == 0)
        {
            return 0;
        }
        for (Long groupId : groupIds)
        {
            eduGroupMapper.deleteMembersByGroupId(groupId);
        }
        return eduGroupMapper.deleteEduGroupByIds(groupIds);
    }

    @Override
    public boolean isCrossClassEnabled()
    {
        return "1".equals(sysConfigService.selectConfigByKey(CFG_CROSS_CLASS));
    }

    /**
     * 公共校验：班级、组名、人数区间、组长归属
     */
    private void prepare(EduGroup eduGroup, Long excludeId)
    {
        EduClass clazz = eduClassMapper.selectEduClassById(eduGroup.getClassId());
        if (clazz == null)
        {
            throw new ServiceException("所属班级不存在或已删除");
        }
        if (eduGroup.getMemberMin() == null)
        {
            eduGroup.setMemberMin(1);
        }
        if (eduGroup.getMemberMax() == null)
        {
            eduGroup.setMemberMax(10);
        }
        if (eduGroup.getMemberMin() > eduGroup.getMemberMax())
        {
            throw new ServiceException("人数下限不能大于上限");
        }
        if (eduGroup.getGroupType() == null)
        {
            eduGroup.setGroupType(0);
        }
        if (eduGroup.getStatus() == null)
        {
            eduGroup.setStatus(0);
        }
        if (eduGroupMapper.countByGroupName(eduGroup.getClassId(), eduGroup.getTaskId(),
                eduGroup.getGroupName(), excludeId) > 0)
        {
            throw new ServiceException("同一班级同一任务下已存在组名「" + eduGroup.getGroupName() + "」");
        }
        List<Long> ids = distinct(eduGroup.getMemberIds());
        if (!ids.isEmpty())
        {
            if (ids.size() < eduGroup.getMemberMin() || ids.size() > eduGroup.getMemberMax())
            {
                throw new ServiceException("成员人数须在 " + eduGroup.getMemberMin() + "～"
                        + eduGroup.getMemberMax() + " 之间，当前 " + ids.size() + " 人");
            }
            if (eduGroup.getLeaderId() != null && !ids.contains(eduGroup.getLeaderId()))
            {
                throw new ServiceException("组长必须是本组成员");
            }
        }
    }

    /**
     * 成员落库；跨班开关关闭时逐个校验是否属于该班
     */
    private void saveMembers(EduGroup eduGroup, Long classId)
    {
        List<Long> ids = distinct(eduGroup.getMemberIds());
        if (ids.isEmpty())
        {
            return;
        }
        boolean crossClass = isCrossClassEnabled();
        List<EduGroupMember> rows = new ArrayList<>(ids.size());
        for (Long userId : ids)
        {
            if (!crossClass && eduGroupMapper.countClassStudent(classId, userId) == 0)
            {
                throw new ServiceException("学生 user_id=" + userId + " 不属于该班，如需跨班分组请先开启参数 "
                        + CFG_CROSS_CLASS);
            }
            EduGroupMember m = new EduGroupMember();
            m.setGroupId(eduGroup.getGroupId());
            m.setUserId(userId);
            m.setConfirmStatus(1);
            m.setContribution(null);
            rows.add(m);
        }
        eduGroupMapper.batchInsertMembers(rows);
        // 组长未显式指定时默认取第一个成员
        if (eduGroup.getLeaderId() == null)
        {
            EduGroup update = new EduGroup();
            update.setGroupId(eduGroup.getGroupId());
            update.setLeaderId(ids.get(0));
            eduGroupMapper.updateEduGroup(update);
        }
    }

    private List<Long> distinct(List<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return new ArrayList<>();
        }
        Set<Long> set = new LinkedHashSet<>(ids);
        set.remove(null);
        return new ArrayList<>(set);
    }

    private String currentUsername()
    {
        try
        {
            String name = SecurityUtils.getUsername();
            return StringUtils.isEmpty(name) ? "system" : name;
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
