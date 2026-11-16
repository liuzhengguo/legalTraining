package com.legaltraining.web.controller.edu;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.scope.BusinessScopeChecker;
import com.legaltraining.business.edu.service.IEduGrantService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.domain.entity.SysRole;
import com.legaltraining.common.core.domain.entity.SysUser;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.system.service.ISysRoleService;
import com.legaltraining.system.service.ISysUserService;

/**
 * 账号授权（E1 平台管理 / MG-02-02、MG-02-03）
 */
@RestController
@RequestMapping("/edu/grant")
public class EduGrantController extends BaseController
{
    @Autowired
    private IEduGrantService eduGrantService;

    @Autowired
    private BusinessScopeChecker businessScope;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    /**
     * 账号列表：账号基本信息 + 已授权角色 + 数据范围并集。
     * 页面首次进入即可呈现内容，无需先手工输入账号 ID 查询。
     */
    @PreAuthorize("@ss.hasPermi('edu:grant:list')")
    @GetMapping("/accounts")
    public AjaxResult accounts(SysUser query)
    {
        List<AccountOption> options = new ArrayList<>();
        for (SysUser user : userService.selectUserList(query))
        {
            if (user.isAdmin())
            {
                continue;
            }
            List<SysRole> granted = roleService.selectRolesByUserId(user.getUserId()).stream()
                    .filter(SysRole::isFlag)
                    .filter(role -> !role.isAdmin() && !"admin".equals(role.getRoleKey()))
                    .toList();
            List<RoleOption> roles = granted.stream()
                    .map(role -> new RoleOption(role.getRoleId(), role.getRoleName(), role.getRoleKey(),
                            role.getDataScope(), dataScopeLabel(parseScope(role.getDataScope()))))
                    .toList();
            int scope = dataScopeValue(granted);
            options.add(new AccountOption(user.getUserId(), user.getUserName(), user.getNickName(),
                    user.getDept() == null ? null : user.getDept().getDeptName(), user.getStatus(),
                    roles, scope, dataScopeLabel(scope)));
        }
        return success(options);
    }

    /**
     * 可分配角色列表（排除超级管理员及其等价角色），供角色筛选与授权面板使用。
     */
    @PreAuthorize("@ss.hasPermi('edu:grant:list')")
    @GetMapping("/roles")
    public AjaxResult assignableRoles()
    {
        List<RoleOption> roles = roleService.selectRoleAll().stream()
                .filter(role -> !role.isAdmin() && !"admin".equals(role.getRoleKey()))
                .map(role -> new RoleOption(role.getRoleId(), role.getRoleName(), role.getRoleKey(),
                        role.getDataScope(), dataScopeLabel(parseScope(role.getDataScope()))))
                .toList();
        return success(roles);
    }

    @PreAuthorize("@ss.hasPermi('edu:grant:list')")
    @GetMapping("/user-role/{userId}")
    public AjaxResult userRoles(@PathVariable Long userId)
    {
        List<SysRole> roles = eduGrantService.selectUserRoles(userId);
        return success(roles);
    }

    /**
     * 分配角色（覆盖式）
     *
     * @param transferToUserId 撤销教师/审核员且存在待办时必须提供
     */
    @Log(title = "账号授权", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:grant:edit')")
    @PutMapping("/user-role")
    public AjaxResult assignRoles(@RequestParam Long userId,
                                  @RequestParam(required = false) Long[] roleIds,
                                  @RequestParam(required = false) Long transferToUserId)
    {
        return success(eduGrantService.assignRoles(userId, roleIds, transferToUserId));
    }

    /**
     * 数据范围自检：当前登录账号是否有权访问该班级
     */
    @GetMapping("/scope/class/{classId}")
    public AjaxResult checkClassScope(@PathVariable Long classId)
    {
        return success(businessScope.canAccessClass(classId));
    }

    /**
     * 数据范围自检：当前登录账号是否有权访问该课程
     */
    @GetMapping("/scope/course/{courseId}")
    public AjaxResult checkCourseScope(@PathVariable Long courseId)
    {
        return success(businessScope.canAccessCourse(courseId));
    }

    /** 多角色数据范围取并集：按框架 dataScope 定义取最宽的一档（数字最小） */
    private int dataScopeValue(List<SysRole> roles)
    {
        int result = 0;
        for (SysRole role : roles)
        {
            int value = parseScope(role.getDataScope());
            if (result == 0 || value < result)
            {
                result = value;
            }
        }
        return result;
    }

    private int parseScope(String dataScope)
    {
        try
        {
            return Integer.parseInt(dataScope);
        }
        catch (Exception e)
        {
            return 5;
        }
    }

    private String dataScopeLabel(int scope)
    {
        switch (scope)
        {
            case 1: return "全部（全校）";
            case 2: return "自定义（按角色配置）";
            case 3: return "本部门";
            case 4: return "本部门及以下";
            case 5: return "仅本人";
            default: return "未分配角色";
        }
    }

    public record RoleOption(Long roleId, String roleName, String roleKey, String dataScope, String dataScopeLabel) {}

    public record AccountOption(Long userId, String userName, String nickName, String deptName, String status,
                                List<RoleOption> roles, Integer dataScopeValue, String dataScopeLabel) {}
}
