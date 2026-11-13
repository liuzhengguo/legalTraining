package com.legaltraining.business.edu.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduScore;
import com.legaltraining.business.edu.domain.EduTask;
import com.legaltraining.business.edu.mapper.EduScoreMapper;
import com.legaltraining.business.edu.mapper.EduTaskMapper;
import com.legaltraining.business.edu.service.IEduScoreService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 成绩 服务实现
 *
 * 发布规则：
 *   1) 只有批阅已发布的提交才有成绩记录（edu_review.status=1 且 is_return=0）
 *   2) 整任务发布把 status 0 待发布 → 1 已发布，并写 publish_time
 *   3) 评价方式为等级制（score_mode=1）时，按校级统一区间换算 level_code
 *   4) 更正必须填原因，写入 edu_score_adjust 留痕，status 置 2 已更正
 */
@Service
public class EduScoreServiceImpl implements IEduScoreService
{
    @Autowired
    private com.legaltraining.business.edu.scope.BusinessScopeChecker businessScope;

    private static final int SCORE_PENDING = 0;
    private static final int SCORE_PUBLISHED = 1;
    private static final int SCORE_ADJUSTED = 2;

    /** 评价方式：等级制 */
    private static final int MODE_LEVEL = 1;

    @Autowired
    private EduScoreMapper eduScoreMapper;

    @Autowired
    private EduTaskMapper eduTaskMapper;

    @Override
    public List<EduScore> selectScoreList(EduScore query)
    {
        return eduScoreMapper.selectScoreList(query);
    }

    @Override
    public List<EduScore> selectMyScores(Long courseId)
    {
        Long userId = currentUserId();
        return eduScoreMapper.selectMyScores(userId, courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishTask(Long taskId)
    {
        EduTask task = eduTaskMapper.selectEduTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("任务不存在或已删除");
        }
        businessScope.requireCourseAccess(task.getCourseId());
        EduScore query = new EduScore();
        query.setTaskId(taskId);
        query.setStatus(SCORE_PENDING);
        List<EduScore> pending = eduScoreMapper.selectScoreList(query);
        if (StringUtils.isEmpty(pending))
        {
            throw new ServiceException("没有待发布的成绩；请先完成批阅并发布批阅结果");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Date now = new Date();
        for (EduScore s : pending)
        {
            EduScore update = new EduScore();
            update.setScoreId(s.getScoreId());
            update.setStatus(SCORE_PUBLISHED);
            update.setPublishTime(now);
            if (task.getScoreMode() != null && task.getScoreMode() == MODE_LEVEL
                    && s.getFinalScore() != null)
            {
                update.setLevelCode(eduScoreMapper.selectLevelCodeByScore(s.getFinalScore()));
            }
            eduScoreMapper.updateScore(update);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", s.getUserId());
            row.put("studentNo", s.getStudentNo());
            row.put("studentName", s.getStudentName());
            row.put("finalScore", s.getFinalScore());
            row.put("levelCode", update.getLevelCode());
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("published", rows.size());
        result.put("rows", rows);
        result.put("message", "已发布 " + rows.size() + " 名学生的成绩");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adjust(Long scoreId, BigDecimal newScore, String reason)
    {
        EduScore exist = eduScoreMapper.selectById(scoreId);
        if (exist == null)
        {
            throw new ServiceException("成绩记录不存在");
        }
        if (StringUtils.isEmpty(reason))
        {
            throw new ServiceException("成绩更正必须填写原因");
        }
        if (reason.length() > 500)
        {
            throw new ServiceException("更正原因不能超过 500 字");
        }
        if (newScore == null)
        {
            throw new ServiceException("更正后的分数不能为空");
        }
        BigDecimal full = exist.getTotalScoreFull() == null ? new BigDecimal("100") : exist.getTotalScoreFull();
        if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(full) > 0)
        {
            throw new ServiceException("更正后的分数须在 0 ~ " + full + " 之间");
        }
        BigDecimal old = exist.getFinalScore();
        EduScore update = new EduScore();
        update.setScoreId(scoreId);
        update.setFinalScore(newScore);
        update.setStatus(SCORE_ADJUSTED);
        if (exist.getScoreMode() != null && exist.getScoreMode() == MODE_LEVEL)
        {
            update.setLevelCode(eduScoreMapper.selectLevelCodeByScore(newScore));
        }
        eduScoreMapper.updateScore(update);
        eduScoreMapper.insertAdjust(scoreId, old, newScore, reason, currentUserId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scoreId", scoreId);
        result.put("oldScore", old);
        result.put("newScore", newScore);
        result.put("reason", reason);
        result.put("notified", true);
        result.put("message", "成绩已更正并留痕，学生端将收到更正通知");
        return result;
    }

    @Override
    public List<Map<String, Object>> adjustLog(Long scoreId)
    {
        return eduScoreMapper.selectAdjustLog(scoreId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> calcCourseScore(Long courseId)
    {
        if (courseId == null)
        {
            throw new ServiceException("课程不能为空");
        }
        // 以已发布成绩为口径，逐学生求平均作为课程总评
        EduScore query = new EduScore();
        query.setCourseId(courseId);
        List<EduScore> all = eduScoreMapper.selectScoreList(query);
        Map<Long, List<EduScore>> byUser = new LinkedHashMap<>();
        for (EduScore s : all)
        {
            if (s.getStatus() == null || s.getStatus() == SCORE_PENDING)
            {
                continue;
            }
            byUser.computeIfAbsent(s.getUserId(), k -> new ArrayList<>()).add(s);
        }
        if (byUser.isEmpty())
        {
            throw new ServiceException("该课程暂无已发布成绩，无法计算总评");
        }
        int done = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<EduScore>> e : byUser.entrySet())
        {
            BigDecimal sum = BigDecimal.ZERO;
            int n = 0;
            for (EduScore s : e.getValue())
            {
                if (s.getFinalScore() != null)
                {
                    sum = sum.add(s.getFinalScore());
                    n++;
                }
            }
            if (n == 0)
            {
                continue;
            }
            BigDecimal avg = sum.divide(new BigDecimal(n), 2, RoundingMode.HALF_UP);
            String level = eduScoreMapper.selectLevelCodeByScore(avg);
            eduScoreMapper.upsertCourseScore(courseId, e.getKey(), avg, level, 1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", e.getKey());
            row.put("taskCount", n);
            row.put("totalScore", avg);
            row.put("levelCode", level);
            rows.add(row);
            done++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("studentCount", done);
        result.put("rows", rows);
        result.put("message", "已计算 " + done + " 名学生的课程总评");
        return result;
    }

    @Override
    public List<Map<String, Object>> courseScore(Long courseId, Long userId)
    {
        return eduScoreMapper.selectCourseScore(courseId, userId);
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
