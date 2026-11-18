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

    /** 撤销教师：把名下课程（含待批提交）整体改派给接收人 */
    int transferTeacherCourses(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    /** 撤销审核员：把待审成果的责任人改派给接收人 */
    int transferPendingAudits(@Param("toUserId") Long toUserId);

    /** 交接留痕：写入双方站内通知 */
    int insertTransferNotice(@Param("receiverId") Long receiverId, @Param("title") String title,
            @Param("content") String content);

    /** 班级所属院系 */
    public Long selectClassDeptId(Long classId);

    /** 班级班主任 */
    public Long selectClassHeadTeacher(Long classId);

    /** 课程授课教师 */
    public Long selectCourseTeacherId(Long courseId);

    /** 学生当前所在班级 */
    public Long selectStudentClassId(Long userId);
}
