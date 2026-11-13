package com.legaltraining.business.edu.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduTask;
import com.legaltraining.business.edu.domain.EduTaskExhibit;
import com.legaltraining.business.edu.domain.EduTaskScoreItem;

/**
 * 实训任务 数据层（TC-03）
 */
public interface EduTaskMapper
{
    EduTask selectEduTaskById(Long taskId);

    List<EduTask> selectEduTaskList(EduTask eduTask);

    int insertEduTask(EduTask eduTask);

    int updateEduTask(EduTask eduTask);

    int deleteEduTaskById(Long taskId);

    // ---------- 评分项 ----------

    List<EduTaskScoreItem> selectScoreItems(Long taskId);

    int deleteScoreItems(Long taskId);

    int batchInsertScoreItems(@Param("taskId") Long taskId, @Param("list") List<EduTaskScoreItem> list);

    // ---------- 关联资源 / 展项 ----------

    List<Long> selectResourceIds(Long taskId);

    int deleteTaskResource(Long taskId);

    int batchInsertTaskResource(@Param("taskId") Long taskId, @Param("ids") List<Long> ids);

    List<EduTaskExhibit> selectExhibits(Long taskId);

    int deleteTaskExhibit(Long taskId);

    int batchInsertTaskExhibit(@Param("taskId") Long taskId, @Param("list") List<EduTaskExhibit> list);

    // ---------- 参与人 ----------

    /** 课程应参与学生（按课程带班取在册学生） */
    List<Long> selectCourseStudentIds(Long courseId);

    /** 分组任务：学生在本次任务分组中的组ID */
    Long selectTaskGroupId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    int deleteParticipants(Long taskId);

    int batchInsertParticipants(@Param("taskId") Long taskId, @Param("userIds") List<Long> userIds);

    /** 分组任务：按已有分组回填 participant.group_id */
    int fillParticipantGroup(@Param("taskId") Long taskId);

    int countParticipants(Long taskId);

    /** 是否为任务应参与人（提交前校验，避免非本课程学生提交） */
    int countParticipant(@Param("taskId") Long taskId, @Param("userId") Long userId);

    int countBySubmitStatus(@Param("taskId") Long taskId, @Param("submitStatus") Integer submitStatus);

    /** 把已过截止时间的进行中任务转"已截止"（TC-03-05 自动截止） */
    int expireTasks(@Param("now") Date now);

    /** 任务下提交数量（撤回前判断） */
    int countSubmission(Long taskId);

    /** 任务下已发布成绩数量（撤回/删除前判断） */
    int countPublishedScore(Long taskId);

    /** 任务撤回时批量改参与人提交状态镜像（6 任务取消） */
    int cancelParticipants(@Param("taskId") Long taskId, @Param("submitStatus") Integer submitStatus);

    /** 参与人名单（含姓名、学号、提交状态、分组） */
    List<java.util.Map<String, Object>> selectParticipantList(Long taskId);

    /** 课程下任务数（课程删除前判断，已在课程 Mapper 中实现，这里供监控统计） */
    int countByCourseAndStatus(@Param("courseId") Long courseId, @Param("status") Integer status);
}
