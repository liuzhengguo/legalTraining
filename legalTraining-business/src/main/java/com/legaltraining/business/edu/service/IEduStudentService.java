package com.legaltraining.business.edu.service;

import java.util.List;
import com.legaltraining.business.edu.domain.EduStudent;

/**
 * 学生账号 服务层（MG-01-05）
 */
public interface IEduStudentService
{
    public List<EduStudent> selectStudentList(EduStudent query);

    public EduStudent selectStudentByUserId(Long userId);

    /**
     * 单条新增学生账号：同时写 sys_user、sys_user_role、edu_user_profile、edu_class_student
     *
     * @return 新账号 userId
     */
    public Long addStudent(EduStudent student);

    /**
     * 启停账号（0 正常 / 1 停用）
     */
    public int changeStatus(Long userId, String status);

    /**
     * 重置密码，返回本次设置的初始密码（仅在响应中出现一次）
     */
    public String resetPassword(Long userId);
}
