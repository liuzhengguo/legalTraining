package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduCourse;
import com.legaltraining.business.edu.domain.EduCourseExhibit;

/**
 * 课程 数据层
 */
public interface EduCourseMapper
{
    EduCourse selectEduCourseById(Long courseId);

    List<EduCourse> selectEduCourseList(EduCourse eduCourse);

    int insertEduCourse(EduCourse eduCourse);

    int updateEduCourse(EduCourse eduCourse);

    int deleteEduCourseById(Long courseId);

    /** 学期内课程编码重复校验（excludeId 为空表示新增） */
    int countByTermAndCode(@Param("termId") Long termId, @Param("courseCode") String courseCode,
            @Param("excludeId") Long excludeId);

    /** 课程带班列表 */
    List<Long> selectClassIds(Long courseId);

    int deleteCourseClass(Long courseId);

    int batchInsertCourseClass(@Param("courseId") Long courseId, @Param("classIds") List<Long> classIds);

    /** 课程关联展区/展项 */
    List<EduCourseExhibit> selectExhibits(Long courseId);

    int deleteCourseExhibit(Long courseId);

    int batchInsertCourseExhibit(@Param("courseId") Long courseId,
            @Param("list") List<EduCourseExhibit> list);

    /** 课程下任务数量（删除前校验） */
    int countTaskByCourse(Long courseId);

    /** 课程下资源数量 */
    int countResourceByCourse(Long courseId);

    /** 复制课程时的资源清单 */
    int copyResourceLinks(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    List<String> selectOfflineResourceTitles(Long courseId);
}
