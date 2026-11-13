package com.legaltraining.business.edu.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduCourse;
import com.legaltraining.business.edu.domain.EduTask;
import com.legaltraining.business.edu.domain.EduTaskExhibit;
import com.legaltraining.business.edu.domain.EduTaskScoreItem;
import com.legaltraining.business.edu.mapper.EduCourseMapper;
import com.legaltraining.business.edu.mapper.EduResourceMapper;
import com.legaltraining.business.edu.mapper.EduTaskMapper;
import com.legaltraining.business.edu.service.IEduTaskService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 实训任务 服务实现（状态机 6.1）
 *
 * 发布前置校验：
 *   1) 课程必须是已发布态（草稿课程不对外，任务发布无意义）
 *   2) start_time < deadline，且 deadline 必须晚于当前时间
 *   3) score_mode=2（多维度加权）时评分项权重合计必须 =100
 *   4) 统计性任务（finish_mode=1/2）必须给分组人数区间
 * 发布副作用：
 *   按课程带班在校生生成 edu_task_participant 快照（"发生时归属"），
 *   此后学生转班不影响本任务待办范围；关联资源 ref_count +1。
 */
@Service
public class EduTaskServiceImpl implements IEduTaskService
{
    /** 任务状态 */
    private static final int TASK_DRAFT = 0;
    private static final int TASK_RUNNING = 1;
    private static final int TASK_EXPIRED = 2;
    private static final int TASK_CANCELLED = 3;
    private static final int TASK_ARCHIVED = 4;

    /** 课程状态 */
    private static final int COURSE_PUBLISHED = 1;
    private static final int COURSE_ARCHIVED = 2;

    /** 提交状态镜像：6 任务取消 */
    private static final int SUBMIT_TASK_CANCELLED = 6;

    /** 评分方式：多维度加权 */
    private static final int SCORE_MODE_WEIGHT = 2;

    @Autowired
    private EduTaskMapper eduTaskMapper;

    @Autowired
    private EduCourseMapper eduCourseMapper;

    @Autowired
    private EduResourceMapper eduResourceMapper;

    @Override
    public EduTask selectEduTaskById(Long taskId)
    {
        EduTask task = eduTaskMapper.selectEduTaskById(taskId);
        if (task == null)
        {
            return null;
        }
        task.setScoreItems(eduTaskMapper.selectScoreItems(taskId));
        task.setResourceIds(eduTaskMapper.selectResourceIds(taskId));
        task.setExhibits(eduTaskMapper.selectExhibits(taskId));
        return task;
    }

