package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduClass;

/**
 * 班级 数据层
 */
public interface EduClassMapper
{
    public EduClass selectEduClassById(Long classId);

    public List<EduClass> selectEduClassList(EduClass eduClass);

    public int insertEduClass(EduClass eduClass);

    public int updateEduClass(EduClass eduClass);

    public int deleteEduClassByIds(Long[] classIds);

    /**
     * 同一学期内班级编码唯一性校验
     */
    public int countByTermAndCode(@Param("termId") Long termId, @Param("classCode") String classCode,
                                  @Param("excludeId") Long excludeId);

    /**
     * 按学期查班级（导入按编码定位用）
     */
    public EduClass selectByTermAndCode(@Param("termId") Long termId, @Param("classCode") String classCode);

    /**
     * 在册学生数（is_current = '1'）
     */
    public int countCurrentStudents(Long classId);

    /**
     * 回写冗余人数
     */
    public int updateStudentCount(@Param("classId") Long classId, @Param("count") int count);

    /**
     * 按学期批量归档班级
     */
    public int archiveByTerm(Long termId);

    /**
     * 该班当前进行中的任务数（经参与人快照 join，不假设 task 有 class_id）
     */
    public int countInProgressTasks(Long classId);
}
