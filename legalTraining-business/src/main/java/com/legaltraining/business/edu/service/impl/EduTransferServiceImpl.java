package com.legaltraining.business.edu.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduClass;
import com.legaltraining.business.edu.domain.EduGroup;
import com.legaltraining.business.edu.domain.EduMembership;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.domain.EduTransferLog;
import com.legaltraining.business.edu.mapper.EduClassMapper;
import com.legaltraining.business.edu.mapper.EduGroupMapper;
import com.legaltraining.business.edu.mapper.EduStudentMapper;
import com.legaltraining.business.edu.mapper.EduTermMapper;
import com.legaltraining.business.edu.mapper.EduTransferMapper;
import com.legaltraining.business.edu.service.IEduTransferService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 转班/转组 服务实现
 *
 * 口径（MG-01-06）：
 * 1. 转班只影响此后产生的数据，历史提交与成绩仍归属原班级（统计走发生时归属）；
 * 2. 学期归档后禁止转班；
 * 3. 学生在分组任务中时给出提示，不阻断转班（分组归属由教师后续处理）；
 * 4. 所有变更写 edu_transfer_log，学生与教师均可在历史中看到。
 */
@Service
public class EduTransferServiceImpl implements IEduTransferService
{
    @Autowired
    private EduTransferMapper eduTransferMapper;

    @Autowired
    private EduStudentMapper eduStudentMapper;

    @Autowired
    private EduClassMapper eduClassMapper;

    @Autowired
    private EduGroupMapper eduGroupMapper;

    @Autowired
    private EduTermMapper eduTermMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferClass(Long userId, Long toClassId, String remark)
    {
        if (userId == null || toClassId == null)
        {
            throw new ServiceException("学生与目标班级不能为空");
        }
        List<EduMembership> current = eduTransferMapper.selectMemberships(userId, true);
        if (current.isEmpty())
        {
            throw new ServiceException("该学生当前没有班级归属，无法转班；请先用导入或单条新增建立归属");
        }
        EduMembership from = current.get(0);
        if (toClassId.equals(from.getClassId()))
        {
            throw new ServiceException("目标班级与当前班级相同，无需转班");
        }

        EduClass toClass = eduClassMapper.selectEduClassById(toClassId);
        if (toClass == null)
        {
            throw new ServiceException("目标班级不存在或已删除");
        }
        if (toClass.getStatus() != null && toClass.getStatus() == 1)
        {
            throw new ServiceException("目标班级已归档，不能转入");
        }
        checkTermEditable(from.getTermId());
        if (toClass.getTermId() != null && !toClass.getTermId().equals(from.getTermId()))
        {
            throw new ServiceException("目标班级不属于当前学期，跨学期请通过学期切换与重新导入处理");
        }

        // 分组任务提示：不阻断，但要让操作人知道需要后续处理
        int groupTasks = eduTransferMapper.countGroupTasksInClass(from.getClassId(), userId);

        eduStudentMapper.closeCurrentMembership(userId, from.getTermId());
        eduStudentMapper.insertMembership(toClassId, userId, toClass.getTermId(), currentUsername());
        eduStudentMapper.updateProfile(userId, null, toClass.getMajorId(), toClass.getGradeYear());

        EduTransferLog log = new EduTransferLog();
        log.setUserId(userId);
        log.setTransferType("class");
        log.setFromId(from.getClassId());
        log.setFromName(from.getClassName());
        log.setToId(toClassId);
        log.setToName(toClass.getClassName());
        log.setTermId(from.getTermId());
        log.setOperatorId(currentUserId());
        log.setRemark(remark);
        eduTransferMapper.insertTransferLog(log);

        // 两侧班级人数重算
        eduClassMapper.updateStudentCount(from.getClassId(),
                eduClassMapper.countCurrentStudents(from.getClassId()));
        eduClassMapper.updateStudentCount(toClassId, eduClassMapper.countCurrentStudents(toClassId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("fromClassId", from.getClassId());
        result.put("fromClassName", from.getClassName());
        result.put("toClassId", toClassId);
        result.put("toClassName", toClass.getClassName());
        result.put("groupTasks", groupTasks);
        result.put("warning", groupTasks > 0
                ? "该生在原班级参与 " + groupTasks + " 个分组任务，转班后分组归属仍留在原班，请教师调整"
                : null);
        result.put("message", "已从「" + from.getClassName() + "」转入「" + toClass.getClassName()
                + "」，历史提交与成绩仍按原班级统计");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferGroup(Long userId, Long fromGroupId, Long toGroupId, String remark)
    {
        EduGroup from = eduGroupMapper.selectEduGroupById(fromGroupId);
        EduGroup to = eduGroupMapper.selectEduGroupById(toGroupId);
        if (from == null || to == null)
        {
            throw new ServiceException("原分组或目标分组不存在");
        }
        if (!from.getClassId().equals(to.getClassId()))
        {
            throw new ServiceException("转组只能在同一班级内进行；跨班请先转班");
        }
        int members = eduGroupMapper.selectMembers(toGroupId).size();
        if (to.getMemberMax() != null && members >= to.getMemberMax())
        {
            throw new ServiceException("目标分组「" + to.getGroupName() + "」已满（上限 "
                    + to.getMemberMax() + " 人）");
        }

        int moved = eduTransferMapper.moveGroupMember(fromGroupId, toGroupId, userId);
        if (moved == 0)
        {
            throw new ServiceException("该学生不在原分组中");
        }
        // 原组长离开后，组长顺延给剩余第一个成员，避免出现"组长不在组内"
        if (from.getLeaderId() != null && from.getLeaderId().equals(userId))
        {
            List<com.legaltraining.business.edu.domain.EduGroupMember> left =
                    eduGroupMapper.selectMembers(fromGroupId);
            EduGroup update = new EduGroup();
            update.setGroupId(fromGroupId);
            update.setLeaderId(left.isEmpty() ? null : left.get(0).getUserId());
            eduGroupMapper.updateEduGroup(update);
        }

        EduTransferLog log = new EduTransferLog();
        log.setUserId(userId);
        log.setTransferType("group");
        log.setFromId(fromGroupId);
        log.setFromName(from.getGroupName());
        log.setToId(toGroupId);
        log.setToName(to.getGroupName());
        log.setTermId(null);
        log.setOperatorId(currentUserId());
        log.setRemark(remark);
        eduTransferMapper.insertTransferLog(log);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("fromGroupId", fromGroupId);
        result.put("fromGroupName", from.getGroupName());
        result.put("toGroupId", toGroupId);
        result.put("toGroupName", to.getGroupName());
        result.put("message", "已从「" + from.getGroupName() + "」转到「" + to.getGroupName() + "」");
        return result;
    }

    @Override
    public List<EduMembership> selectMemberships(Long userId, boolean currentOnly)
    {
        return eduTransferMapper.selectMemberships(userId, currentOnly);
    }

    @Override
    public List<EduTransferLog> selectTransferLogs(EduTransferLog query)
    {
        return eduTransferMapper.selectTransferLogs(query);
    }

    private void checkTermEditable(Long termId)
    {
        if (termId == null)
        {
            return;
        }
        EduTerm term = eduTermMapper.selectEduTermById(termId);
        if (term != null && term.getStatus() != null && term.getStatus() == 2)
        {
            throw new ServiceException("学期「" + term.getTermName() + "」已归档，转班前需先解冻");
        }
    }

    private String currentUsername()
    {
        try
        {
            String name = SecurityUtils.getUsername();
            return StringUtils.isEmpty(name) ? "system" : name;
        }
        catch (Exception e)
        {
            return "system";
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
            return null;
        }
    }
}
