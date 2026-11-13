package com.legaltraining.business.edu.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduReview;
import com.legaltraining.business.edu.domain.EduReviewScore;
import com.legaltraining.business.edu.domain.EduScore;
import com.legaltraining.business.edu.domain.EduSubmission;
import com.legaltraining.business.edu.domain.EduTask;
import com.legaltraining.business.edu.domain.EduTaskScoreItem;
import com.legaltraining.business.edu.mapper.EduReviewMapper;
import com.legaltraining.business.edu.mapper.EduScoreMapper;
import com.legaltraining.business.edu.mapper.EduSubmissionMapper;
import com.legaltraining.business.edu.mapper.EduTaskMapper;
import com.legaltraining.business.edu.service.IEduReviewService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 批阅 服务实现
 *
 * 计分规则（与 TC-03-01 权重校验一致）：
 *   score_mode=2 多维度加权：合计 = Σ 各项得分；且要求 Σ 各项满分 = 任务满分（权重即分值）
 *   score_mode=0/1 百分制/等级制：合计 = 教师直接给的分（不超过任务满分）
 * 发布批阅后：提交状态 → 3 已批阅，参与人镜像 → 3，并生成"待发布"成绩记录。
 */
@Service
public class EduReviewServiceImpl implements IEduReviewService
{
    @Autowired
    private com.legaltraining.business.edu.scope.BusinessScopeChecker businessScope;

    private static final int REVIEW_DRAFT = 0;
    private static final int REVIEW_PUBLISHED = 1;

    private static final int SUB_SUBMITTED = 2;
    private static final int SUB_REVIEWED = 3;
    private static final int SUB_RETURNED = 4;

    private static final int SCORE_MODE_WEIGHT = 2;

    /** 退回原因最少字数（TC-04-04） */
    private static final int RETURN_REASON_MIN = 20;

    @Autowired
    private EduReviewMapper eduReviewMapper;

    @Autowired
    private EduSubmissionMapper eduSubmissionMapper;

    @Autowired
    private EduTaskMapper eduTaskMapper;

    @Autowired
    private EduScoreMapper eduScoreMapper;

    @Override
    public List<EduReview> selectPendingList(EduReview query)
    {
        return eduReviewMapper.selectPendingList(query);
    }

    @Override
    public List<EduReview> selectReviewList(EduReview query)
    {
        List<EduReview> list = eduReviewMapper.selectReviewList(query);
        for (EduReview r : list)
        {
            r.setScores(eduReviewMapper.selectScores(r.getReviewId()));
        }
        return list;
    }

