package com.legaltraining.business.edu.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduCourse;
import com.legaltraining.business.edu.domain.EduCourseExhibit;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.mapper.EduCourseMapper;
import com.legaltraining.business.edu.mapper.EduTermMapper;
import com.legaltraining.business.edu.service.IEduCourseService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 课程 服务实现
 *
 * 关键规则：
 * 1) 学期已归档（term.status=2）后，课程不可新增/编辑/发布，只能读。
 * 2) 课程编码在学期内唯一。
 * 3) 已发布课程改回草稿不允许（避免学生端课程突然消失），只能归档。
 * 4) 有任务的课程禁止删除（历史成绩需可追溯），改用归档。
 * 5) 复制课程只复制"结构"（带班、资源关联、展项关联），不复制学生提交与成绩。
 */
@Service
public class EduCourseServiceImpl implements IEduCourseService
{
    /** 学期状态：2 已归档 */
    private static final int TERM_ARCHIVED = 2;

    /** 课程状态 */
    private static final int COURSE_DRAFT = 0;
    private static final int COURSE_PUBLISHED = 1;
    private static final int COURSE_ARCHIVED = 2;

    @Autowired
    private EduCourseMapper eduCourseMapper;

    @Autowired
    private EduTermMapper eduTermMapper;

    @Autowired
    private com.legaltraining.business.edu.mapper.EduTaskMapper eduTaskMapper;

    @Autowired
    private com.legaltraining.business.edu.mapper.EduResourceMapper eduResourceMapper;

    @Override
    public EduCourse selectEduCourseById(Long courseId)
    {
        EduCourse course = eduCourseMapper.selectEduCourseById(courseId);
        if (course == null)
        {
            return null;
        }
        course.setClassIds(eduCourseMapper.selectClassIds(courseId));
        course.setExhibits(eduCourseMapper.selectExhibits(courseId));
        return course;
    }