    @Override
    public List<EduTask> selectEduTaskList(EduTask eduTask)
    {
        return eduTaskMapper.selectEduTaskList(eduTask);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertEduTask(EduTask eduTask)
    {
        EduCourse course = mustEditableCourse(eduTask.getCourseId());
        checkTime(eduTask.getStartTime(), eduTask.getDeadline());
        checkGroupRange(eduTask);
        // 新建一律草稿，避免漏校验直接对外
        eduTask.setStatus(TASK_DRAFT);
        if (eduTask.getDeadlinePolicy() == null)
        {
            eduTask.setDeadlinePolicy(0);
        }
        if (eduTask.getGraceDays() == null)
        {
            eduTask.setGraceDays(3);
        }
        if (eduTask.getFinishMode() == null)
        {
            eduTask.setFinishMode(0);
        }
        if (eduTask.getScoreMode() == null)
        {
            eduTask.setScoreMode(0);
        }
        if (eduTask.getTotalScore() == null)
        {
            eduTask.setTotalScore(new BigDecimal("100.00"));
        }
        eduTask.setWeightCheck(normalizeWeightCheck(eduTask.getScoreMode(), eduTask.getScoreItems()));
        eduTask.setCreateBy(currentUsername());
        int rows = eduTaskMapper.insertEduTask(eduTask);
        saveScoreItems(eduTask);
        saveResources(eduTask.getTaskId(), eduTask.getResourceIds());
        saveExhibits(eduTask.getTaskId(), eduTask.getExhibits());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateEduTask(EduTask eduTask)
    {
        EduTask exist = mustExist(eduTask.getTaskId());
        if (exist.getStatus() != null && exist.getStatus() == TASK_CANCELLED)
        {
            throw new ServiceException("任务已撤回，不可修改");
        }
        if (exist.getStatus() != null && exist.getStatus() >= TASK_EXPIRED)
        {
            throw new ServiceException("任务已截止或归档，不可修改；如需延长请使用「延期」");
        }
        mustEditableCourse(exist.getCourseId());
        if (eduTask.getStartTime() != null || eduTask.getDeadline() != null)
        {
            checkTime(eduTask.getStartTime() == null ? exist.getStartTime() : eduTask.getStartTime(),
                    eduTask.getDeadline() == null ? exist.getDeadline() : eduTask.getDeadline());
        }
        if (eduTask.getFinishMode() != null || eduTask.getGroupMin() != null
                || eduTask.getGroupMax() != null)
        {
            EduTask merged = new EduTask();
            merged.setFinishMode(eduTask.getFinishMode() == null ? exist.getFinishMode() : eduTask.getFinishMode());
            merged.setGroupMin(eduTask.getGroupMin() == null ? exist.getGroupMin() : eduTask.getGroupMin());
            merged.setGroupMax(eduTask.getGroupMax() == null ? exist.getGroupMax() : eduTask.getGroupMax());
            checkGroupRange(merged);
        }
        if (eduTask.getScoreItems() != null || eduTask.getScoreMode() != null)
        {
            int mode = eduTask.getScoreMode() == null ? exist.getScoreMode() : eduTask.getScoreMode();
            List<EduTaskScoreItem> items = eduTask.getScoreItems() == null
                    ? eduTaskMapper.selectScoreItems(exist.getTaskId()) : eduTask.getScoreItems();
            eduTask.setWeightCheck(normalizeWeightCheck(mode, items));
        }
        // 已发布任务改时间要同步参与人逾期标记，这里只允许通过延期接口改
        if (exist.getStatus() != null && exist.getStatus() == TASK_RUNNING && eduTask.getDeadline() != null
                && !eduTask.getDeadline().equals(exist.getDeadline()))
        {
            throw new ServiceException("任务进行中，请使用「延期」接口修改截止时间");
        }
        eduTask.setUpdateBy(currentUsername());
        int rows = eduTaskMapper.updateEduTask(eduTask);
        if (eduTask.getScoreItems() != null)
        {
            saveScoreItems(eduTask);
        }
        if (eduTask.getResourceIds() != null)
        {
            if (exist.getStatus() != null && exist.getStatus() == TASK_RUNNING)
            {
                adjustRefCount(exist.getTaskId(), -1);
                saveResources(exist.getTaskId(), eduTask.getResourceIds());
                adjustRefCount(exist.getTaskId(), 1);
            }
            else
            {
                saveResources(exist.getTaskId(), eduTask.getResourceIds());
            }
        }
        if (eduTask.getExhibits() != null)
        {
            saveExhibits(exist.getTaskId(), eduTask.getExhibits());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEduTaskById(Long taskId)
    {
        EduTask exist = mustExist(taskId);
        if (exist.getStatus() != null && exist.getStatus() == TASK_RUNNING)
        {
            throw new ServiceException("任务进行中，请先撤回再删除");
        }
        if (eduTaskMapper.countPublishedScore(taskId) > 0)
        {
            throw new ServiceException("该任务成绩已发布，不可删除；如需停止使用请归档");
        }
        // 已发布过且未撤回的任务释放资源引用计数
        if (exist.getPublishTime() != null && exist.getStatus() != null
                && exist.getStatus() != TASK_CANCELLED)
        {
            adjustRefCount(taskId, -1);
        }
        eduTaskMapper.deleteScoreItems(taskId);
        eduTaskMapper.deleteTaskResource(taskId);
        eduTaskMapper.deleteTaskExhibit(taskId);
        eduTaskMapper.deleteParticipants(taskId);
        return eduTaskMapper.deleteEduTaskById(taskId);
    }

    @Override
    public Map<String, Object> weightCheck(EduTask eduTask)
    {
        List<EduTaskScoreItem> items = eduTask.getScoreItems();
        if (items == null && eduTask.getTaskId() != null)
        {
            items = eduTaskMapper.selectScoreItems(eduTask.getTaskId());
        }
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> detail = new ArrayList<>();
        BigDecimal full = eduTask.getTotalScore() == null ? new BigDecimal("100") : eduTask.getTotalScore();
        if (items != null)
        {
            for (EduTaskScoreItem it : items)
            {
                if (it == null || it.getWeight() == null)
                {
                    continue;
                }
                total = total.add(it.getWeight());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("itemName", it.getItemName());
                row.put("weight", it.getWeight());
                // 折算示例：满分 × 权重% = 该项折合分值
                row.put("convertedScore", full.multiply(it.getWeight())
                        .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP));
                detail.add(row);
            }
        }
        boolean valid = total.compareTo(new BigDecimal("100")) == 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemCount", detail.size());
        result.put("weightSum", total);
        result.put("valid", valid);
        result.put("totalScore", full);
        result.put("items", detail);
        if (!valid)
        {
            result.put("message", "评分项权重合计为 " + total + "%，须等于 100% 才可发布");
        }
        else
        {
            result.put("message", "权重合计 100%，校验通过");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publish(Long taskId)
    {
        EduTask task = mustExist(taskId);
        if (task.getStatus() != null && task.getStatus() != TASK_DRAFT)
        {
            throw new ServiceException("只有草稿状态的任务可以发布，当前状态：" + statusText(task.getStatus()));
        }
        EduCourse course = mustEditableCourse(task.getCourseId());
        if (course.getStatus() == null || course.getStatus() != COURSE_PUBLISHED)
        {
            throw new ServiceException("所属课程尚未发布，请先发布课程");
        }
        checkTime(task.getStartTime(), task.getDeadline());
        if (task.getDeadline() != null && task.getDeadline().before(new Date()))
        {
            throw new ServiceException("截止时间已过，请先调整截止时间再发布");
        }
        if (task.getScoreMode() != null && task.getScoreMode() == SCORE_MODE_WEIGHT)
        {
            List<EduTaskScoreItem> items = eduTaskMapper.selectScoreItems(taskId);
            if (StringUtils.isEmpty(items))
            {
                throw new ServiceException("多维度加权评价必须配置评分项");
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (EduTaskScoreItem it : items)
            {
                if (it.getWeight() != null)
                {
                    sum = sum.add(it.getWeight());
                }
            }
            if (sum.compareTo(new BigDecimal("100")) != 0)
            {
                throw new ServiceException("评分项权重合计为 " + sum + "%，须等于 100% 才能发布");
            }
        }
        checkGroupRange(task);

        List<Long> students = eduTaskMapper.selectCourseStudentIds(task.getCourseId());
        if (StringUtils.isEmpty(students))
        {
            throw new ServiceException("所属课程下没有在册学生，无法发布；请先确认课程带班与班级学生");
        }

        EduTask update = new EduTask();
        update.setTaskId(taskId);
        update.setStatus(TASK_RUNNING);
        update.setPublishTime(new Date());
        update.setUpdateBy(currentUsername());
        eduTaskMapper.updateEduTask(update);

        eduTaskMapper.deleteParticipants(taskId);
        eduTaskMapper.batchInsertParticipants(taskId, students);
        if (task.getFinishMode() != null && task.getFinishMode() != 0)
        {
            // 分组任务：若已建好任务分组，回填 group_id
            eduTaskMapper.fillParticipantGroup(taskId);
        }
        adjustRefCount(taskId, 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", TASK_RUNNING);
        result.put("participantCount", students.size());
        result.put("publishTime", update.getPublishTime());
        result.put("message", "任务已发布，已为 " + students.size() + " 名学生生成待办");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeDeadline(Long taskId, Date deadline, String reason)
    {
        EduTask exist = mustExist(taskId);
        if (exist.getStatus() != null && exist.getStatus() >= TASK_EXPIRED)
        {
            throw new ServiceException("任务已截止或归档，不可延期");
        }
        if (deadline == null)
        {
            throw new ServiceException("新的截止时间不能为空");
        }
        if (exist.getDeadline() != null && !deadline.after(exist.getDeadline()))
        {
            throw new ServiceException("新截止时间必须晚于原截止时间");
        }
        if (reason != null && reason.length() > 500)
        {
            throw new ServiceException("延期原因不能超过 500 字");
        }
        String notice = "\n\n【延期通知 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())
                + "】截止时间调整为 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(deadline)
                + (StringUtils.isEmpty(reason) ? "" : "；原因：" + reason);
        EduTask update = new EduTask();
        update.setTaskId(taskId);
        update.setDeadline(deadline);
        // 已截止任务延期后恢复进行中，学生端重新开放提交
        if (exist.getStatus() != null && exist.getStatus() == TASK_EXPIRED)
        {
            update.setStatus(TASK_RUNNING);
        }
        update.setRequirement((exist.getRequirement() == null ? "" : exist.getRequirement()) + notice);
        update.setUpdateBy(currentUsername());
        return eduTaskMapper.updateEduTask(update);
    }

    @Override
    public int appendNotice(Long taskId, String notice)
    {
        EduTask exist = mustExist(taskId);
        if (StringUtils.isEmpty(notice))
        {
            throw new ServiceException("补充说明内容不能为空");
        }
        String merged = (exist.getRequirement() == null ? "" : exist.getRequirement())
                + "\n\n【补充说明 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())
                + "】" + notice;
        EduTask update = new EduTask();
        update.setTaskId(taskId);
        update.setRequirement(merged);
        update.setUpdateBy(currentUsername());
        return eduTaskMapper.updateEduTask(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recall(Long taskId, String reason)
    {
        EduTask exist = mustExist(taskId);
        if (exist.getStatus() == null || exist.getStatus() != TASK_RUNNING)
        {
            throw new ServiceException("只有进行中的任务可以撤回，当前状态：" + statusText(exist.getStatus()));
        }
        int submissionCount = eduTaskMapper.countSubmission(taskId);
        if (submissionCount > 0 && StringUtils.isEmpty(reason))
        {
            throw new ServiceException("已有 " + submissionCount + " 份提交，撤回必须填写原因并通知学生");
        }
        if (reason != null && reason.length() > 500)
        {
            throw new ServiceException("撤回原因不能超过 500 字");
        }
        EduTask update = new EduTask();
        update.setTaskId(taskId);
        update.setStatus(TASK_CANCELLED);
        update.setCancelReason(reason);
        update.setUpdateBy(currentUsername());
        eduTaskMapper.updateEduTask(update);
        // 参与人状态镜像为"任务取消"，避免学生端待办残留
        eduTaskMapper.cancelParticipants(taskId, SUBMIT_TASK_CANCELLED);
        adjustRefCount(taskId, -1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", TASK_CANCELLED);
        result.put("submissionCount", submissionCount);
        result.put("reason", reason);
        result.put("message", "任务已撤回" + (submissionCount > 0 ? "，已通知 " + submissionCount + " 名学生" : ""));
        return result;
    }

    @Override
    public int archive(Long taskId)
    {
        EduTask exist = mustExist(taskId);
        if (exist.getStatus() == null || exist.getStatus() != TASK_EXPIRED)
        {
            throw new ServiceException("只有已截止的任务可以归档，当前状态：" + statusText(exist.getStatus()));
        }
        EduTask update = new EduTask();
        update.setTaskId(taskId);
        update.setStatus(TASK_ARCHIVED);
        update.setUpdateBy(currentUsername());
        return eduTaskMapper.updateEduTask(update);
    }

    @Override
    public Map<String, Object> preview(Long taskId)
    {
        EduTask task = selectEduTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("任务不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("title", task.getTitle());
        result.put("courseName", task.getCourseName());
        result.put("requirement", task.getRequirement());
        result.put("templateFile", task.getTemplateFile());
        result.put("startTime", task.getStartTime());
        result.put("deadline", task.getDeadline());
        result.put("deadlinePolicy", task.getDeadlinePolicy());
        result.put("graceDays", task.getGraceDays());
        result.put("finishMode", task.getFinishMode());
        result.put("scoreMode", task.getScoreMode());
        result.put("totalScore", task.getTotalScore());
        result.put("scoreItems", task.getScoreItems());
        result.put("resourceIds", task.getResourceIds());
        result.put("exhibits", task.getExhibits());
        result.put("status", task.getStatus());
        result.put("deadlineText", deadlineText(task));
        return result;
    }

    @Override
    public int scanExpired()
    {
        return eduTaskMapper.expireTasks(new Date());
    }

    @Override
    public List<Map<String, Object>> participants(Long taskId)
    {
        mustExist(taskId);
        return eduTaskMapper.selectParticipantList(taskId);
    }

    // ---------- 内部 ----------

    /**
     * 截止说明文案：区分 严格/宽限/审批补交/不限
     */
    private String deadlineText(EduTask task)
    {
        if (task.getDeadline() == null)
        {
            return "未设置截止时间";
        }
        String base = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(task.getDeadline());
        int policy = task.getDeadlinePolicy() == null ? 0 : task.getDeadlinePolicy();
        switch (policy)
        {
            case 1:
                return base + " 截止，宽限 " + (task.getGraceDays() == null ? 0 : task.getGraceDays())
                        + " 天（宽限期内提交标记逾期）";
            case 2:
                return base + " 截止，逾期需提交补交申请并经教师审批";
            case 3:
                return base + " 建议完成时间（不限提交）";
            default:
                return base + " 截止，逾期不可提交";
        }
    }

    private void checkTime(Date start, Date deadline)
    {
        if (start == null || deadline == null)
        {
            throw new ServiceException("开始时间与截止时间不能为空");
        }
        if (!deadline.after(start))
        {
            throw new ServiceException("截止时间必须晚于开始时间");
        }
    }

    private void checkGroupRange(EduTask task)
    {
        if (task.getFinishMode() == null || task.getFinishMode() == 0)
        {
            return;
        }
        Integer min = task.getGroupMin();
        Integer max = task.getGroupMax();
        if (min == null || max == null)
        {
            throw new ServiceException("分组任务必须设置每组人数下限与上限");
        }
        if (min < 1 || max < min)
        {
            throw new ServiceException("每组人数区间不合法（下限≥1，上限≥下限）");
        }
    }

    private String normalizeWeightCheck(Integer scoreMode, List<EduTaskScoreItem> items)
    {
        if (scoreMode == null || scoreMode != SCORE_MODE_WEIGHT)
        {
            // 非加权模式不校验权重，直接标记为合规，避免前端误判
            return "1";
        }
        if (StringUtils.isEmpty(items))
        {
            return "0";
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (EduTaskScoreItem it : items)
        {
            if (it != null && it.getWeight() != null)
            {
                sum = sum.add(it.getWeight());
            }
        }
        return sum.compareTo(new BigDecimal("100")) == 0 ? "1" : "0";
    }

    private void saveScoreItems(EduTask eduTask)
    {
        eduTaskMapper.deleteScoreItems(eduTask.getTaskId());
        List<EduTaskScoreItem> items = eduTask.getScoreItems();
        if (StringUtils.isEmpty(items))
        {
            return;
        }
        List<EduTaskScoreItem> clean = new ArrayList<>();
        int idx = 0;
        for (EduTaskScoreItem it : items)
        {
            if (it == null || StringUtils.isEmpty(it.getItemName()))
            {
                continue;
            }
            if (it.getWeight() == null || it.getWeight().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("评分项「" + it.getItemName() + "」权重不合法");
            }
            it.setSortNo(it.getSortNo() == null ? idx : it.getSortNo());
            idx++;
            clean.add(it);
        }
        if (clean.isEmpty())
        {
            return;
        }
        eduTaskMapper.batchInsertScoreItems(eduTask.getTaskId(), clean);
    }

    private void saveResources(Long taskId, List<Long> resourceIds)
    {
        eduTaskMapper.deleteTaskResource(taskId);
        if (StringUtils.isEmpty(resourceIds))
        {
            return;
        }
        List<Long> clean = new ArrayList<>();
        for (Long rid : resourceIds)
        {
            if (rid != null && !clean.contains(rid))
            {
                clean.add(rid);
            }
        }
        if (!clean.isEmpty())
        {
            eduTaskMapper.batchInsertTaskResource(taskId, clean);
        }
    }

    private void saveExhibits(Long taskId, List<EduTaskExhibit> exhibits)
    {
        eduTaskMapper.deleteTaskExhibit(taskId);
        if (StringUtils.isEmpty(exhibits))
        {
            return;
        }
        List<EduTaskExhibit> clean = new ArrayList<>();
        for (EduTaskExhibit it : exhibits)
        {
            if (it == null || it.getTargetId() == null)
            {
                continue;
            }
            if (!"zone".equals(it.getTargetType()) && !"item".equals(it.getTargetType()))
            {
                throw new ServiceException("展项关联类型只能是 zone 或 item");
            }
            clean.add(it);
        }
        if (!clean.isEmpty())
        {
            eduTaskMapper.batchInsertTaskExhibit(taskId, clean);
        }
    }

    /** 关联资源引用计数整体加减（发布 +1，撤回/删除 -1） */
    private void adjustRefCount(Long taskId, int delta)
    {
        List<Long> ids = eduTaskMapper.selectResourceIds(taskId);
        if (StringUtils.isEmpty(ids))
        {
            return;
        }
        for (Long rid : ids)
        {
            eduResourceMapper.changeRefCount(rid, delta);
        }
    }

    private EduCourse mustEditableCourse(Long courseId)
    {
        if (courseId == null)
        {
            throw new ServiceException("所属课程不能为空");
        }
        EduCourse course = eduCourseMapper.selectEduCourseById(courseId);
        if (course == null)
        {
            throw new ServiceException("所属课程不存在或已删除");
        }
        if (course.getStatus() != null && course.getStatus() == COURSE_ARCHIVED)
        {
            throw new ServiceException("所属课程已归档，不可维护任务");
        }
        return course;
    }

    private EduTask mustExist(Long taskId)
    {
        EduTask task = eduTaskMapper.selectEduTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("任务不存在或已删除");
        }
        return task;
    }

    private String statusText(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        switch (status)
        {
            case TASK_DRAFT:
                return "草稿";
            case TASK_RUNNING:
                return "进行中";
            case TASK_EXPIRED:
                return "已截止";
            case TASK_CANCELLED:
                return "已取消";
            case TASK_ARCHIVED:
                return "已归档";
            default:
                return String.valueOf(status);
        }
    }

    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
