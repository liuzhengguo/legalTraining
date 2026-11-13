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

        eduTermMapper.clearCurrentTerm(termId);

        EduTerm update = new EduTerm();
        update.setTermId(termId);
        update.setIsCurrent("1");
        update.setStatus(1);
        update.setUpdateBy(currentUsername());
        eduTermMapper.updateEduTerm(update);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("termId", termId);
        result.put("termName", target.getTermName());
        result.put("oldTermId", old == null ? null : old.getTermId());
        result.put("oldTermName", old == null ? null : old.getTermName());
        result.put("inProgressTasks", inProgress);
        result.put("message", inProgress > 0
                ? "已切换到「" + target.getTermName() + "」，上一学期仍有 " + inProgress + " 个任务进行中，将继续在原学期下运行"
                : "已切换到「" + target.getTermName() + "」");
        return result;
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("termId", termId);
        result.put("termName", term.getTermName());
        result.put("inProgressTasks", inProgressTasks);
        result.put("unreviewedSubmissions", unreviewed);
        result.put("unpublishedScores", unpublishedScores);
        result.put("pendingRecommendations", pendingRecs);
        // 未批阅提交是唯一需要强制归档的阻断项，其余为提示项
        result.put("needForce", unreviewed > 0);
        result.put("canArchive", unreviewed == 0);
        result.put("message", buildArchiveMessage(inProgressTasks, unreviewed, unpublishedScores, pendingRecs));
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
        if (unreviewed > 0 && !force)
        {
            throw new ServiceException("存在 " + unreviewed + " 份未批阅提交，如需强制归档请填写原因");
        }
        if (unreviewed > 0 && StringUtils.isEmpty(reason))
        {
            throw new ServiceException("强制归档必须填写原因");
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

    private String buildArchiveMessage(int inProgressTasks, int unreviewed, int unpublishedScores, int pendingRecs)
    {
        StringBuilder sb = new StringBuilder();
        if (inProgressTasks > 0)
        {
            sb.append("进行中任务 ").append(inProgressTasks).append(" 个；");
        }
        if (unreviewed > 0)
        {
            sb.append("未批阅提交 ").append(unreviewed).append(" 份；");
        }
        if (unpublishedScores > 0)
        {
            sb.append("未发布成绩 ").append(unpublishedScores).append(" 条；");
        }
        if (pendingRecs > 0)
        {
            sb.append("待处理成果推荐 ").append(pendingRecs).append(" 条；");
        }
        return sb.length() == 0 ? "无未完成事项，可正常归档" : sb.toString();
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
