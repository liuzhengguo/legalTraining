package com.legaltraining.web.controller.edu;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduTask;
import com.legaltraining.business.edu.service.IEduTaskService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;

/**
 * 实训任务（TC-03-01~06，状态机 6.1）
 */
@RestController
@RequestMapping("/edu/task")
public class EduTaskController extends BaseController
{
    @Autowired
    private IEduTaskService eduTaskService;

    @PreAuthorize("@ss.hasPermi('edu:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduTask eduTask)
    {
        startPage();
        List<EduTask> list = eduTaskService.selectEduTaskList(eduTask);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('edu:task:query')")
    @GetMapping("/{taskId}")
    public AjaxResult getInfo(@PathVariable Long taskId)
    {
        return success(eduTaskService.selectEduTaskById(taskId));
    }

    @Log(title = "实训任务", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:task:add')")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody EduTask eduTask)
    {
        return toAjax(eduTaskService.insertEduTask(eduTask));
    }

    /**
     * 编辑（部分字段更新，必填校验在服务层按最终值判断）
     */
    @Log(title = "实训任务", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody EduTask eduTask)
    {
        return toAjax(eduTaskService.updateEduTask(eduTask));
    }

    @Log(title = "实训任务", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:task:remove')")
    @DeleteMapping("/{taskId}")
    public AjaxResult remove(@PathVariable Long taskId)
    {
        return toAjax(eduTaskService.deleteEduTaskById(taskId));
    }

    /**
     * 权重校验（TC-03-01）：返回合计与折算示例，前端在评分项编辑区实时提示
     */
    @PreAuthorize("@ss.hasPermi('edu:task:query')")
    @PostMapping("/weight-check")
    public AjaxResult weightCheck(@RequestBody EduTask eduTask)
    {
        return success(eduTaskService.weightCheck(eduTask));
    }

    /**
     * 发布（TC-03-02）
     */
    @Log(title = "任务发布", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:publish')")
    @PutMapping("/{taskId}/publish")
    public AjaxResult publish(@PathVariable Long taskId)
    {
        return success(eduTaskService.publish(taskId));
    }

    /**
     * 延期（TC-03-06）
     */
    @Log(title = "任务延期", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:edit')")
    @PutMapping("/{taskId}/deadline")
    public AjaxResult changeDeadline(@PathVariable Long taskId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    pattern = "yyyy-MM-dd HH:mm:ss") Date deadline,
            @RequestParam(required = false) String reason)
    {
        return toAjax(eduTaskService.changeDeadline(taskId, deadline, reason));
    }

    /**
     * 补充说明（TC-03-06）
     */
    @Log(title = "任务补充说明", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:edit')")
    @PutMapping("/{taskId}/notice")
    public AjaxResult appendNotice(@PathVariable Long taskId, @RequestParam String notice)
    {
        return toAjax(eduTaskService.appendNotice(taskId, notice));
    }

    /**
     * 撤回（TC-03-04）：有提交时必须填原因
     */
    @Log(title = "任务撤回", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:recall')")
    @PutMapping("/{taskId}/recall")
    public AjaxResult recall(@PathVariable Long taskId, @RequestParam(required = false) String reason)
    {
        return success(eduTaskService.recall(taskId, reason));
    }

    /**
     * 归档：已截止 → 已归档
     */
    @Log(title = "任务归档", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:edit')")
    @PutMapping("/{taskId}/archive")
    public AjaxResult archive(@PathVariable Long taskId)
    {
        return toAjax(eduTaskService.archive(taskId));
    }

    /**
     * 学生视角预览（TC-03-05）
     */
    @PreAuthorize("@ss.hasPermi('edu:task:query')")
    @GetMapping("/{taskId}/preview")
    public AjaxResult preview(@PathVariable Long taskId)
    {
        return success(eduTaskService.preview(taskId));
    }

    /**
     * 参与人名单（含提交状态），批阅页左侧列表也走这里
     */
    @PreAuthorize("@ss.hasPermi('edu:task:query')")
    @GetMapping("/{taskId}/participants")
    public AjaxResult participants(@PathVariable Long taskId)
    {
        return success(eduTaskService.participants(taskId));
    }

    /**
     * 自动截止扫描：供定时任务或运维手动触发
     */
    @Log(title = "任务自动截止", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:edit')")
    @PutMapping("/scan-expired")
    public AjaxResult scanExpired()
    {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("expired", eduTaskService.scanExpired());
        return success(result);
    }
}
