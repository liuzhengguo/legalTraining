package com.legaltraining.web.controller.edu;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.constant.UserConstants;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.domain.entity.SysDept;
import com.legaltraining.common.core.domain.entity.SysRole;
import com.legaltraining.common.core.domain.entity.SysUser;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.system.service.ISysDeptService;
import com.legaltraining.system.service.ISysRoleService;
import com.legaltraining.system.service.ISysUserService;

@RestController
@RequestMapping("/edu/teacher")
public class EduTeacherController extends BaseController
{
    @Autowired
    private ISysUserService users;

    @Autowired
    private ISysRoleService roles;

    @Autowired
    private ISysDeptService departments;

    @PreAuthorize("@ss.hasPermi('edu:teacher:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysUser query)
    {
        query.setRoleId(teacherRoleId());
        startPage();
        List<SysUser> teachers = users.selectAllocatedList(query);
        TableDataInfo result = getDataTable(teachers);
        result.setRows(teachers.stream().map(this::teacherData).toList());
        return result;
    }

    @PreAuthorize("@ss.hasPermi('edu:teacher:list')")
    @GetMapping("/dept-options")
    public AjaxResult deptOptions()
    {
        SysDept query = new SysDept();
        query.setStatus("0");
        return success(departments.selectDeptList(query).stream()
                .map(dept -> Map.of("value", dept.getDeptId(), "label", dept.getDeptName())).toList());
    }

    @PreAuthorize("@ss.hasPermi('edu:teacher:edit')")
    @GetMapping("/{userId}")
    public AjaxResult detail(@PathVariable Long userId)
    {
        return success(teacherData(editableTeacher(userId)));
    }

    @Autowired
    private JdbcTemplate jdbc;

    private static final String INELIGIBLE_ROLES = """
            select ur.user_id from sys_user_role ur join sys_role r on r.role_id = ur.role_id
            where r.del_flag = '0' and r.role_key in ('admin', 'student', 'teacher')
            """;

    @PreAuthorize("@ss.hasPermi('edu:teacher:add')")
    @GetMapping("/user-options")
    public AjaxResult userOptions()
    {
        SysUser query = new SysUser();
        query.setStatus("0");
        var excluded = new java.util.HashSet<>(jdbc.queryForList(INELIGIBLE_ROLES, Long.class));
        return success(users.selectUserList(query).stream()
                .filter(user -> !user.isAdmin() && !excluded.contains(user.getUserId()))
                .map(user -> Map.of("value", user.getUserId(), "label", user.getNickName() + "（" + user.getUserName() + "）"))
                .toList());
    }

    @Log(title = "关联教师账号", businessType = BusinessType.GRANT)
    @PreAuthorize("@ss.hasPermi('edu:teacher:add')")
    @PostMapping
    public AjaxResult add(@RequestBody TeacherLink form)
    {
        Long userId = form.linkedUserId();
        if (userId == null || userId <= 0 || SecurityUtils.isAdmin(userId))
        {
            throw new ServiceException("请选择可关联的系统用户");
        }
        users.checkUserDataScope(userId);
        int linked = jdbc.update("""
                insert ignore into sys_user_role (user_id, role_id)
                select u.user_id, ? from sys_user u
                where u.user_id = ? and u.status = '0' and u.del_flag = '0'
                and u.user_id not in (
                """ + INELIGIBLE_ROLES + ")", teacherRoleId(), userId);
        if (linked == 0)
        {
            throw new ServiceException("该用户已关联教师角色或当前不可关联，请刷新后重试");
        }
        return success();
    }

    @Log(title = "教师账号", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:teacher:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody TeacherForm form)
    {
        SysUser existing = editableTeacher(form.userId());
        SysUser user = buildUser(form);
        user.setUserId(existing.getUserId());
        user.setUpdateBy(getUsername());
        if (form.password() != null && !form.password().isEmpty())
        {
            user.setPassword(encodePassword(form.password()));
        }
        return toAjax(users.updateUserProfile(user));
    }

    private Long teacherRoleId()
    {
        SysRole query = new SysRole();
        query.setRoleKey("teacher");
        query.setStatus("0");
        return roles.selectRoleList(query).stream()
                .filter(role -> "teacher".equals(role.getRoleKey()))
                .map(SysRole::getRoleId).findFirst()
                .orElseThrow(() -> new ServiceException("教师角色不可用"));
    }

    private SysUser editableTeacher(Long userId)
    {
        if (userId == null || SecurityUtils.isAdmin(userId))
        {
            throw new ServiceException("请选择可管理的教师账号");
        }
        users.checkUserDataScope(userId);
        SysUser user = users.selectUserById(userId);
        if (user == null || !"0".equals(user.getDelFlag())
                || !roles.selectRoleListByUserId(userId).equals(List.of(teacherRoleId())))
        {
            throw new ServiceException("该账号需通过系统用户管理维护");
        }
        return user;
    }

    private SysUser buildUser(TeacherForm form)
    {
        String name = form.nickName() == null ? "" : form.nickName().trim();
        if (name.isEmpty() || name.length() > 30)
        {
            throw new ServiceException("请填写教师姓名，最多 30 个字符");
        }
        if (form.deptId() == null || form.deptId() <= 0)
        {
            throw new ServiceException("请选择院系");
        }
        departments.checkDeptDataScope(form.deptId());
        SysDept dept = departments.selectDeptById(form.deptId());
        if (dept == null || !"0".equals(dept.getStatus()) || !"0".equals(dept.getDelFlag()))
        {
            throw new ServiceException("所选院系不可用");
        }
        if (!"0".equals(form.status()) && !"1".equals(form.status()))
        {
            throw new ServiceException("请选择账号状态");
        }
        SysUser user = new SysUser();
        user.setNickName(name);
        user.setDeptId(form.deptId());
        user.setStatus(form.status());
        return user;
    }

    private String encodePassword(String password)
    {
        if (password == null || password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            throw new ServiceException("密码长度须为 5 至 20 个字符");
        }
        return SecurityUtils.encryptPassword(password);
    }

    private TeacherData teacherData(SysUser user)
    {
        return new TeacherData(user.getUserId(), user.getUserName(), user.getNickName(), user.getDeptId(), user.getStatus());
    }

    public record TeacherForm(Long userId, String userName, String nickName, Long deptId, String status, String password) {}

    public record TeacherLink(Long linkedUserId) {}

    private record TeacherData(Long userId, String userName, String nickName, Long deptId, String status) {}
}
