package com.legaltraining.business.edu.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.legaltraining.common.utils.file.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduSubmission;
import com.legaltraining.business.edu.domain.EduSubmissionFile;
import com.legaltraining.business.edu.domain.EduTask;
import com.legaltraining.business.edu.mapper.EduSubmissionMapper;
import com.legaltraining.business.edu.mapper.EduTaskMapper;
import com.legaltraining.business.edu.service.IEduSubmissionService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 作业提交 服务实现（状态机 6.2）
 *
 * 截止策略决定"能不能交"：
 *   deadline_policy=0 严格       → 过截止即拒绝
 *   deadline_policy=1 宽限       → 截止后 grace_days 天内可交，标记 is_overdue='1'
 *   deadline_policy=2 审批补交   → 截止后须 late_apply_status=2 且未过 resubmit_deadline
 *   deadline_policy=3 不限       → 任何时候可交，过截止标记逾期
 * 被退回（status=4）后，重交截止 resubmit_deadline 内可再次提交，不受任务截止时间限制。
 */
@Service
public class EduSubmissionServiceImpl implements IEduSubmissionService
{
    /** 提交状态（状态机 6.2） */
    private static final int SUB_NOT_SUBMITTED = 0;
    private static final int SUB_DRAFT = 1;
    private static final int SUB_SUBMITTED = 2;
    private static final int SUB_REVIEWED = 3;
    private static final int SUB_RETURNED = 4;
    private static final int SUB_OVERDUE = 5;

    /** 任务状态 */
    private static final int TASK_RUNNING = 1;
    private static final int TASK_EXPIRED = 2;
    private static final int TASK_CANCELLED = 3;
    private static final int TASK_ARCHIVED = 4;

    /** 补交申请状态 */
    private static final int LATE_NONE = 0;
    private static final int LATE_PENDING = 1;
    private static final int LATE_APPROVED = 2;
    private static final int LATE_REJECTED = 3;

    /** 正文上限与附件上限（ST-02-02） */
    private static final int CONTENT_MAX = 2000;
    private static final int FILE_MAX = 10;

    /** 缺省补交天数 */
    private static final int DEFAULT_LATE_DAYS = 3;

    @Autowired
    private EduSubmissionMapper eduSubmissionMapper;

    @Autowired
    private EduTaskMapper eduTaskMapper;

    @Autowired
    private EduTaskTeamService teams;

    @Override
    public EduSubmission selectById(Long submissionId)
    {
        EduSubmission sub = eduSubmissionMapper.selectEduSubmissionById(submissionId);
        if (sub != null)
        {
            sub.setFiles(eduSubmissionMapper.selectFiles(submissionId, sub.getVersion()));
        }
        return sub;
    }

    @Override
    public EduSubmission my(Long taskId)
    {
        Long userId = currentUserId();
        EduTask task = mustTask(taskId);
        checkParticipant(taskId, userId);
        EduTaskTeamService.Team team = teams.forStudent(task, userId);
        EduSubmission sub = submissionFor(taskId, userId, team);
        boolean maySubmit = !groupTask(task) || team != null && team.confirmed() && (team.leaderId() == userId || teams.anyMemberSubmit(taskId));
        if (sub == null)
        {
            sub = new EduSubmission();
            sub.setTaskId(taskId);
            sub.setUserId(userId);
            sub.setStatus(SUB_NOT_SUBMITTED);
            sub.setVersion(0);
            sub.setLateApplyStatus(LATE_NONE);
            sub.setSubmittable(maySubmit && canSubmit(task, null, new Date()));
            sub.setWithdrawable(Boolean.FALSE);
            return sub;
        }
        sub.setFiles(eduSubmissionMapper.selectFiles(sub.getSubmissionId(), sub.getVersion()));
        sub.setReturnReason(eduSubmissionMapper.selectLastReturnReason(sub.getSubmissionId()));
        sub.setSubmittable(maySubmit && canSubmit(task, sub, new Date()));
        sub.setWithdrawable(maySubmit && isWithdrawable(task, sub, new Date()));
        return sub;
    }