    @Override
    public EduReview selectById(Long reviewId)
    {
        EduReview r = eduReviewMapper.selectEduReviewById(reviewId);
        if (r != null)
        {
            r.setScores(eduReviewMapper.selectScores(reviewId));
        }
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduReview save(EduReview review)
    {
        if (review.getSubmissionId() == null)
        {
            throw new ServiceException("提交记录不能为空");
        }
        EduSubmission sub = eduSubmissionMapper.selectEduSubmissionById(review.getSubmissionId());
        if (sub == null)
        {
            throw new ServiceException("提交记录不存在");
        }
        if (sub.getStatus() != null && sub.getStatus() == SUB_RETURNED)
        {
            throw new ServiceException("该提交已退回，等待学生重交后再批阅");
        }
        EduTask task = eduTaskMapper.selectEduTaskById(sub.getTaskId());
        if (task == null)
        {
            throw new ServiceException("任务不存在");
        }
        businessScope.requireCourseAccess(task.getCourseId());
        boolean publish = review.getStatus() != null && review.getStatus() == REVIEW_PUBLISHED;

        BigDecimal total = calcTotal(task, review.getScores(), review.getTotalScore(), publish);
        Integer version = sub.getVersion();

        EduReview exist = eduReviewMapper.selectBySubmissionAndVersion(sub.getSubmissionId(), version);
        EduReview target = new EduReview();
        target.setSubmissionId(sub.getSubmissionId());
        target.setSubmissionVersion(version);
        target.setTeacherId(currentUserId());
        target.setTotalScore(total);
        target.setReviewComment(review.getReviewComment());
        target.setReviewTime(new Date());
        target.setIsReturn("0");
        target.setStatus(publish ? REVIEW_PUBLISHED : REVIEW_DRAFT);
        if (publish)
        {
            target.setPublishTime(new Date());
        }

        if (exist == null)
        {
            eduReviewMapper.insertEduReview(target);
        }
        else
        {
            if (exist.getStatus() != null && exist.getStatus() == REVIEW_PUBLISHED && !publish)
            {
                throw new ServiceException("该版本批阅已发布，不可再存为草稿");
            }
            target.setReviewId(exist.getReviewId());
            eduReviewMapper.updateEduReview(target);
            eduReviewMapper.deleteScores(exist.getReviewId());
        }

        // 逐项得分落库
        List<EduReviewScore> scores = review.getScores();
        if (!StringUtils.isEmpty(scores))
        {
            List<EduReviewScore> clean = new ArrayList<>();
            for (EduReviewScore s : scores)
            {
                if (s == null || s.getItemId() == null || s.getScore() == null)
                {
                    continue;
                }
                clean.add(s);
            }
            if (!clean.isEmpty())
            {
                eduReviewMapper.batchInsertScores(target.getReviewId(), clean);
            }
        }

        if (publish)
        {
            EduSubmission update = new EduSubmission();
            update.setSubmissionId(sub.getSubmissionId());
            update.setStatus(SUB_REVIEWED);
            eduSubmissionMapper.updateEduSubmission(update);
            eduSubmissionMapper.syncParticipantStatus(sub.getTaskId(), sub.getUserId(), SUB_REVIEWED,
                    sub.getIsOverdue());
            upsertPendingScore(task, sub, total);
        }
        return selectById(target.getReviewId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduReview publish(Long reviewId)
    {
        EduReview review = eduReviewMapper.selectEduReviewById(reviewId);
        if (review == null)
        {
            throw new ServiceException("批阅记录不存在");
        }
        if (review.getStatus() != null && review.getStatus() == REVIEW_PUBLISHED)
        {
            throw new ServiceException("该批阅已发布");
        }
        if (review.getTotalScore() == null)
        {
            throw new ServiceException("请先录入得分再发布");
        }
        EduSubmission sub = eduSubmissionMapper.selectEduSubmissionById(review.getSubmissionId());
        if (sub == null)
        {
            throw new ServiceException("提交记录不存在");
        }
        EduTask task = eduTaskMapper.selectEduTaskById(sub.getTaskId());
        if (task == null)
        {
            throw new ServiceException("任务不存在");
        }
        businessScope.requireCourseAccess(task.getCourseId());
        EduReview update = new EduReview();
        update.setReviewId(reviewId);
        update.setStatus(REVIEW_PUBLISHED);
        update.setPublishTime(new Date());
        eduReviewMapper.updateEduReview(update);

        EduSubmission subUpdate = new EduSubmission();
        subUpdate.setSubmissionId(sub.getSubmissionId());
        subUpdate.setStatus(SUB_REVIEWED);
        eduSubmissionMapper.updateEduSubmission(subUpdate);
        eduSubmissionMapper.syncParticipantStatus(sub.getTaskId(), sub.getUserId(), SUB_REVIEWED,
                sub.getIsOverdue());
        upsertPendingScore(task, sub, review.getTotalScore());
        return selectById(reviewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduReview returnBack(Long submissionId, String reason, Date deadline)
    {
        EduSubmission sub = eduSubmissionMapper.selectEduSubmissionById(submissionId);
        if (sub == null)
        {
            throw new ServiceException("提交记录不存在");
        }
        EduTask task = eduTaskMapper.selectEduTaskById(sub.getTaskId());
        if (task == null)
        {
            throw new ServiceException("任务不存在");
        }
        businessScope.requireCourseAccess(task.getCourseId());
        if (StringUtils.isEmpty(reason) || reason.trim().length() < RETURN_REASON_MIN)
        {
            throw new ServiceException("退回原因不能少于 " + RETURN_REASON_MIN + " 字，请说明具体问题");
        }
        if (reason.length() > 500)
        {
            throw new ServiceException("退回原因不能超过 500 字");
        }
        if (deadline == null)
        {
            throw new ServiceException("请设置重交截止时间");
        }
        if (!deadline.after(new Date()))
        {
            throw new ServiceException("重交截止时间必须晚于当前时间");
        }
        EduReview review = new EduReview();
        review.setSubmissionId(submissionId);
        review.setSubmissionVersion(sub.getVersion());
        review.setTeacherId(currentUserId());
        review.setStatus(REVIEW_PUBLISHED);
        review.setReviewTime(new Date());
        review.setPublishTime(new Date());
        review.setIsReturn("1");
        review.setReturnReason(reason);
        review.setReturnDeadline(deadline);
        review.setReviewComment(reason);
        eduReviewMapper.insertEduReview(review);

        EduSubmission update = new EduSubmission();
        update.setSubmissionId(submissionId);
        update.setStatus(SUB_RETURNED);
        update.setResubmitDeadline(deadline);
        eduSubmissionMapper.updateEduSubmission(update);
        eduSubmissionMapper.syncParticipantStatus(sub.getTaskId(), sub.getUserId(), SUB_RETURNED,
                sub.getIsOverdue());
        return selectById(review.getReviewId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batch(List<Map<String, Object>> items)
    {
        if (StringUtils.isEmpty(items))
        {
            throw new ServiceException("批量批阅列表不能为空");
        }
        int ok = 0;
        int fail = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Map<String, Object> it : items)
        {
            Object sid = it.get("submissionId");
            Object sc = it.get("totalScore");
            try
            {
                if (sid == null || sc == null)
                {
                    throw new ServiceException("submissionId 与 totalScore 不能为空");
                }
                EduReview r = new EduReview();
                r.setSubmissionId(Long.valueOf(sid.toString()));
                r.setTotalScore(new BigDecimal(sc.toString()));
                r.setReviewComment((String) it.get("reviewComment"));
                r.setStatus(REVIEW_PUBLISHED);
                save(r);
                ok++;
            }
            catch (Exception e)
            {
                fail++;
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("submissionId", sid);
                f.put("reason", e.getMessage());
                failures.add(f);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ok);
        result.put("fail", fail);
        result.put("failures", failures);
        result.put("message", "批量批阅完成：成功 " + ok + " 条，失败 " + fail + " 条");
        return result;
    }

    @Override
    public List<Map<String, Object>> progress(Long courseId)
    {
        return eduReviewMapper.selectTaskReviewProgress(courseId);
    }

    // ---------- 内部 ----------

    /**
     * 计算合计得分，publish=true 时做完整性校验
     */
    private BigDecimal calcTotal(EduTask task, List<EduReviewScore> scores, BigDecimal direct,
            boolean publish)
    {
        BigDecimal full = task.getTotalScore() == null ? new BigDecimal("100") : task.getTotalScore();
        boolean weighted = task.getScoreMode() != null && task.getScoreMode() == SCORE_MODE_WEIGHT;
        if (!weighted)
        {
            BigDecimal total = direct == null ? BigDecimal.ZERO : direct;
            if (total.compareTo(BigDecimal.ZERO) < 0 || total.compareTo(full) > 0)
            {
                throw new ServiceException("得分必须在 0 ~ " + full + " 之间");
            }
            if (publish && direct == null)
            {
                throw new ServiceException("请先录入得分");
            }
            return total;
        }

        List<EduTaskScoreItem> items = eduTaskMapper.selectScoreItems(task.getTaskId());
        if (StringUtils.isEmpty(items))
        {
            throw new ServiceException("该任务未配置评分项，无法按维度评分");
        }
        Map<Long, EduTaskScoreItem> index = new HashMap<>();
        BigDecimal maxSum = BigDecimal.ZERO;
        for (EduTaskScoreItem it : items)
        {
            index.put(it.getItemId(), it);
            if (it.getMaxScore() != null)
            {
                maxSum = maxSum.add(it.getMaxScore());
            }
        }
        if (maxSum.compareTo(full) != 0)
        {
            throw new ServiceException("评分项满分合计为 " + maxSum + "，与任务满分 " + full
                    + " 不一致，请先修正评分项");
        }
        Map<Long, BigDecimal> got = new HashMap<>();
        if (!StringUtils.isEmpty(scores))
        {
            for (EduReviewScore s : scores)
            {
                if (s == null || s.getItemId() == null)
                {
                    continue;
                }
                EduTaskScoreItem item = index.get(s.getItemId());
                if (item == null)
                {
                    throw new ServiceException("评分项不属于该任务，itemId=" + s.getItemId());
                }
                BigDecimal sc = s.getScore() == null ? BigDecimal.ZERO : s.getScore();
                BigDecimal max = item.getMaxScore() == null ? BigDecimal.ZERO : item.getMaxScore();
                if (sc.compareTo(BigDecimal.ZERO) < 0 || sc.compareTo(max) > 0)
                {
                    throw new ServiceException("评分项「" + item.getItemName() + "」得分须在 0 ~ " + max + " 之间");
                }
                got.put(s.getItemId(), sc);
            }
        }
        if (publish && got.size() < index.size())
        {
            throw new ServiceException("还有 " + (index.size() - got.size()) + " 个评分项未打分，不能发布");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : got.values())
        {
            total = total.add(v);
        }
        return total;
    }

    /** 生成/更新"待发布"成绩记录，真正的发布由成绩模块统一执行 */
    private void upsertPendingScore(EduTask task, EduSubmission sub, BigDecimal total)
    {
        EduScore exist = eduScoreMapper.selectByTaskAndUser(sub.getTaskId(), sub.getUserId());
        BigDecimal adjust = exist == null || exist.getGroupAdjust() == null
                ? BigDecimal.ZERO : exist.getGroupAdjust();
        EduScore score = new EduScore();
        score.setTaskId(sub.getTaskId());
        score.setCourseId(task == null ? null : task.getCourseId());
        score.setUserId(sub.getUserId());
        score.setGroupId(sub.getGroupId());
        score.setTaskScore(total);
        score.setGroupAdjust(adjust);
        score.setFinalScore(total.add(adjust));
        score.setStatus(0);
        if (exist == null)
        {
            eduScoreMapper.insertScore(score);
        }
        else
        {
            score.setScoreId(exist.getScoreId());
            eduScoreMapper.updateScore(score);
        }
    }

    private Long currentUserId()
    {
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception e)
        {
            throw new ServiceException("无法获取当前登录用户");
        }
    }
}
