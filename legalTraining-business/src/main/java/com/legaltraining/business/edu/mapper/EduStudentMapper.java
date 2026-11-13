package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduStudent;

/**
 * 学生账号 数据层（跨 sys_user / edu_user_profile / edu_class_student）
 */
public interface EduStudentMapper
{
    public List<EduStudent> selectStudentList(EduStudent query);

    public EduStudent selectStudentByUserId(Long userId);

    /** 账号扩展信息是否存在 */
    public int countProfile(Long userId);

    public int insertProfile(@Param("userId") Long userId, @Param("studentNo") String studentNo,
                             @Param("userCategory") String userCategory, @Param("majorId") Long majorId,
                             @Param("gradeYear") Integer gradeYear, @Param("source") String source);

    public int updateProfile(@Param("userId") Long userId, @Param("studentNo") String studentNo,
                             @Param("majorId") Long majorId, @Param("gradeYear") Integer gradeYear);

    /** 学号是否已存在（学年内唯一，实际按全局唯一约束 + 学期归属判断） */
    public int countStudentNo(@Param("studentNo") String studentNo, @Param("excludeUserId") Long excludeUserId);

    public Long selectUserIdByStudentNo(String studentNo);

    /** 关闭该生在指定学期的当前归属（转班前调用） */
    public int closeCurrentMembership(@Param("userId") Long userId, @Param("termId") Long termId);

    /** 建立班级归属 */
    public int insertMembership(@Param("classId") Long classId, @Param("userId") Long userId,
                                @Param("termId") Long termId, @Param("createBy") String createBy);

    /** 该生是否有任何当前归属 */
    public int countCurrentMembership(Long userId);
}
