package com.legaltraining.business.edu.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 授权与数据范围 数据层（MG-02-02/03）
 */
public interface EduGrantMapper
{
    /** 该教师名下待批阅提交数（撤回教师角色前的阻断检查） */
    public int countPendingReviews(@Param("teacherId") Long teacherId);

    /** 全校待审成果数（撤回审核员角色前的阻断检查） */
    public int countPendingAudits();

    /** 班级所属院系 */
    public Long selectClassDeptId(Long classId);

    /** 班级班主任 */
    public Long selectClassHeadTeacher(Long classId);

    /** 课程授课教师 */
    public Long selectCourseTeacherId(Long courseId);

    /** 学生当前所在班级 */
    public Long selectStudentClassId(Long userId);
}
