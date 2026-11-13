package com.legaltraining.business.edu.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduReview;

/**
 * 批阅 服务层（TC-04-01~06）
 */
public interface IEduReviewService
{
    /** 待批列表（TC-04-01）：status=2 的提交，可按任务/学号/姓名筛选 */
    List<EduReview> selectPendingList(EduReview query);

    /** 已批阅列表 */
    List<EduReview> selectReviewList(EduReview query);

    EduReview selectById(Long reviewId);

    /** 保存批阅（TC-04-03）：逐项评分自动合计；publish=true 时同时发布 */
    EduReview save(EduReview review);

    /** 发布已保存的批阅草稿 */
    EduReview publish(Long reviewId);

    /** 退回（TC-04-04）：原因≥20 字 + 重交截止 */
    EduReview returnBack(Long submissionId, String reason, Date deadline);

    /** 批量批阅（TC-04-05）：仅给总分与评语，不做逐项 */
    Map<String, Object> batch(List<Map<String, Object>> items);

    /** 批阅进度（TC-04 列表视图 / MG-03-02） */
    List<Map<String, Object>> progress(Long courseId);
}
