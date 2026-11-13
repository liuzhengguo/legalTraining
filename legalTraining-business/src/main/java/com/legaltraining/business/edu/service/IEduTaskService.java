package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduTask;

/**
 * 实训任务 服务层（TC-03-01~06）
 */
public interface IEduTaskService
{
    EduTask selectEduTaskById(Long taskId);

    List<EduTask> selectEduTaskList(EduTask eduTask);

    /** 新建（草稿）：仅落库，不生成待办 */
    int insertEduTask(EduTask eduTask);

    /** 编辑：仅草稿/已截止状态可改；进行中只允许改截止时间与补充说明（走专用接口） */
    int updateEduTask(EduTask eduTask);

    int deleteEduTaskById(Long taskId);

    /** 权重校验（TC-03-01）：返回合计、是否合规、折算示例 */
    Map<String, Object> weightCheck(EduTask eduTask);

    /** 发布（TC-03-02）：校验时间与权重，按班级快照生成参与人 */
    Map<String, Object> publish(Long taskId);

    /** 延期（TC-03-06） */
    int changeDeadline(Long taskId, java.util.Date deadline, String reason);

    /** 补充说明（TC-03-06）：追加到 requirement 末尾并保留时间戳 */
    int appendNotice(Long taskId, String notice);

    /** 撤回（TC-03-04）：已进行中 → 已取消；有提交时必须填原因 */
    Map<String, Object> recall(Long taskId, String reason);

    /** 归档（状态机 6.1）：已截止 → 已归档 */
    int archive(Long taskId);

    /** 学生视角预览（TC-03-05）：不暴露内部字段，只返回学生可见内容 */
    Map<String, Object> preview(Long taskId);

    /** 自动截止扫描：把过期的进行中任务转为已截止 */
    int scanExpired();

    /** 参与人名单 */
    List<Map<String, Object>> participants(Long taskId);
}
