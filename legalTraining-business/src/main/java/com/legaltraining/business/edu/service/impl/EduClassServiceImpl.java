package com.legaltraining.business.edu.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.legaltraining.business.edu.domain.EduClass;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.mapper.EduClassMapper;
import com.legaltraining.business.edu.mapper.EduTermMapper;
import com.legaltraining.business.edu.service.IEduClassService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 班级 服务实现
 */
@Service
public class EduClassServiceImpl implements IEduClassService
{
    /** 学期状态：2 已归档 */
    private static final int TERM_ARCHIVED = 2;

    @Autowired
    private EduClassMapper eduClassMapper;

    @Autowired
    private EduTermMapper eduTermMapper;

    @Override
    public EduClass selectEduClassById(Long classId)
    {
        return eduClassMapper.selectEduClassById(classId);
    }

    @Override
    public List<EduClass> selectEduClassList(EduClass eduClass)
    {
        return eduClassMapper.selectEduClassList(eduClass);
    }

    @Override
    public int insertEduClass(EduClass eduClass)
    {
        checkTermEditable(eduClass.getTermId());
        if (eduClassMapper.countByTermAndCode(eduClass.getTermId(), eduClass.getClassCode(), null) > 0)
        {
            throw new ServiceException("该学期下班级编码「" + eduClass.getClassCode() + "」已存在");
        }
        if (eduClass.getStatus() == null)
        {
            eduClass.setStatus(0);
        }
        eduClass.setCreateBy(currentUsername());
        return eduClassMapper.insertEduClass(eduClass);
    }

    @Override
    public int updateEduClass(EduClass eduClass)
    {
        EduClass exist = eduClassMapper.selectEduClassById(eduClass.getClassId());
        if (exist == null)
        {
            throw new ServiceException("班级不存在或已删除");
        }
        if (eduClass.getTermId() != null)
        {
            checkTermEditable(eduClass.getTermId());
        }
        Long termId = eduClass.getTermId() != null ? eduClass.getTermId() : exist.getTermId();
        String code = StringUtils.isNotEmpty(eduClass.getClassCode()) ? eduClass.getClassCode() : exist.getClassCode();
        if (eduClassMapper.countByTermAndCode(termId, code, eduClass.getClassId()) > 0)
        {
            throw new ServiceException("该学期下班级编码「" + code + "」已存在");
        }
        eduClass.setUpdateBy(currentUsername());
        return eduClassMapper.updateEduClass(eduClass);
    }

    @Override
    public int deleteEduClassByIds(Long[] classIds)
    {
        if (classIds == null || classIds.length == 0)
        {
            return 0;
        }
        for (Long classId : classIds)
        {
            EduClass exist = eduClassMapper.selectEduClassById(classId);
            if (exist == null)
            {
                continue;
            }
            int students = eduClassMapper.countCurrentStudents(classId);
            if (students > 0)
            {
                throw new ServiceException("班级「" + exist.getClassName() + "」仍有 " + students
                        + " 名在册学生，请先转班后再删除");
            }
        }
        return eduClassMapper.deleteEduClassByIds(classIds);
    }

    @Override
    public int recountStudentCount(Long classId)
    {
        int count = eduClassMapper.countCurrentStudents(classId);
        eduClassMapper.updateStudentCount(classId, count);
        return count;
    }

    @Override
    public Map<String, Object> changeStatus(Long classId, Integer status)
    {
        EduClass exist = eduClassMapper.selectEduClassById(classId);
        if (exist == null)
        {
            throw new ServiceException("班级不存在或已删除");
        }
        if (status == null || (status != 0 && status != 1))
        {
            throw new ServiceException("状态值只能是 0（启用）或 1（归档）");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (status == 1)
        {
            // 归档前提示，但不阻断：任务仍按原班级快照继续运行（与学期归档口径一致）
            int inProgress = eduClassMapper.countInProgressTasks(classId);
            result.put("inProgressTasks", inProgress);
            result.put("warning", inProgress > 0
                    ? "该班仍有 " + inProgress + " 个任务进行中，归档后将不能再新增学生与发布任务"
                    : null);
        }

        EduClass update = new EduClass();
        update.setClassId(classId);
        update.setStatus(status);
        update.setUpdateBy(currentUsername());
        eduClassMapper.updateEduClass(update);

        result.put("classId", classId);
        result.put("status", status);
        result.put("message", status == 1 ? "班级已归档" : "班级已启用");
        return result;
    }

    /**
     * 已归档学期下不允许新增/调整班级（与 MG-01-06 "归档后禁止转班"同一条约束）
     */
    private void checkTermEditable(Long termId)
    {
        if (termId == null)
        {
            throw new ServiceException("所属学期不能为空");
        }
        EduTerm term = eduTermMapper.selectEduTermById(termId);
        if (term == null)
        {
            throw new ServiceException("所属学期不存在或已删除");
        }
        if (term.getStatus() != null && term.getStatus() == TERM_ARCHIVED)
        {
            throw new ServiceException("学期「" + term.getTermName() + "」已归档，需先解冻才能维护班级");
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