    @Override
    public List<EduSubmission> selectSubmissionList(EduSubmission query)
    {
        query.getParams().put("reviewTeacherId",
                businessScope.hasAnyRole("registrar") ? null : currentUserId());
        List<EduSubmission> list = eduSubmissionMapper.selectSubmissionList(query);
        for (EduSubmission s : list)
        {
            s.setReturnReason(eduSubmissionMapper.selectLastReturnReason(s.getSubmissionId()));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduSubmission saveDraft(Long taskId, String content, List<EduSubmissionFile> files)
    {
        teams.lockTask(taskId);
        EduTask task = mustTask(taskId);
        if (task.getStatus() != null && (task.getStatus() == TASK_CANCELLED || task.getStatus() == TASK_ARCHIVED))
        {
            throw new ServiceException("任务已撤回或归档，无法保存草稿");
        }
        checkContent(content);
        List<EduSubmissionFile> clean = cleanFiles(files);
        Long userId = currentUserId();
        checkParticipant(taskId, userId);
        EduTaskTeamService.Team team = requireTeam(task, userId, false);
        EduSubmission exist = submissionFor(taskId, userId, team);
        rejectLegacyWrite(taskId, team);
        Date now = new Date();
        if (exist == null)
        {
            EduSubmission sub = new EduSubmission();
            sub.setTaskId(taskId);
            setSubject(sub, userId, team);
            sub.setContent(content);
            sub.setVersion(1);
            sub.setStatus(SUB_DRAFT);
            sub.setDraftTime(now);
            sub.setIsOverdue("0");
            sub.setLateApplyStatus(LATE_NONE);
            eduSubmissionMapper.insertEduSubmission(sub);
            saveFiles(sub.getSubmissionId(), 1, clean);
            syncStatus(taskId, userId, team, SUB_DRAFT, "0");
            return selectById(sub.getSubmissionId());
        }
        if (exist.getStatus() != null && (exist.getStatus() == SUB_SUBMITTED || exist.getStatus() == SUB_REVIEWED))
        {
            throw new ServiceException("作业已提交或批阅，请在允许撤回或退回后再修改");
        }
        // Keep the return state and its resubmission deadline until the student submits again.
        if (Integer.valueOf(SUB_RETURNED).equals(exist.getStatus()))
        {
            throw new ServiceException("作业已退回，请修改后直接重交");
        }
        EduSubmission update = new EduSubmission();
        update.setSubmissionId(exist.getSubmissionId());
        update.setContent(content);
        update.setDraftTime(now);
        update.setStatus(SUB_DRAFT);
        eduSubmissionMapper.updateEduSubmission(update);
        saveFiles(exist.getSubmissionId(), exist.getVersion(), clean);
        syncStatus(taskId, userId, team, SUB_DRAFT, "0");
        return selectById(exist.getSubmissionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduSubmission submit(Long taskId, String content, List<EduSubmissionFile> files)
    {
        teams.lockTask(taskId);
        EduTask task = mustTask(taskId);
        if (task.getStatus() != null && (task.getStatus() == TASK_CANCELLED || task.getStatus() == TASK_ARCHIVED))
        {
            throw new ServiceException("任务已撤回或归档，无法提交");
        }
        checkContent(content);
        List<EduSubmissionFile> clean = cleanFiles(files);
        if (StringUtils.isEmpty(content) && clean.isEmpty())
        {
            throw new ServiceException("请填写作业说明或上传附件后再提交");
        }
        Long userId = currentUserId();
        checkParticipant(taskId, userId);
        EduTaskTeamService.Team team = requireTeam(task, userId, true);
        EduSubmission exist = submissionFor(taskId, userId, team);
        rejectLegacyWrite(taskId, team);
        Date now = new Date();

        // 幂等：内容与附件完全一致且已是提交态，直接返回原记录，不产生新版本
        if (exist != null && exist.getStatus() != null
                && (exist.getStatus() == SUB_SUBMITTED || exist.getStatus() == SUB_REVIEWED)
                && sameContent(exist, content, clean))
        {
            return selectById(exist.getSubmissionId());
        }

        boolean overdue = false;
        String reason = submitBlockReason(task, exist, now);
        if (reason != null)
        {
            throw new ServiceException(reason);
        }
        overdue = isOverdue(task, exist, now);

        if (exist == null)
        {
            EduSubmission sub = new EduSubmission();
            sub.setTaskId(taskId);
            setSubject(sub, userId, team);
            sub.setContent(content);
            sub.setVersion(1);
            sub.setStatus(SUB_SUBMITTED);
            sub.setSubmitTime(now);
            sub.setIsOverdue(overdue ? "1" : "0");
            sub.setLateApplyStatus(LATE_NONE);
            eduSubmissionMapper.insertEduSubmission(sub);
            saveFiles(sub.getSubmissionId(), 1, clean);
            writeVersion(sub.getSubmissionId(), 1, content, clean, userId);
            syncStatus(taskId, userId, team, SUB_SUBMITTED, overdue ? "1" : "0");
            notifyReceipt(taskId, sub.getSubmissionId(), 1, userId, team);
            return selectById(sub.getSubmissionId());
        }

        int newVersion = (exist.getVersion() == null ? 0 : exist.getVersion()) + 1;
        EduSubmission update = new EduSubmission();
        update.setSubmissionId(exist.getSubmissionId());
        update.setContent(content);
        update.setVersion(newVersion);
        update.setStatus(SUB_SUBMITTED);
        update.setSubmitTime(now);
        update.setIsOverdue(overdue ? "1" : "0");
        // 退回后重交成功即清空退回截止，避免重复利用
        update.setResubmitDeadline(exist.getResubmitDeadline());
        eduSubmissionMapper.updateEduSubmission(update);
        saveFiles(exist.getSubmissionId(), newVersion, clean);
        writeVersion(exist.getSubmissionId(), newVersion, content, clean, userId);
        syncStatus(taskId, userId, team, SUB_SUBMITTED, overdue ? "1" : "0");
        notifyReceipt(taskId, exist.getSubmissionId(), newVersion, userId, team);
        return selectById(exist.getSubmissionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public EduSubmission withdraw(Long submissionId)
    {
        EduSubmission sub = mustSubmission(submissionId);
        teams.lockTask(sub.getTaskId());
        sub = currentSubmission(submissionId);
        checkSelf(sub);
        EduTask task = mustTask(sub.getTaskId());
        EduTaskTeamService.Team team = requireTeam(task, currentUserId(), true);
        if (Integer.valueOf(1).equals(sub.getSubjectType())
                && (team == null || !Long.valueOf(team.id()).equals(sub.getGroupId())))
        {
            throw new ServiceException("提交不属于当前队伍");
        }
        Date now = new Date();
        if (sub.getStatus() != null && sub.getStatus() == SUB_REVIEWED)
        {
            throw new ServiceException("作业已批阅，不可撤回");
        }
        if (sub.getStatus() == null || (sub.getStatus() != SUB_SUBMITTED && sub.getStatus() != SUB_DRAFT))
        {
            throw new ServiceException("当前状态不可撤回");
        }
        if (task.getDeadline() != null && now.after(task.getDeadline()))
        {
            throw new ServiceException("已过截止时间，不可撤回");
        }
        if (eduSubmissionMapper.countReviewForVersion(submissionId, sub.getVersion()) > 0)
        {
            throw new ServiceException("教师已开始批阅，不可撤回");
        }
        EduSubmission update = new EduSubmission();
        update.setSubmissionId(submissionId);
        update.setStatus(SUB_DRAFT);
        update.setWithdrawTime(now);
        eduSubmissionMapper.updateEduSubmission(update);
        // 撤回后待办恢复为草稿态，仍在待批列表中移除
        syncStatus(task.getTaskId(), sub.getUserId(), team, SUB_DRAFT, "0");
        return selectById(submissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public EduSubmission lateApply(Long submissionId, String reason)
    {
        EduSubmission sub = mustSubmission(submissionId);
        teams.lockTask(sub.getTaskId());
        sub = currentSubmission(submissionId);
        checkSelf(sub);
        EduTask task = mustTask(sub.getTaskId());
        EduTaskTeamService.Team team = requireTeam(task, currentUserId(), true);
        if (Integer.valueOf(1).equals(sub.getSubjectType())
                && (team == null || !Long.valueOf(team.id()).equals(sub.getGroupId())))
        {
            throw new ServiceException("提交不属于当前队伍");
        }
        return applyLate(task, sub, reason, team);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduSubmission lateApplyForTask(Long taskId, String reason)
    {
        Long userId = currentUserId();
        teams.lockTask(taskId);
        EduTask task = mustTask(taskId);
        checkParticipant(taskId, userId);
        EduTaskTeamService.Team team = requireTeam(task, userId, true);
        EduSubmission existing = submissionFor(taskId, userId, team);
        rejectLegacyWrite(taskId, team);
        return applyLate(task, existing, reason, team);
    }

    private EduSubmission applyLate(EduTask task, EduSubmission sub, String reason, EduTaskTeamService.Team team)
    {
        if (!Integer.valueOf(2).equals(task.getDeadlinePolicy())
                || !(Integer.valueOf(TASK_RUNNING).equals(task.getStatus()) || Integer.valueOf(TASK_EXPIRED).equals(task.getStatus())))
        {
            throw new ServiceException("当前任务不接受补交申请");
        }
        if (task.getDeadline() == null || !new Date().after(task.getDeadline()))
        {
            throw new ServiceException("任务尚未截止，无需申请补交");
        }
        if (StringUtils.isEmpty(reason) || reason.trim().length() < 10)
        {
            throw new ServiceException("补交理由不能少于 10 个字");
        }
        if (reason.length() > 500)
        {
            throw new ServiceException("补交理由不能超过 500 字");
        }
        if (sub == null)
        {
            Long userId = currentUserId();
            sub = new EduSubmission();
            sub.setTaskId(task.getTaskId());
            setSubject(sub, userId, team);
            sub.setStatus(SUB_OVERDUE);
            sub.setVersion(0);
            sub.setIsOverdue("1");
            sub.setLateApplyStatus(LATE_PENDING);
            sub.setLateReason(reason.trim());
            eduSubmissionMapper.insertEduSubmission(sub);
            syncStatus(task.getTaskId(), userId, team, SUB_OVERDUE, "1");
            return selectById(sub.getSubmissionId());
        }
        if (!Integer.valueOf(SUB_DRAFT).equals(sub.getStatus()) && !Integer.valueOf(SUB_OVERDUE).equals(sub.getStatus())
                && !Integer.valueOf(SUB_NOT_SUBMITTED).equals(sub.getStatus()))
        {
            throw new ServiceException("当前提交状态不支持申请补交");
        }
        if (sub.getLateApplyStatus() != null && sub.getLateApplyStatus() == LATE_PENDING)
        {
            throw new ServiceException("补交申请正在审批中，请勿重复提交");
        }
        if (sub.getLateApplyStatus() != null && sub.getLateApplyStatus() == LATE_APPROVED)
        {
            throw new ServiceException("补交申请已通过，请在补交截止前完成提交");
        }
        EduSubmission update = new EduSubmission();
        update.setSubmissionId(sub.getSubmissionId());
        update.setLateApplyStatus(LATE_PENDING);
        update.setLateReason(reason);
        // 尚未建过提交记录的，这里补一条"逾期未交"占位，便于教师审批列表可见
        if (sub.getStatus() == null || sub.getStatus() == SUB_NOT_SUBMITTED)
        {
            update.setStatus(SUB_OVERDUE);
        }
        eduSubmissionMapper.updateEduSubmission(update);
        return selectById(sub.getSubmissionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public EduSubmission lateApprove(Long submissionId, boolean approve, Date deadline, String remark)
    {
        EduSubmission sub = mustSubmission(submissionId);
        requireReviewAccess(sub.getTaskId());
        teams.lockTask(sub.getTaskId());
        sub = currentSubmission(submissionId);
        if (sub.getLateApplyStatus() == null || sub.getLateApplyStatus() != LATE_PENDING)
        {
            throw new ServiceException("该提交没有待审批的补交申请");
        }
        EduSubmission update = new EduSubmission();
        update.setSubmissionId(submissionId);
        update.setLateApproveBy(currentUserId());
        update.setLateApproveTime(new Date());
        if (approve)
        {
            Date limit = deadline;
            if (limit == null)
            {
                limit = new Date(System.currentTimeMillis() + DEFAULT_LATE_DAYS * 24L * 3600L * 1000L);
            }
            if (!limit.after(new Date()))
            {
                throw new ServiceException("补交截止时间必须晚于当前时间");
            }
            update.setLateApplyStatus(LATE_APPROVED);
            update.setResubmitDeadline(limit);
            update.setStatus(SUB_DRAFT);
        }
        else
        {
            update.setLateApplyStatus(LATE_REJECTED);
            update.setStatus(SUB_OVERDUE);
            if (StringUtils.isNotEmpty(remark))
            {
                update.setRemark("补交驳回：" + remark);
            }
        }
        eduSubmissionMapper.updateEduSubmission(update);
        if (Integer.valueOf(1).equals(sub.getSubjectType()))
            eduSubmissionMapper.syncGroupStatus(sub.getTaskId(), sub.getGroupId(), update.getStatus(), "1");
        else eduSubmissionMapper.syncParticipantStatus(sub.getTaskId(), sub.getUserId(), update.getStatus(), "1");
        return selectById(submissionId);
    }

    @Override
    public List<Map<String, Object>> versions(Long submissionId)
    {
        mustSubmission(submissionId);
        List<Map<String, Object>> raw = new ArrayList<>();
        for (Object it : eduSubmissionMapper.selectVersions(submissionId))
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) it;
            Map<String, Object> clean = new LinkedHashMap<>(row);
            raw.add(clean);
        }
        return raw;
    }

    @Override
    public List<EduSubmissionFile> files(Long submissionId, Integer version)
    {
        return eduSubmissionMapper.selectFiles(submissionId, version);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> receipt(Long taskId)
    {
        EduSubmission submission = my(taskId);
        Long submissionId = submission.getSubmissionId();
        if (submissionId == null) throw new ServiceException("暂无正式提交回执");
        Map<String, Object> snapshot = eduSubmissionMapper.selectLatestReceipt(submissionId);
        if (snapshot == null) throw new ServiceException("暂无正式提交回执");
        int version = ((Number) snapshot.get("version")).intValue();
        List<?> files;
        try
        {
            var parsed = JSON.parseArray((String) snapshot.get("fileSnapshot"));
            files = parsed == null ? legacyReceiptFiles(submissionId, version)
                    : parsed.stream().filter(Map.class::isInstance).map(entry -> {
                        var file = (Map<?, ?>) entry;
                        return receiptFile((String) file.get("fileName"), (String) file.get("sha256"));
                    }).toList();
        }
        catch (JSONException ex) { files = legacyReceiptFiles(submissionId, version); }
        var result = new LinkedHashMap<String, Object>();
        result.put("submissionId", submissionId);
        result.put("taskId", taskId);
        result.put("version", version);
        result.put("submittedAt", snapshot.get("submittedAt"));
        result.put("currentStatus", submission.getStatus());
        result.put("files", files);
        return result;
    }

    private List<?> legacyReceiptFiles(long submissionId, int version)
    {
        return eduSubmissionMapper.selectFiles(submissionId, version).stream()
                .map(file -> receiptFile(file.getFileName(), null)).toList();
    }

    private Map<String, Object> receiptFile(String name, String hash)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", name);
        result.put("sha256", hash);
        result.put("hashStatus", hash == null ? "unavailable" : "recorded");
        return result;
    }

    private void notifyReceipt(long taskId, long submissionId, int version, long userId, EduTaskTeamService.Team team)
    {
        var participants = team == null ? List.of(userId) : eduSubmissionMapper.groupParticipants(taskId, team.id());
        if (!participants.isEmpty()) eduSubmissionMapper.insertReceiptNotices(taskId, submissionId, version, participants);
    }

    // ---------- 内部 ----------

    /** 返回不允许提交的原因；null 表示可提交 */
    private String submitBlockReason(EduTask task, EduSubmission sub, Date now)
    {
        if (task.getStartTime() != null && now.before(task.getStartTime()))
        {
            return "任务尚未开始";
        }
        if (sub != null && (Integer.valueOf(SUB_SUBMITTED).equals(sub.getStatus())
                || Integer.valueOf(SUB_REVIEWED).equals(sub.getStatus())))
        {
            return "作业已提交或批阅，请在允许撤回或退回后再修改";
        }
        // 被退回后，重交截止内优先放行
        if (sub != null && sub.getStatus() != null && sub.getStatus() == SUB_RETURNED
                && sub.getResubmitDeadline() != null && now.before(sub.getResubmitDeadline()))
        {
            return null;
        }
        if (task.getDeadline() != null && !now.after(task.getDeadline()))
        {
            return null;
        }
        int policy = task.getDeadlinePolicy() == null ? 0 : task.getDeadlinePolicy();
        switch (policy)
        {
            case 1:
                Date limit = graceLimit(task);
                if (limit != null && !now.after(limit))
                {
                    return null;
                }
                return "已超过宽限期（" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(limit)
                        + "），无法提交";
            case 2:
                if (sub != null && sub.getLateApplyStatus() != null && sub.getLateApplyStatus() == LATE_APPROVED
                        && sub.getResubmitDeadline() != null && !now.after(sub.getResubmitDeadline()))
                {
                    return null;
                }
                if (sub != null && sub.getLateApplyStatus() != null
                        && sub.getLateApplyStatus() == LATE_PENDING)
                {
                    return "补交申请审批中，请等待教师处理";
                }
                if (sub != null && sub.getLateApplyStatus() != null
                        && sub.getLateApplyStatus() == LATE_REJECTED)
                {
                    return "补交申请已被驳回，无法提交";
                }
                return "任务已截止，请先提交补交申请";
            case 3:
                return null;
            default:
                return "任务已截止，逾期不可提交";
        }
    }

    private boolean canSubmit(EduTask task, EduSubmission sub, Date now)
    {
        if (task.getStatus() != null && (task.getStatus() == TASK_CANCELLED || task.getStatus() == TASK_ARCHIVED))
        {
            return false;
        }
        if (sub != null && sub.getStatus() != null && sub.getStatus() == SUB_REVIEWED)
        {
            return false;
        }
        return submitBlockReason(task, sub, now) == null;
    }

    /** 是否逾期提交（能交但已过截止时间） */
    private boolean isOverdue(EduTask task, EduSubmission sub, Date now)
    {
        if (sub != null && sub.getStatus() != null && sub.getStatus() == SUB_RETURNED
                && sub.getResubmitDeadline() != null && now.before(sub.getResubmitDeadline()))
        {
            return true;
        }
        return task.getDeadline() != null && now.after(task.getDeadline());
    }

    private Date graceLimit(EduTask task)
    {
        if (task.getDeadline() == null)
        {
            return null;
        }
        int days = task.getGraceDays() == null ? 0 : task.getGraceDays();
        return new Date(task.getDeadline().getTime() + days * 24L * 3600L * 1000L);
    }

    private boolean isWithdrawable(EduTask task, EduSubmission sub, Date now)
    {
        if (sub.getStatus() == null || (sub.getStatus() != SUB_SUBMITTED && sub.getStatus() != SUB_DRAFT))
        {
            return false;
        }
        return (task.getDeadline() == null || !now.after(task.getDeadline()))
                && eduSubmissionMapper.countReviewForVersion(sub.getSubmissionId(), sub.getVersion()) == 0;
    }

    private boolean sameContent(EduSubmission exist, String content, List<EduSubmissionFile> files)
    {
        String oldContent = exist.getContent() == null ? "" : exist.getContent();
        String newContent = content == null ? "" : content;
        if (!oldContent.equals(newContent))
        {
            return false;
        }
        List<EduSubmissionFile> oldFiles = eduSubmissionMapper.selectFiles(exist.getSubmissionId(),
                exist.getVersion());
        if (oldFiles.size() != files.size())
        {
            return false;
        }
        for (int i = 0; i < files.size(); i++)
        {
            String a = oldFiles.get(i).getFileId();
            String b = files.get(i).getFileId();
            if (a == null ? b != null : !a.equals(b))
            {
                return false;
            }
        }
        return true;
    }

    private void checkContent(String content)
    {
        if (content != null && content.length() > CONTENT_MAX)
        {
            throw new ServiceException("作业说明不能超过 " + CONTENT_MAX + " 字");
        }
    }

    private List<EduSubmissionFile> cleanFiles(List<EduSubmissionFile> files)
    {
        List<EduSubmissionFile> clean = new ArrayList<>();
        if (StringUtils.isEmpty(files))
        {
            return clean;
        }
        if (files.size() > FILE_MAX)
        {
            throw new ServiceException("单次提交最多上传 " + FILE_MAX + " 个附件");
        }
        int idx = 0;
        for (EduSubmissionFile f : files)
        {
            if (f == null || StringUtils.isEmpty(f.getFileId()))
            {
                continue;
            }
            f.setSortNo(f.getSortNo() == null ? idx : f.getSortNo());
            idx++;
            clean.add(f);
        }
        return clean;
    }

    private void saveFiles(Long submissionId, Integer version, List<EduSubmissionFile> files)
    {
        eduSubmissionMapper.deleteFilesByVersion(submissionId, version);
        if (StringUtils.isEmpty(files))
        {
            return;
        }
        eduSubmissionMapper.batchInsertFiles(submissionId, version, files);
    }

    /** 版本快照：正文 + 附件清单 JSON，便于事后追溯（TC-04-02） */
    private void writeVersion(Long submissionId, int version, String content,
            List<EduSubmissionFile> files, Long operatorId)
    {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (EduSubmissionFile file : files)
        {
            Map<String, Object> entry = receiptFile(file.getFileName(), attachmentHash(file.getFileId()));
            entry.put("fileId", file.getFileId());
            snapshot.add(entry);
        }
        eduSubmissionMapper.insertVersion(submissionId, version, content, JSON.toJSONString(snapshot), operatorId);
    }

    private String attachmentHash(String fileId)
    {
        try
        {
            Path file = FileUtils.resolveStoredUpload(fileId);
            if (file == null || Files.size(file) > 100L * 1024 * 1024) return null;
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(file), sha256))
            {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(sha256.digest());
        }
        catch (IOException ex) { return null; }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private boolean groupTask(EduTask task)
    {
        return task.getFinishMode() != null && task.getFinishMode() != 0;
    }

    private EduTaskTeamService.Team requireTeam(EduTask task, long userId, boolean submit)
    {
        if (!groupTask(task)) return null;
        EduTaskTeamService.Team team = teams.forStudent(task, userId);
        if (team == null || !team.confirmed()) throw new ServiceException("请先完成组队");
        if (submit && team.leaderId() != userId && !teams.anyMemberSubmit(task.getTaskId())) throw new ServiceException("只有组长可提交、撤回或申请补交");
        return team;
    }

    private EduSubmission submissionFor(long taskId, long userId, EduTaskTeamService.Team team)
    {
        EduSubmission personal = eduSubmissionMapper.selectByTaskAndUser(taskId, userId);
        if (personal != null && Integer.valueOf(0).equals(personal.getSubjectType())) return personal;
        return team == null ? null : eduSubmissionMapper.selectByTaskAndGroup(taskId, team.id());
    }

    private void rejectLegacyWrite(long taskId, EduTaskTeamService.Team team)
    {
        if (team != null && teams.hasLegacyWork(taskId, team.id()))
            throw new ServiceException("该队伍存在旧版个人作业，请联系教师处理后再提交小组作业");
    }

    private void setSubject(EduSubmission sub, long userId, EduTaskTeamService.Team team)
    {
        sub.setSubjectType(team == null ? 0 : 1);
        sub.setUserId(team == null ? userId : team.leaderId());
        if (team != null) sub.setGroupId(team.id());
    }

    private void syncStatus(long taskId, long userId, EduTaskTeamService.Team team, int status, String overdue)
    {
        if (team == null) eduSubmissionMapper.syncParticipantStatus(taskId, userId, status, overdue);
        else eduSubmissionMapper.syncGroupStatus(taskId, team.id(), status, overdue);
    }

    /** 只有任务参与人（发布时快照的应参与学生）才能提交 */
    private void checkParticipant(Long taskId, Long userId)
    {
        if (isAdmin())
        {
            return;
        }
        if (eduTaskMapper.countParticipant(taskId, userId) == 0)
        {
            throw new ServiceException("你不在本任务的参与人名单中，无法提交（可能因转班导致）");
        }
    }

    private EduTask mustTask(Long taskId)
    {
        EduTask task = eduTaskMapper.selectEduTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("任务不存在或已删除");
        }
        return task;
    }

    @Autowired
    private com.legaltraining.business.edu.scope.BusinessScopeChecker businessScope;

    @Override
    public void requireReviewAccess(Long taskId)
    {
        businessScope.requireCourseAccess(mustTask(taskId).getCourseId());
    }

    private EduSubmission mustSubmission(Long submissionId)
    {
        EduSubmission sub = eduSubmissionMapper.selectEduSubmissionById(submissionId);
        if (sub == null)
        {
            throw new ServiceException("提交记录不存在");
        }
        return sub;
    }

    private EduSubmission currentSubmission(Long submissionId)
    {
        EduSubmission latest = eduSubmissionMapper.selectCurrentSubmission(submissionId);
        if (latest == null) throw new ServiceException("提交记录不存在");
        return latest;
    }

    /** 学生只能操作自己的提交；教师/管理员通过批阅接口处理，不走这里 */
    private void checkSelf(EduSubmission sub)
    {
        Long uid = currentUserId();
        if (Integer.valueOf(1).equals(sub.getSubjectType())) return;
        if (sub.getUserId() != null && !sub.getUserId().equals(uid) && !isAdmin())
        {
            throw new ServiceException("只能操作自己的提交");
        }
    }

    private boolean isAdmin()
    {
        try
        {
            return SecurityUtils.isAdmin(SecurityUtils.getUserId());
        }
        catch (Exception e)
        {
            return false;
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
