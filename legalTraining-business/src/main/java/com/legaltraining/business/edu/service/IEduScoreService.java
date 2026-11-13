package com.legaltraining.business.edu.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduScore;

/**
 * 成绩 服务层（TC-04-05 / TC-05-04）
 */
public interface IEduScoreService
{
    List<EduScore> selectScoreList(EduScore query);

    /** 学生端：我的成绩 */
    List<EduScore> selectMyScores(Long courseId);

    /** 整任务成绩发布（TC-04-05）：待发布 → 已发布，等级制同时换算等级 */
    Map<String, Object> publishTask(Long taskId);

    /** 成绩更正（TC-04-06）：留痕 + 通知标记 */
    Map<String, Object> adjust(Long scoreId, BigDecimal newScore, String reason);

    List<Map<String, Object>> adjustLog(Long scoreId);

    /** 课程总评计算（TC-05-04） */
    Map<String, Object> calcCourseScore(Long courseId);

    List<Map<String, Object>> courseScore(Long courseId, Long userId);
}
