package com.legaltraining.business.edu.service.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.mapper.EduTermMapper;
import com.legaltraining.business.edu.service.IEduTermService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 学年学期 服务实现
 */
@Service
public class EduTermServiceImpl implements IEduTermService
{
    @Autowired
    private EduTermMapper eduTermMapper;

    @Override
    public List<EduTerm> selectEduTermList(EduTerm eduTerm)
    {
        return eduTermMapper.selectEduTermList(eduTerm);
    }

    @Override
    public EduTerm selectEduTermById(Long termId)
    {
        return eduTermMapper.selectEduTermById(termId);
    }

    @Override
    public EduTerm selectCurrentTerm()
    {
        return eduTermMapper.selectCurrentTerm();
    }

    @Override
    public int insertEduTerm(EduTerm eduTerm)
    {
        checkDates(eduTerm);
        if (eduTermMapper.countBySchoolYearAndTermNo(eduTerm.getSchoolYear(), eduTerm.getTermNo(), null) > 0)
        {
            throw new ServiceException("该学年学期已存在，请勿重复创建");
        }
        if ("1".equals(eduTerm.getIsCurrent()))
        {
            eduTermMapper.clearCurrentTerm(null);
        }
        else if (StringUtils.isEmpty(eduTerm.getIsCurrent()))
        {
            eduTerm.setIsCurrent("0");
        }
        if (eduTerm.getStatus() == null)
        {
            // 成为当前学期即为"进行中"，否则为"筹备"
            eduTerm.setStatus("1".equals(eduTerm.getIsCurrent()) ? 1 : 0);
        }
        eduTerm.setCreateBy(currentUsername());
        return eduTermMapper.insertEduTerm(eduTerm);
    }

    @Override
    public int updateEduTerm(EduTerm eduTerm)
    {
        checkDates(eduTerm);
        EduTerm exist = eduTermMapper.selectEduTermById(eduTerm.getTermId());
        if (exist == null)
        {
            throw new ServiceException("学期不存在或已删除");
        }
        if (eduTermMapper.countBySchoolYearAndTermNo(eduTerm.getSchoolYear(), eduTerm.getTermNo(),
                eduTerm.getTermId()) > 0)
        {
            throw new ServiceException("该学年学期已存在，请勿重复创建");
        }
        if ("1".equals(eduTerm.getIsCurrent()))
        {
            eduTermMapper.clearCurrentTerm(eduTerm.getTermId());
        }
        eduTerm.setUpdateBy(currentUsername());
        return eduTermMapper.updateEduTerm(eduTerm);
    }

