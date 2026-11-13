package com.legaltraining.business.edu.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduReview;
import com.legaltraining.business.edu.domain.EduReviewScore;

/**
 * 批阅 数据层（TC-04）
 */
public interface EduReviewMapper
{
    EduReview selectEduReviewById(Long reviewId);

    /** 取某提交某版本的最新批阅 */
    EduReview selectBySubmissionAndVersion(@Param("submissionId") Long submissionId,
            @Param("version") Integer version);

    /** 待批列表：已提交未批阅 */
    List<EduReview> selectPendingList(EduReview query);

    /** 已批阅列表（含草稿） */
    List<EduReview> selectReviewList(EduReview query);

    int insertEduReview(EduReview review);

    int updateEduReview(EduReview review);

    int deleteEduReview(Long reviewId);

    // ---------- 逐项得分 ----------

    List<EduReviewScore> selectScores(Long reviewId);

    int deleteScores(Long reviewId);

    int batchInsertScores(@Param("reviewId") Long reviewId, @Param("list") List<EduReviewScore> list);

    /** 待批数量（门户待办用） */
    int countPendingByTeacher(@Param("teacherId") Long teacherId);

    /** 任务维度批阅进度 */
    List<Map<String, Object>> selectTaskReviewProgress(@Param("courseId") Long courseId);
}
