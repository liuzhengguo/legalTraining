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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public List<EduScore> selectScoreList(EduScore query)
    {
        Long teacherId = businessScope.hasAnyRole("registrar") ? null : currentUserId();
        return eduScoreMapper.selectScoreList(query, teacherId);
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
        businessScope.requireTermWritableByTask(taskId); // F07：归档后成绩写接口只读
        EduScore query = new EduScore();
        query.setTaskId(taskId);
        query.setStatus(SCORE_PENDING);
        List<EduScore> pending = eduScoreMapper.selectScoreList(query, null);
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
            if (eduScoreMapper.publishScore(update) == 0)
            {
                continue;
            }
            notifyStudent("NT-06", s, "成绩已发布", task.getTitle());

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
        EduScore exist = lockScore(scoreId);
        businessScope.requireCourseAccess(exist.getCourseId());
        businessScope.requireTermWritableByCourse(exist.getCourseId()); // F07
        if (exist.getStatus() == SCORE_PENDING)
        {
            throw new ServiceException("请先发布成绩再更正");
        }
        if (StringUtils.isBlank(reason))
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
        notifyStudent("NT-07", exist, "成绩已更正", exist.getTaskTitle() + "：" + reason);

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
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupAdjust(Long scoreId, BigDecimal adjust, String reason)
    {
        EduScore exist = lockScore(scoreId);
        businessScope.requireCourseAccess(exist.getCourseId());
        businessScope.requireTermWritableByCourse(exist.getCourseId()); // F07
        if (exist.getStatus() == SCORE_PENDING)
        {
            throw new ServiceException("请先发布成绩再设置组内加减分");
        }
        if (adjust == null)
        {
            throw new ServiceException("加减分值不能为空");
        }
        BigDecimal full = exist.getTotalScoreFull() == null ? new BigDecimal("100") : exist.getTotalScoreFull();
        // 组内加减分限制在 ±满分范围，final_score 限制在 0 ~ 满分
        if (adjust.abs().compareTo(full) > 0)
        {
            throw new ServiceException("加减分绝对值不能超过 " + full);
        }
        if (StringUtils.isBlank(reason) || reason.length() > 500)
        {
            throw new ServiceException("请填写加减分原因（不超过 500 字）");
        }
        BigDecimal oldFinal = exist.getFinalScore() == null ? BigDecimal.ZERO : exist.getFinalScore();
        BigDecimal newFinal = (exist.getTaskScore() == null ? BigDecimal.ZERO : exist.getTaskScore()).add(adjust);
        if (newFinal.compareTo(BigDecimal.ZERO) < 0 || newFinal.compareTo(full) > 0)
        {
            throw new ServiceException("加减分后成绩须在 0 ~ " + full + " 之间");
        }
        EduScore update = new EduScore();
        update.setScoreId(scoreId);
        update.setGroupAdjust(adjust);
        update.setFinalScore(newFinal);
        update.setStatus(SCORE_ADJUSTED);
        if (exist.getScoreMode() != null && exist.getScoreMode() == MODE_LEVEL)
        {
            update.setLevelCode(eduScoreMapper.selectLevelCodeByScore(newFinal));
        }
        eduScoreMapper.updateScore(update);
        eduScoreMapper.insertAdjust(scoreId, oldFinal, newFinal, "组内加减分：" + reason, currentUserId());
        notifyStudent("NT-07", exist, "组内加减分已更新", exist.getTaskTitle() + "：" + reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scoreId", scoreId);
        result.put("groupAdjust", adjust);
        result.put("finalScore", newFinal);
        result.put("message", "组内加减分已保存并留痕");
        return result;
    }

    @Override
    public List<Map<String, Object>> adjustLog(Long scoreId)
    {
        EduScore score = eduScoreMapper.selectById(scoreId);
        if (score == null)
        {
            throw new ServiceException("成绩记录不存在");
        }
        if (!currentUserId().equals(score.getUserId()) || score.getStatus() == SCORE_PENDING)
        {
            businessScope.requireCourseAccess(score.getCourseId());
        }
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
        businessScope.requireCourseAccess(courseId);
        businessScope.requireTermWritableByCourse(courseId); // F07
        // 以已发布成绩为口径，逐学生求平均作为课程总评
        EduScore query = new EduScore();
        query.setCourseId(courseId);
        List<EduScore> all = eduScoreMapper.selectScoreList(query, null);
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
        if (!businessScope.canAccessCourse(courseId))
        {
            userId = currentUserId();
        }
        return eduScoreMapper.selectCourseScore(courseId, userId);
    }

    @Override
    public List<Map<String, Object>> appeals(boolean mine, Long taskId)
    {
        String sql = """
                select a.appeal_id as appealId, a.score_id as scoreId, a.reason, a.status,
                       a.reply, a.create_time as createTime, a.handle_time as handleTime,
                       s.final_score as finalScore, s.level_code as levelCode,
                       t.title as taskTitle, u.nick_name as studentName
                from edu_score_appeal a join edu_score s on s.score_id = a.score_id
                join edu_task t on t.task_id = s.task_id
                join edu_course c on c.course_id = s.course_id
                join sys_user u on u.user_id = s.user_id
                where s.status in (1, 2)
                """;
        List<Object> args = new ArrayList<>();
        if (mine)
        {
            sql += " and s.user_id = ?";
            args.add(currentUserId());
        }
        else if (!businessScope.hasAnyRole("registrar"))
        {
            sql += " and c.teacher_id = ? and c.del_flag = '0'";
            args.add(currentUserId());
        }
        if (taskId != null)
        {
            sql += " and s.task_id = ?";
            args.add(taskId);
        }
        return jdbc.queryForList(sql + " order by a.appeal_id desc", args.toArray());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAppeal(Long scoreId, String reason)
    {
        EduScore score = lockScore(scoreId);
        if (!currentUserId().equals(score.getUserId()) || score.getStatus() == SCORE_PENDING)
        {
            throw new ServiceException("只能对自己的已发布成绩提出异议");
        }
        if (jdbc.queryForObject("select count(*) from edu_score_appeal where score_id = ?",
                Integer.class, scoreId) > 0)
        {
            throw new ServiceException("该成绩已有异议申请，可在成绩页查看处理结果");
        }
        jdbc.update("insert into edu_score_appeal(score_id, reason) values (?, ?)", scoreId, reason.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveAppeal(Long appealId, String reply, BigDecimal newScore)
    {
        List<Long> scoreIds = jdbc.queryForList("select score_id from edu_score_appeal where appeal_id = ?",
                Long.class, appealId);
        if (scoreIds.isEmpty())
        {
            throw new ServiceException("成绩异议不存在");
        }
        EduScore score = lockScore(scoreIds.get(0));
        businessScope.requireCourseAccess(score.getCourseId());
        businessScope.requireTermWritableByCourse(score.getCourseId()); // F07
        if (score.getStatus() == SCORE_PENDING)
        {
            throw new ServiceException("成绩尚未发布");
        }
        int updated = jdbc.update("""
                update edu_score_appeal set status = ?, reply = ?, handler_id = ?, handle_time = now()
                where appeal_id = ? and status = 0
                """, newScore == null ? 1 : 2, reply.trim(), currentUserId(), appealId);
        if (updated == 0)
        {
            throw new ServiceException("该异议已处理，请刷新查看");
        }
        if (newScore != null)
        {
            adjust(score.getScoreId(), newScore, reply.trim());
        }
        notifyStudent("SCORE-APPEAL", score, "成绩异议已处理", score.getTaskTitle() + "：" + reply.trim());
    }

    private EduScore lockScore(Long scoreId)
    {
        if (jdbc.queryForList("select score_id from edu_score where score_id = ? for update",
                Long.class, scoreId).isEmpty())
        {
            throw new ServiceException("成绩记录不存在");
        }
        return eduScoreMapper.selectById(scoreId);
    }

    private void notifyStudent(String event, EduScore score, String title, String content)
    {
        jdbc.update("""
                insert into ops_notification(event_code, receiver_id, title, content, biz_type, biz_id, send_time)
                values (?, ?, ?, ?, 'task', ?, now())
                """, event, score.getUserId(), title, content, score.getTaskId());
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
