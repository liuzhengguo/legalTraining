package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import com.legaltraining.common.core.domain.entity.SysRole;

/**
 * 账号授权（MG-02-02 一人多角色与权限并集）
 */
public interface IEduGrantService
{
    /**
     * 查询账号当前角色
     */
    public List<SysRole> selectUserRoles(Long userId);

    /**
     * 分配角色（覆盖式）
     *
     * @param transferToUserId 撤销教师/审核员且存在待办时，指定的接收人
     */
    public Map<String, Object> assignRoles(Long userId, Long[] roleIds, Long transferToUserId);
}
