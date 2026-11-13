package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduSubmission;
import com.legaltraining.business.edu.domain.EduSubmissionFile;

/**
 * 作业提交 数据层（ST-02）
 */
public interface EduSubmissionMapper
{
    EduSubmission selectEduSubmissionById(Long submissionId);

    /** 唯一键查询：同一学生同一任务只有一条 */
    EduSubmission selectByTaskAndUser(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /** 任务下全部提交（教师批阅列表） */
    List<EduSubmission> selectSubmissionList(EduSubmission query);

    int insertEduSubmission(EduSubmission submission);

    int updateEduSubmission(EduSubmission submission);

    /** 参与人提交状态镜像同步（edu_task_participant.submit_status） */
    int syncParticipantStatus(@Param("taskId") Long taskId, @Param("userId") Long userId,
            @Param("submitStatus") Integer submitStatus, @Param("overdueFlag") String overdueFlag);

    // ---------- 附件 ----------

    List<EduSubmissionFile> selectFiles(@Param("submissionId") Long submissionId,
            @Param("version") Integer version);

    int deleteFilesByVersion(@Param("submissionId") Long submissionId, @Param("version") Integer version);

    int batchInsertFiles(@Param("submissionId") Long submissionId, @Param("version") Integer version,
            @Param("list") List<EduSubmissionFile> list);

    // ---------- 版本 ----------

    /** 写版本快照（内容 + 附件 JSON） */
    int insertVersion(@Param("submissionId") Long submissionId, @Param("version") Integer version,
            @Param("content") String content, @Param("fileSnapshot") String fileSnapshot,
            @Param("operatorId") Long operatorId);

    List<java.util.LinkedHashMap> selectVersions(Long submissionId);

    /** 最近一次退回原因（来自 edu_review） */
    String selectLastReturnReason(Long submissionId);

    int countByTaskAndStatus(@Param("taskId") Long taskId, @Param("status") Integer status);
}