    @Override
    public int deleteEduTermByIds(Long[] termIds)
    {
        if (termIds == null || termIds.length == 0)
        {
            return 0;
        }
        for (Long termId : termIds)
        {
            EduTerm term = eduTermMapper.selectEduTermById(termId);
            if (term == null)
            {
                continue;
            }
            if ("1".equals(term.getIsCurrent()))
            {
                throw new ServiceException("当前学期不允许删除，请先切换当前学期");
            }
            if (term.getStatus() != null && term.getStatus() == 2)
            {
                throw new ServiceException("已归档学期不允许删除，如需更正请先申请解冻");
            }
        }
        return eduTermMapper.deleteEduTermByIds(termIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> switchCurrent(Long termId)
    {
        Map<String, Object> result = previewSwitch(termId);
        eduTermMapper.clearCurrentTerm(termId);
        EduTerm update = new EduTerm();
        update.setTermId(termId);
        update.setIsCurrent("1");
        update.setStatus(1);
        update.setUpdateBy(currentUsername());
        eduTermMapper.updateEduTerm(update);
        int inProgress = (Integer) result.get("inProgressTasks");
        String message = "已切换到「" + result.get("termName") + "」";
        result.put("message", inProgress > 0 ? message + "，上一学期仍有 " + inProgress + " 个任务进行中，将继续在原学期下运行" : message);
        return result;
    }

    @Override
    public Map<String, Object> previewSwitch(Long termId)
    {
        EduTerm target = eduTermMapper.selectEduTermById(termId);
        if (target == null)
        {
            throw new ServiceException("学期不存在或已删除");
        }
        if (target.getStatus() != null && target.getStatus() == 2)
        {
            throw new ServiceException("已归档学期不能设为当前学期");
        }

        EduTerm old = eduTermMapper.selectCurrentTerm();
        int inProgress = (old != null) ? eduTermMapper.countInProgressTasks(old.getTermId()) : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("termId", termId);
        result.put("termName", target.getTermName());
        result.put("oldTermId", old == null ? null : old.getTermId());
        result.put("oldTermName", old == null ? null : old.getTermName());
        result.put("inProgressTasks", inProgress);
        result.put("message", inProgress > 0
                ? "当前学期仍有 " + inProgress + " 个任务进行中，切换后将继续在原学期运行。确认切换到「" + target.getTermName() + "」？"
                : "确认切换到「" + target.getTermName() + "」？");
        return result;
    }

    private void checkDates(EduTerm term)
    {
        if (term.getStartDate() != null && term.getEndDate() != null && term.getEndDate().before(term.getStartDate()))
            throw new ServiceException("学期结束日期不能早于开始日期");
    }

    @Override
    public Map<String, Object> checkArchive(Long termId)
    {
        EduTerm term = eduTermMapper.selectEduTermById(termId);
        if (term == null)
        {
            throw new ServiceException("学期不存在或已删除");
        }

        int inProgressTasks = eduTermMapper.countInProgressTasks(termId);
        int unreviewed = eduTermMapper.countUnreviewedSubmissions(termId);
        int unpublishedScores = eduTermMapper.countUnpublishedScores(termId);
        int pendingRecs = eduTermMapper.countPendingRecommendations(termId);
        int missingTotals = eduTermMapper.countMissingCourseTotals(termId);

        // F07：阻断项 = 未批阅提交 + 未发布成绩 + 缺失课程总评（P10 要求 P6 成绩汇总先完成）。
        // 进行中任务、待处理成果推荐仍为提示项，不阻断归档。
        int blockers = unreviewed + unpublishedScores + missingTotals;
        boolean admin = SecurityUtils.isAdmin(SecurityUtils.getUserId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("termId", termId);
        result.put("termName", term.getTermName());
        result.put("inProgressTasks", inProgressTasks);
        result.put("unreviewedSubmissions", unreviewed);
        result.put("unpublishedScores", unpublishedScores);
        result.put("pendingRecommendations", pendingRecs);
        result.put("missingCourseTotals", missingTotals);
        result.put("needForce", blockers > 0);
        result.put("canArchive", blockers == 0);
        // 强制归档仅管理员可用；阻断项>0 且非管理员时明确提示无权限
        result.put("canForce", admin);
        result.put("message", buildArchiveMessage(inProgressTasks, unreviewed, unpublishedScores, pendingRecs, missingTotals, admin));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long termId, boolean force, String reason)
    {
        EduTerm term = eduTermMapper.selectEduTermById(termId);
        if (term == null)
        {
            throw new ServiceException("学期不存在或已删除");
        }
        if (term.getStatus() != null && term.getStatus() == 2)
        {
            throw new ServiceException("该学期已归档，无需重复归档");
        }

        Map<String, Object> report = checkArchive(termId);
        int unreviewed = (Integer) report.get("unreviewedSubmissions");
        int unpublishedScores = (Integer) report.get("unpublishedScores");
        int missingTotals = (Integer) report.get("missingCourseTotals");
        int blockers = unreviewed + unpublishedScores + missingTotals;

        if (blockers > 0)
        {
            // F07：强制归档仅管理员可执行，且必须填写原因
            if (!SecurityUtils.isAdmin(SecurityUtils.getUserId()))
            {
                throw new ServiceException("存在未完成的批阅或成绩汇总，仅系统管理员可强制归档");
            }
            if (!force)
            {
                throw new ServiceException(buildBlockerMessage(unreviewed, unpublishedScores, missingTotals)
                        + "，请先完成成绩汇总，或由管理员强制归档并填写原因");
            }
            if (StringUtils.isEmpty(reason))
            {
                throw new ServiceException("强制归档必须填写原因");
            }
        }

        // 未批提交在任务上留痕（"归档时未批"），再统一锁定任务状态
        eduTermMapper.markUnreviewedTasks(termId);
        eduTermMapper.archiveTasks(termId);

        EduTerm update = new EduTerm();
        update.setTermId(termId);
        update.setStatus(2);
        update.setArchiveTime(new Date());
        update.setArchiveBy(currentUsername());
        update.setArchiveReport(JSON.toJSONString(report));
        update.setUpdateBy(currentUsername());
        update.setRemark(StringUtils.isNotEmpty(reason) ? reason : term.getRemark());
        eduTermMapper.updateEduTerm(update);
    }

    private String buildArchiveMessage(int inProgressTasks, int unreviewed, int unpublishedScores,
            int pendingRecs, int missingTotals, boolean admin)
    {
        StringBuilder sb = new StringBuilder();
        if (unreviewed > 0)
        {
            sb.append("未批阅提交 ").append(unreviewed).append(" 份；");
        }
        if (unpublishedScores > 0)
        {
            sb.append("未发布成绩 ").append(unpublishedScores).append(" 条；");
        }
        if (missingTotals > 0)
        {
            sb.append("缺失课程总评 ").append(missingTotals).append(" 人/课；");
        }
        if (inProgressTasks > 0)
        {
            sb.append("进行中任务 ").append(inProgressTasks).append(" 个；");
        }
        if (pendingRecs > 0)
        {
            sb.append("待处理成果推荐 ").append(pendingRecs).append(" 条；");
        }
        String blockers = buildBlockerMessage(unreviewed, unpublishedScores, missingTotals);
        String hint;
        if (blockers.isEmpty())
        {
            hint = "无未完成事项，可正常归档";
        }
        else if (!admin)
        {
            hint = blockers + "，仅系统管理员可强制归档";
        }
        else
        {
            hint = blockers + "，请完成成绩汇总，或强制归档并填写原因";
        }
        if (sb.length() == 0) return hint;
        return hint + "（另：" + sb + "）";
    }

    private String buildBlockerMessage(int unreviewed, int unpublishedScores, int missingTotals)
    {
        StringBuilder sb = new StringBuilder();
        if (unreviewed > 0)
        {
            sb.append("未批阅提交 ").append(unreviewed).append(" 份");
        }
        if (unpublishedScores > 0)
        {
            if (sb.length() > 0) sb.append("、");
            sb.append("未发布成绩 ").append(unpublishedScores).append(" 条");
        }
        if (missingTotals > 0)
        {
            if (sb.length() > 0) sb.append("、");
            sb.append("缺失课程总评 ").append(missingTotals).append(" 人/课");
        }
        return sb.toString();
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
