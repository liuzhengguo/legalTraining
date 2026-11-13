package com.legaltraining.business.edu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduClass;
import com.legaltraining.business.edu.domain.EduStudent;
import com.legaltraining.business.edu.mapper.EduClassMapper;
import com.legaltraining.business.edu.mapper.EduStudentMapper;
import com.legaltraining.business.edu.service.IEduStudentService;
import com.legaltraining.common.core.domain.entity.SysUser;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;
import com.legaltraining.system.service.ISysConfigService;
import com.legaltraining.system.service.ISysUserService;

/**
 * 学生账号 服务实现
 *
 * 学生账号分散在两张表：sys_user（登录与状态）+ edu_user_profile（学号、类别、专业、来源），
 * 班级归属单独落 edu_class_student，便于"发生时归属"式查询。
 */
@Service
public class EduStudentServiceImpl implements IEduStudentService
{
    /** 学生角色（sql/biz_seed.sql 内置） */
    private static final Long ROLE_STUDENT = 104L;

    @Autowired
    private EduStudentMapper eduStudentMapper;

    @Autowired
    private EduClassMapper eduClassMapper;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private ISysConfigService sysConfigService;

    @Override
    public List<EduStudent> selectStudentList(EduStudent query)
    {
        return eduStudentMapper.selectStudentList(query);
    }

    @Override
    public EduStudent selectStudentByUserId(Long userId)
    {
        return eduStudentMapper.selectStudentByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addStudent(EduStudent student)
    {
        if (StringUtils.isEmpty(student.getStudentNo()))
        {
            throw new ServiceException("学号不能为空");
        }
        if (StringUtils.isEmpty(student.getNickName()))
        {
            throw new ServiceException("姓名不能为空");
        }
        if (student.getClassId() == null)
        {
            throw new ServiceException("所属班级不能为空");
        }
        EduClass clazz = eduClassMapper.selectEduClassById(student.getClassId());
        if (clazz == null)
        {
            throw new ServiceException("所属班级不存在或已删除");
        }
        if (clazz.getStatus() != null && clazz.getStatus() == 1)
        {
            throw new ServiceException("班级「" + clazz.getClassName() + "」已归档，不能新增学生");
        }
        if (eduStudentMapper.countStudentNo(student.getStudentNo(), null) > 0)
        {
            throw new ServiceException("学号「" + student.getStudentNo() + "」已存在");
        }

        SysUser exist = sysUserService.selectUserByUserName(student.getStudentNo());
        if (exist != null)
        {
            throw new ServiceException("登录名「" + student.getStudentNo() + "」已被占用，请联系管理员处理");
        }
        if (StringUtils.isNotEmpty(student.getPhonenumber()))
        {
            SysUser phoneQuery = new SysUser();
            phoneQuery.setPhonenumber(student.getPhonenumber());
            if (!sysUserService.checkPhoneUnique(phoneQuery))
            {
                throw new ServiceException("手机号「" + student.getPhonenumber() + "」已被其他账号使用");
            }
        }

        SysUser user = new SysUser();
        user.setUserName(student.getStudentNo());
        user.setNickName(student.getNickName());
        user.setDeptId(clazz.getDeptId());
        user.setPhonenumber(student.getPhonenumber());
        user.setStatus("0");
        user.setPassword(SecurityUtils.encryptPassword(initPassword()));
        user.setRoleIds(new Long[] { ROLE_STUDENT });
        user.setRemark("学生账号，班级：" + clazz.getClassName());
        sysUserService.insertUser(user);

        Long userId = user.getUserId();
        if (userId == null)
        {
            throw new ServiceException("账号创建失败，未取得 user_id");
        }
        Long majorId = student.getMajorId() != null ? student.getMajorId() : clazz.getMajorId();
        Integer gradeYear = student.getGradeYear() != null ? student.getGradeYear() : clazz.getGradeYear();
        eduStudentMapper.insertProfile(userId, student.getStudentNo(), "student", majorId, gradeYear, "manual");
        eduStudentMapper.insertMembership(clazz.getClassId(), userId, clazz.getTermId(), currentUsername());
        eduClassMapper.updateStudentCount(clazz.getClassId(), eduClassMapper.countCurrentStudents(clazz.getClassId()));
        return userId;
    }

    @Override
    public int changeStatus(Long userId, String status)
    {
        if (!"0".equals(status) && !"1".equals(status))
        {
            throw new ServiceException("状态值只能是 0（正常）或 1（停用）");
        }
        SysUser user = sysUserService.selectUserById(userId);
        if (user == null)
        {
            throw new ServiceException("账号不存在");
        }
        if (userId != null && userId == 1L)
        {
            throw new ServiceException("超级管理员账号不允许停用");
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setStatus(status);
        return sysUserService.updateUserStatus(update);
    }

    @Override
    public String resetPassword(Long userId)
    {
        SysUser user = sysUserService.selectUserById(userId);
        if (user == null)
        {
            throw new ServiceException("账号不存在");
        }
        String pwd = initPassword();
        sysUserService.resetUserPwd(userId, SecurityUtils.encryptPassword(pwd));
        return pwd;
    }

    /**
     * 初始密码：优先取参数 sys.user.initPassword，其次 biz.student.initPassword
     */
    private String initPassword()
    {
        String pwd = sysConfigService.selectConfigByKey("sys.user.initPassword");
        if (StringUtils.isEmpty(pwd))
        {
            pwd = sysConfigService.selectConfigByKey("biz.student.initPassword");
        }
        return StringUtils.isEmpty(pwd) ? "123456" : pwd;
    }

    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
