package com.legaltraining.business.edu.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduSubmission;
import com.legaltraining.business.edu.domain.EduSubmissionFile;

/**
 * 作业提交 服务层（ST-02-01~05，状态机 6.2）
 */
public interface IEduSubmissionService
{
    EduSubmission selectById(Long submissionId);

    /** 我的提交（含退回原因、重交截止、是否可提交/可撤回） */
    EduSubmission my(Long taskId);

    /** 教师侧提交列表（批阅待办） */
    List<EduSubmission> selectSubmissionList(EduSubmission query);

    void requireReviewAccess(Long taskId);

    /** 草稿保存（自动/手动），不生成版本、不影响待办 */
    EduSubmission saveDraft(Long taskId, String content, List<EduSubmissionFile> files);

    /** 提交（幂等）：内容与附件未变化时返回同一提交，不新增版本 */
    EduSubmission submit(Long taskId, String content, List<EduSubmissionFile> files);

    /** 撤回：截止前可撤回，已批阅不可撤回 */
    EduSubmission withdraw(Long submissionId);

    /** 逾期补交申请（ST-02-05） */
    EduSubmission lateApply(Long submissionId, String reason);

    EduSubmission lateApplyForTask(Long taskId, String reason);

    /** 教师审批补交：approve=true 通过并给补交截止时间 */
    EduSubmission lateApprove(Long submissionId, boolean approve, Date deadline, String remark);

    /** 历史版本（TC-04-02） */
    List<Map<String, Object>> versions(Long submissionId);

    /** 当前版本附件 */
    List<EduSubmissionFile> files(Long submissionId, Integer version);
}
