package com.legaltraining.business.edu.scope;

import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.legaltraining.business.edu.mapper.EduGrantMapper;
import com.legaltraining.common.core.domain.entity.SysDept;
import com.legaltraining.common.core.domain.entity.SysRole;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.system.service.ISysDeptService;

/**
 * 业务数据范围校验（MG-02-03）
 *
 * 与框架 @DataScope 的区别：框架的 data_scope 是 SQL 拼接的部门条件，
 * 这里回答的是"这条班级/课程我能不能碰"，用于单条写操作的归属校验。
 *
 * 判定顺序（取最宽，不做叠加放大）：
 *   超级管理员 → 全通
 *   班主任 / 授课教师 / 学生本人 → 通过（业务归属优先于部门范围）
 *   角色 data_scope：1 全部 / 2 自定部门 / 3 本部门 / 4 本部门及以下 / 5 仅本人
 */
@Component("businessScope")
public class BusinessScopeChecker
{
    /** 角色 key：超级管理员 */
    private static final String ROLE_ADMIN = "admin";

    @Autowired
    private EduGrantMapper eduGrantMapper;

    @Autowired
    private ISysDeptService sysDeptService;

    /**
     * 是否可访问指定班级
     */
    public boolean canAccessClass(Long classId)
    {
        if (classId == null)
        {
            return false;
        }
        Long userId = currentUserId();
        if (userId == null)
        {
            return false;
        }
        if (isSuperAdmin(userId))
        {
            return true;
        }
        // 业务归属：班主任、学生本人
        Long headTeacher = eduGrantMapper.selectClassHeadTeacher(classId);
        if (userId.equals(headTeacher))
        {
            return true;
        }
        Long studentClass = eduGrantMapper.selectStudentClassId(userId);
        if (classId.equals(studentClass))
        {
            return true;
        }
        return matchDeptScope(eduGrantMapper.selectClassDeptId(classId));
    }

    /**
     * 是否可访问指定课程
     */
    public boolean canAccessCourse(Long courseId)
    {
        if (courseId == null)
        {
            return false;
        }
        Long userId = currentUserId();
        if (userId == null)
        {
            return false;
        }
        if (isSuperAdmin(userId))
        {
            return true;
        }
        Long teacherId = eduGrantMapper.selectCourseTeacherId(courseId);
        if (userId.equals(teacherId))
        {
            return true;
        }
        return hasAnyRole("registrar");
    }

    public void requireCourseAccess(Long courseId)
    {
        if (!canAccessCourse(courseId))
        {
            throw new ServiceException("当前账号无权处理该课程");
        }
    }

    /**
     * 是否具备任一管理类角色（用于判断能否进入 E1）
     */
    public boolean hasAnyRole(String... roleKeys)
    {
        Long userId = currentUserId();
        if (userId == null)
        {
            return false;
        }
        if (isSuperAdmin(userId))
        {
            return true;
        }
        List<SysRole> roles = activeRoles();
        Set<String> keys = new HashSet<>();
        for (SysRole r : roles)
        {
            keys.add(r.getRoleKey());
        }
        for (String k : roleKeys)
        {
            if (keys.contains(k))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isSuperAdmin(Long userId)
    {
        if (userId == 1L)
        {
            return true;
        }
        List<SysRole> roles = activeRoles();
        for (SysRole r : roles)
        {
            if (ROLE_ADMIN.equals(r.getRoleKey()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 部门范围匹配；deptId 为空表示无法判定（返回 false，避免越权）
     */
    private boolean matchDeptScope(Long deptId)
    {
        if (deptId == null)
        {
            return false;
        }
        Long userId = currentUserId();
        Long userDeptId = SecurityUtils.getLoginUser().getDeptId();
        List<SysRole> roles = activeRoles();

        Set<Long> allowed = new HashSet<>();
        for (SysRole role : roles)
        {
            String scope = role.getDataScope();
            if ("1".equals(scope))
            {
                return true;
            }
            if ("2".equals(scope))
            {
                allowed.addAll(sysDeptService.selectDeptListByRoleId(role.getRoleId()));
            }
            else if ("3".equals(scope) && userDeptId != null)
            {
                allowed.add(userDeptId);
            }
            else if ("4".equals(scope) && userDeptId != null)
            {
                // 本部门及以下：按 ancestors 判断，避免递归查询
                allowed.add(userDeptId);
                SysDept query = new SysDept();
                for (SysDept d : sysDeptService.selectDeptList(query))
                {
                    String ancestors = d.getAncestors() == null ? "" : d.getAncestors();
                    if (d.getDeptId().equals(userDeptId)
                            || ancestors.contains("," + userDeptId + ",")
                            || ancestors.endsWith("," + userDeptId))
                    {
                        allowed.add(d.getDeptId());
                    }
                }
            }
        }
        return allowed.contains(deptId);
    }

    private List<SysRole> activeRoles()
    {
        List<SysRole> roles = SecurityUtils.getLoginUser().getUser().getRoles();
        return roles == null ? Collections.emptyList()
                : roles.stream().filter(role -> "0".equals(role.getStatus())).toList();
    }

    private Long currentUserId()
    {
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
