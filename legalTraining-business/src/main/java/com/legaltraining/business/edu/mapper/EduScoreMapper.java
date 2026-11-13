package com.legaltraining.business.edu.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduScore;

/**
 * 成绩 数据层（TC-04-05 / TC-05）
 */
public interface EduScoreMapper
{
    EduScore selectById(Long scoreId);

    EduScore selectByTaskAndUser(@Param("taskId") Long taskId, @Param("userId") Long userId);

    List<EduScore> selectScoreList(EduScore query);

    int insertScore(EduScore score);

    int updateScore(EduScore score);

    int deleteByTask(Long taskId);

    /** 成绩更正留痕 */
    int insertAdjust(@Param("scoreId") Long scoreId, @Param("oldScore") BigDecimal oldScore,
            @Param("newScore") BigDecimal newScore, @Param("reason") String reason,
            @Param("operatorId") Long operatorId);

    List<Map<String, Object>> selectAdjustLog(Long scoreId);

    /** 等级换算：按校级统一区间把分数映射为等级编码 */
    String selectLevelCodeByScore(@Param("score") BigDecimal score);

    /** 课程总评写入 */
    int upsertCourseScore(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("totalScore") BigDecimal totalScore, @Param("levelCode") String levelCode,
            @Param("status") Integer status);

    List<Map<String, Object>> selectCourseScore(@Param("courseId") Long courseId,
            @Param("userId") Long userId);

    /** 课程总评：各任务已发布成绩的平均分与任务数 */
    Map<String, Object> selectCourseAverage(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /** 成绩发布时批量取某任务下全部批阅通过的提交 */
    List<Map<String, Object>> selectReviewScoresByTask(Long taskId);

    /** 学生端：我的成绩 */
    List<EduScore> selectMyScores(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