    @Override
    public List<EduCourse> selectEduCourseList(EduCourse eduCourse)
    {
        List<EduCourse> list = eduCourseMapper.selectEduCourseList(eduCourse);
        for (EduCourse c : list)
        {
            c.setTaskCount(eduCourseMapper.countTaskByCourse(c.getCourseId()));
            c.setResourceCount(eduCourseMapper.countResourceByCourse(c.getCourseId()));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertEduCourse(EduCourse eduCourse)
    {
        checkTermEditable(eduCourse.getTermId());
        if (eduCourseMapper.countByTermAndCode(eduCourse.getTermId(), eduCourse.getCourseCode(), null) > 0)
        {
            throw new ServiceException("该学期下课程编码「" + eduCourse.getCourseCode() + "」已存在");
        }
        if (eduCourse.getStatus() == null)
        {
            eduCourse.setStatus(COURSE_DRAFT);
        }
        // 主班取带班列表第一个，便于列表展示与按班筛选
        if (eduCourse.getClassId() == null && StringUtils.isNotEmpty(eduCourse.getClassIds()))
        {
            eduCourse.setClassId(eduCourse.getClassIds().get(0));
        }
        eduCourse.setCreateBy(currentUsername());
        int rows = eduCourseMapper.insertEduCourse(eduCourse);
        saveClassIds(eduCourse.getCourseId(), eduCourse.getClassIds());
        saveExhibits(eduCourse.getCourseId(), eduCourse.getExhibits());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateEduCourse(EduCourse eduCourse)
    {
        EduCourse exist = eduCourseMapper.selectEduCourseById(eduCourse.getCourseId());
        if (exist == null)
        {
            throw new ServiceException("课程不存在或已删除");
        }
        if (COURSE_ARCHIVED == exist.getStatus())
        {
            throw new ServiceException("课程已归档，不可修改");
        }
        checkTermEditable(exist.getTermId());
        if (StringUtils.isNotEmpty(eduCourse.getCourseCode())
                && eduCourseMapper.countByTermAndCode(exist.getTermId(), eduCourse.getCourseCode(),
                        eduCourse.getCourseId()) > 0)
        {
            throw new ServiceException("该学期下课程编码「" + eduCourse.getCourseCode() + "」已存在");
        }
        if (eduCourse.getStatus() != null && eduCourse.getStatus() == COURSE_DRAFT
                && COURSE_PUBLISHED == exist.getStatus())
        {
            throw new ServiceException("已发布课程不可退回草稿，如需停止使用请归档");
        }
        eduCourse.setUpdateBy(currentUsername());
        int rows = eduCourseMapper.updateEduCourse(eduCourse);
        if (eduCourse.getClassIds() != null)
        {
            saveClassIds(eduCourse.getCourseId(), eduCourse.getClassIds());
        }
        if (eduCourse.getExhibits() != null)
        {
            saveExhibits(eduCourse.getCourseId(), eduCourse.getExhibits());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEduCourseById(Long courseId)
    {
        EduCourse exist = eduCourseMapper.selectEduCourseById(courseId);
        if (exist == null)
        {
            throw new ServiceException("课程不存在或已删除");
        }
        if (COURSE_PUBLISHED == exist.getStatus())
        {
            throw new ServiceException("已发布课程不可删除，请先归档");
        }
        int taskCount = eduCourseMapper.countTaskByCourse(courseId);
        if (taskCount > 0)
        {
            throw new ServiceException("课程下已有 " + taskCount + " 个任务，不可删除；请改为归档以保留历史数据");
        }
        eduCourseMapper.deleteCourseClass(courseId);
        eduCourseMapper.deleteCourseExhibit(courseId);
        return eduCourseMapper.deleteEduCourseById(courseId);
    }

    @Override
    public int publish(Long courseId)
    {
        EduCourse exist = eduCourseMapper.selectEduCourseById(courseId);
        if (exist == null)
        {
            throw new ServiceException("课程不存在或已删除");
        }
        if (COURSE_ARCHIVED == exist.getStatus())
        {
            throw new ServiceException("课程已归档，不可发布");
        }
        if (COURSE_PUBLISHED == exist.getStatus())
        {
            throw new ServiceException("课程已是发布状态");
        }
        checkTermEditable(exist.getTermId());
        List<Long> classIds = eduCourseMapper.selectClassIds(courseId);
        if (StringUtils.isEmpty(classIds) && exist.getClassId() == null)
        {
            throw new ServiceException("发布前请先为课程指定至少一个班级");
        }
        EduCourse update = new EduCourse();
        update.setCourseId(courseId);
        update.setStatus(COURSE_PUBLISHED);
        update.setPublishTime(new Date());
        update.setUpdateBy(currentUsername());
        return eduCourseMapper.updateEduCourse(update);
    }

    @Override
    public int archive(Long courseId)
    {
        EduCourse exist = eduCourseMapper.selectEduCourseById(courseId);
        if (exist == null)
        {
            throw new ServiceException("课程不存在或已删除");
        }
        if (COURSE_ARCHIVED == exist.getStatus())
        {
            throw new ServiceException("课程已是归档状态");
        }
        EduCourse update = new EduCourse();
        update.setCourseId(courseId);
        update.setStatus(COURSE_ARCHIVED);
        update.setUpdateBy(currentUsername());
        return eduCourseMapper.updateEduCourse(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> copyCourse(Long courseId, String newCourseCode, String newCourseName, Long newTermId)
    {
        EduCourse source = eduCourseMapper.selectEduCourseById(courseId);
        if (source == null)
        {
            throw new ServiceException("源课程不存在或已删除");
        }
        Long termId = newTermId == null ? source.getTermId() : newTermId;
        checkTermEditable(termId);
        String code = StringUtils.isEmpty(newCourseCode) ? source.getCourseCode() + "-COPY" : newCourseCode;
        if (eduCourseMapper.countByTermAndCode(termId, code, null) > 0)
        {
            throw new ServiceException("目标学期下课程编码「" + code + "」已存在，请换一个编码");
        }

        EduCourse target = new EduCourse();
        target.setCourseCode(code);
        target.setCourseName(StringUtils.isEmpty(newCourseName) ? source.getCourseName() + "（副本）" : newCourseName);
        target.setTermId(termId);
        target.setTeacherId(source.getTeacherId());
        boolean sameTerm = termId.equals(source.getTermId());
        target.setClassId(sameTerm ? source.getClassId() : null);
        target.setCover(source.getCover());
        target.setIntro(source.getIntro());
        // 副本一律从草稿开始，避免未配任务就先对学生可见
        target.setStatus(COURSE_DRAFT);
        target.setCopyFrom(courseId);
        target.setCreateBy(currentUsername());
        eduCourseMapper.insertEduCourse(target);

        List<Long> classIds = sameTerm ? eduCourseMapper.selectClassIds(courseId) : java.util.Collections.emptyList();
        saveClassIds(target.getCourseId(), classIds);
        saveExhibits(target.getCourseId(), eduCourseMapper.selectExhibits(courseId));

        int resourceCopied = eduCourseMapper.copyResourceLinks(courseId, target.getCourseId());
        int taskCopied = copyTaskTemplates(courseId, target.getCourseId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", target.getCourseId());
        result.put("courseCode", target.getCourseCode());
        result.put("termId", termId);
        result.put("copyFrom", courseId);
        result.put("classCopied", classIds == null ? 0 : classIds.size());
        result.put("resourceCopied", resourceCopied);
        result.put("taskCopied", taskCopied);
        result.put("offlineResources", eduCourseMapper.selectOfflineResourceTitles(target.getCourseId()));
        result.put("message", "课程已复制为草稿，请确认班级与任务后再发布");
        return result;
    }

    private int copyTaskTemplates(Long sourceCourseId, Long targetCourseId)
    {
        var query = new com.legaltraining.business.edu.domain.EduTask();
        query.setCourseId(sourceCourseId);
        var templates = eduTaskMapper.selectEduTaskList(query);
        java.util.Set<Long> copiedResources = new java.util.HashSet<>();
        for (var task : templates)
        {
            Long sourceTaskId = task.getTaskId();
            task.setTaskId(null);
            task.setCourseId(targetCourseId);
            task.setStatus(0);
            task.setPublishTime(null);
            task.setDeadlinePolicy(0);
            task.setGraceDays(3);
            task.setCreateBy(currentUsername());
            eduTaskMapper.insertEduTask(task);

            var scores = eduTaskMapper.selectScoreItems(sourceTaskId);
            var resources = eduTaskMapper.selectResourceIds(sourceTaskId);
            var exhibits = eduTaskMapper.selectExhibits(sourceTaskId);
            if (!scores.isEmpty()) eduTaskMapper.batchInsertScoreItems(task.getTaskId(), scores);
            if (!resources.isEmpty()) eduTaskMapper.batchInsertTaskResource(task.getTaskId(), resources);
            if (!exhibits.isEmpty()) eduTaskMapper.batchInsertTaskExhibit(task.getTaskId(), exhibits);
            copiedResources.addAll(resources);
        }
        copiedResources.forEach(eduResourceMapper::recountRef);
        return templates.size();
    }

    @Override
    public List<EduCourseExhibit> selectExhibits(Long courseId)
    {
        return eduCourseMapper.selectExhibits(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveExhibits(Long courseId, List<EduCourseExhibit> exhibits)
    {
        eduCourseMapper.deleteCourseExhibit(courseId);
        if (StringUtils.isEmpty(exhibits))
        {
            return 0;
        }
        List<EduCourseExhibit> clean = new ArrayList<>();
        for (EduCourseExhibit it : exhibits)
        {
            if (it == null || it.getTargetId() == null)
            {
                continue;
            }
            if (!"zone".equals(it.getTargetType()) && !"item".equals(it.getTargetType()))
            {
                throw new ServiceException("展项关联类型只能是 zone 或 item");
            }
            it.setCourseId(courseId);
            it.setSortNo(it.getSortNo() == null ? clean.size() : it.getSortNo());
            clean.add(it);
        }
        if (clean.isEmpty())
        {
            return 0;
        }
        return eduCourseMapper.batchInsertCourseExhibit(courseId, clean);
    }

    private void saveClassIds(Long courseId, List<Long> classIds)
    {
        eduCourseMapper.deleteCourseClass(courseId);
        if (StringUtils.isEmpty(classIds))
        {
            return;
        }
        List<Long> clean = new ArrayList<>();
        for (Long cid : classIds)
        {
            if (cid != null && !clean.contains(cid))
            {
                clean.add(cid);
            }
        }
        if (!clean.isEmpty())
        {
            eduCourseMapper.batchInsertCourseClass(courseId, clean);
        }
    }

    private void checkTermEditable(Long termId)
    {
        if (termId == null)
        {
            throw new ServiceException("所属学期不能为空");
        }
        EduTerm term = eduTermMapper.selectEduTermById(termId);
        if (term == null)
        {
            throw new ServiceException("所属学期不存在");
        }
        if (term.getStatus() != null && term.getStatus() == TERM_ARCHIVED)
        {
            throw new ServiceException("学期「" + term.getTermName() + "」已归档，不可新增或修改课程");
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
