package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduCourse;
import com.legaltraining.business.edu.domain.EduCourseExhibit;

/**
 * 课程 服务层（TC-01-01~03）
 */
public interface IEduCourseService
{
    EduCourse selectEduCourseById(Long courseId);

    List<EduCourse> selectEduCourseList(EduCourse eduCourse);

    /** 新增（草稿态；带班列表一并落库） */
    int insertEduCourse(EduCourse eduCourse);

    int updateEduCourse(EduCourse eduCourse);

    /** 删除：已有任务时禁止删除，避免历史成绩失联 */
    int deleteEduCourseById(Long courseId);

    /** 发布：0草稿 → 1已发布，需至少关联一个班级 */
    int publish(Long courseId);

    /** 归档：1已发布 → 2已归档，归档后只读 */
    int archive(Long courseId);

    /** 复制课程（TC-01-02）：复制基本信息/带班/资源关联/展项关联，不复制学生提交与成绩 */
    Map<String, Object> copyCourse(Long courseId, String newCourseCode, String newCourseName, Long newTermId);

    /** 课程关联的展区/展项 */
    List<EduCourseExhibit> selectExhibits(Long courseId);

    /** 覆盖式保存展项关联（TC-01-03） */
    int saveExhibits(Long courseId, List<EduCourseExhibit> exhibits);
}
