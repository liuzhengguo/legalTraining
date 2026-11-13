package com.legaltraining.business.edu.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.mapper.EduGrantMapper;
import com.legaltraining.business.edu.service.IEduGrantService;
import com.legaltraining.common.core.domain.entity.SysRole;
import com.legaltraining.common.core.domain.entity.SysUser;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.system.service.ISysRoleService;
import com.legaltraining.system.service.ISysUserService;

/**
 * 账号授权 服务实现
 *
 * 规则（MG-02-02）：
 * 1. 一人可多角色，权限取并集，不做叠加放大（并集由框架 selectMenuPermsByUserId 天然实现）；
 * 2. 教师（推荐权限）与审核员不得为同一账号：授权时给出提示，动作时由推荐/审核服务强制拦截；
 * 3. 撤销教师或审核员角色时，若仍有未完成待办，必须指定接收人，否则阻止撤销。
 */
@Service
public class EduGrantServiceImpl implements IEduGrantService
{
    /** 内置角色：教师（含推荐权限） */
    private static final long ROLE_TEACHER = 103L;

    /** 内置角色：审核员 */
    private static final long ROLE_AUDITOR = 106L;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private ISysRoleService sysRoleService;

    @Autowired
    private EduGrantMapper eduGrantMapper;

    @Override
    public List<SysRole> selectUserRoles(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("账号不能为空");
        }
        return sysRoleService.selectRolesByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assignRoles(Long userId, Long[] roleIds, Long transferToUserId)
    {
        if (userId == null)
        {
            throw new ServiceException("账号不能为空");
        }
        if (userId == 1L)
        {
            throw new ServiceException("超级管理员账号的角色不允许在此处调整");
        }
        SysUser user = sysUserService.selectUserById(userId);
        if (user == null)
        {
            throw new ServiceException("账号不存在");
        }
        if (roleIds == null)
        {
            roleIds = new Long[0];
        }
        for (Long roleId : roleIds)
        {
            if (roleId == null)
            {
                continue;
            }
            if (sysRoleService.selectRoleById(roleId) == null)
            {
                throw new ServiceException("角色 " + roleId + " 不存在");
            }
        }

        List<Long> before = new ArrayList<>(sysRoleService.selectRoleListByUserId(userId));
        List<Long> after = new ArrayList<>();
        for (Long r : roleIds)
        {
            if (r != null && !after.contains(r))
            {
                after.add(r);
            }
        }
        List<Long> removed = new ArrayList<>(before);
        removed.removeAll(after);

        List<String> warnings = new ArrayList<>();
        if (after.contains(ROLE_TEACHER) && after.contains(ROLE_AUDITOR))
        {
            warnings.add("该账号同时具备推荐与审核权限；推荐与审核须为不同账号，实际推荐/审核时系统会强制拦截");
        }

        // 撤销教师：待批提交必须有接收人
        if (removed.contains(ROLE_TEACHER))
        {
            int pending = eduGrantMapper.countPendingReviews(userId);
            if (pending > 0)
            {
                if (transferToUserId == null)
                {
                    throw new ServiceException("该教师名下仍有 " + pending
                            + " 份待批阅提交，撤销前请指定接收人（transferToUserId）");
                }
                checkReceiver(transferToUserId, ROLE_TEACHER, "教师");
                warnings.add(pending + " 份待批阅提交已标记转交给 user_id=" + transferToUserId
                        + "，实际改派在批阅模块完成（第二批）");
            }
        }
        // 撤销审核员：待审成果必须有接收人
        if (removed.contains(ROLE_AUDITOR))
        {
            int pending = eduGrantMapper.countPendingAudits();
            if (pending > 0)
            {
                if (transferToUserId == null)
                {
                    throw new ServiceException("平台仍有 " + pending
                            + " 条待审成果，撤销审核员前请指定接收人（transferToUserId）");
                }
                checkReceiver(transferToUserId, ROLE_AUDITOR, "审核员");
                warnings.add(pending + " 条待审成果已标记转交给 user_id=" + transferToUserId
                        + "，实际改派在成果审核模块完成（第三批）");
            }
        }

        sysUserService.insertUserAuth(userId, after.toArray(new Long[0]));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("userName", user.getUserName());
        result.put("roleIds", after);
        result.put("removedRoleIds", removed);
        result.put("warnings", warnings);
        result.put("message", "已保存，共 " + after.size() + " 个角色；权限变更即时生效（前端缓存最长 5 分钟）");
        return result;
    }

    /**
     * 接收人校验：必须存在且已具备对应角色
     */
    private void checkReceiver(Long transferToUserId, long roleId, String roleName)
    {
        SysUser receiver = sysUserService.selectUserById(transferToUserId);
        if (receiver == null)
        {
            throw new ServiceException("接收人不存在");
        }
        if (transferToUserId.equals(0L))
        {
            throw new ServiceException("接收人不能是系统账号");
        }
        List<Long> roles = sysRoleService.selectRoleListByUserId(transferToUserId);
        if (!roles.contains(roleId))
        {
            throw new ServiceException("接收人「" + receiver.getNickName() + "」不具备" + roleName + "角色");
        }
    }
}
