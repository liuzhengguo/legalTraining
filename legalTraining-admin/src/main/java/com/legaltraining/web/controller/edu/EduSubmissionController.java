package com.legaltraining.web.controller.edu;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import com.alibaba.fastjson2.JSON;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduSubmission;
import com.legaltraining.business.edu.domain.EduSubmissionFile;
import com.legaltraining.business.edu.service.IEduSubmissionService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.enums.BusinessType;

/**
 * 作业提交（ST-02-01~05）
 *
 * 学生端（H5）与教师端共用；幂等由唯一键 (task_id, user_id) 保证。
 */
@RestController
@RequestMapping("/edu/submission")
public class EduSubmissionController extends BaseController
{
    @Autowired
    private IEduSubmissionService eduSubmissionService;

    @Autowired
    private com.legaltraining.business.edu.service.impl.EduTaskTeamService teams;

    public record TeamText(@jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 64) String text) {}
    public record Contribution(@jakarta.validation.constraints.Size(max = 500) String text) {}

    @PreAuthorize("@ss.hasPermi('edu:review:list')")
    @GetMapping("/team/{taskId}/settings")
    public AjaxResult teamSettings(@PathVariable long taskId)
    {
        eduSubmissionService.requireReviewAccess(taskId);
        return success(Map.of("anyMemberSubmit", teams.anyMemberSubmit(taskId)));
    }

    @PreAuthorize("@ss.hasPermi('edu:task:edit')")
    @PutMapping("/team/{taskId}/settings")
    public AjaxResult changeTeamSettings(@PathVariable long taskId, @RequestParam boolean anyMemberSubmit)
    {
        eduSubmissionService.requireReviewAccess(taskId);
        teams.setAnyMemberSubmit(taskId, anyMemberSubmit);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('edu:submission:my')")
    @GetMapping("/team/{taskId}/editors")
    public AjaxResult editors(@PathVariable long taskId)
    {
        return success(teams.editors(taskId));
    }

    @PreAuthorize("@ss.hasPermi('edu:submission:edit')")
    @PostMapping("/team/{taskId}/editing")
    public AjaxResult editing(@PathVariable long taskId, @RequestParam boolean editing)
    {
        return success(teams.editing(taskId, editing));
    }

    @Log(title = "旧版分组作业迁移", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:task:edit')")
    @PutMapping("/team/{taskId}/legacy/{submissionId}")
    public AjaxResult migrateLegacy(@PathVariable long taskId, @PathVariable long submissionId)
    {
        eduSubmissionService.requireReviewAccess(taskId);
        teams.migrateLegacy(taskId, submissionId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('edu:submission:my')")
    @GetMapping("/team/{taskId}")
    public AjaxResult team(@PathVariable long taskId) { return success(teams.overview(taskId)); }

    @PreAuthorize("@ss.hasPermi('edu:submission:edit')")
    @PostMapping("/team/{taskId}")
    public AjaxResult createTeam(@PathVariable long taskId, @jakarta.validation.Valid @RequestBody TeamText body)
    {
        return success(teams.create(taskId, body.text()));
    }

    @PreAuthorize("@ss.hasPermi('edu:submission:edit')")
    @PostMapping("/team/{taskId}/invite")
    public AjaxResult invite(@PathVariable long taskId, @jakarta.validation.Valid @RequestBody TeamText body)
    {
        return success(teams.invite(taskId, body.text()));
    }

    @PreAuthorize("@ss.hasPermi('edu:submission:edit')")
    @PostMapping("/team/{taskId}/invite/{groupId}")
    public AjaxResult respond(@PathVariable long taskId, @PathVariable long groupId, @RequestParam boolean accept)
    {
        return success(teams.respond(taskId, groupId, accept));
    }

    @PreAuthorize("@ss.hasPermi('edu:submission:edit')")
    @PostMapping("/team/{taskId}/confirm")
    public AjaxResult confirm(@PathVariable long taskId) { return success(teams.confirm(taskId)); }

    @PreAuthorize("@ss.hasPermi('edu:submission:edit')")
    @PutMapping("/team/{taskId}/contribution")
    public AjaxResult contribute(@PathVariable long taskId, @jakarta.validation.Valid @RequestBody Contribution body)
    {
        teams.contribution(taskId, body.text());
        return success();
    }

    /**
     * 我的提交（ST-02-01 / ST-03-02）：未提交时也返回骨架，前端据 submittable 置灰按钮
     */
    @PreAuthorize("@ss.hasPermi('edu:submission:my')")
    @GetMapping("/my/{taskId}")
    public AjaxResult my(@PathVariable Long taskId)
    {
        return success(eduSubmissionService.my(taskId));
    }

    @PreAuthorize("@ss.hasPermi('edu:submission:my')")
    @GetMapping("/my/{taskId}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long taskId)
    {
        var receipt = eduSubmissionService.receipt(taskId);
        String filename = "receipt-" + receipt.get("submissionId") + "-v" + receipt.get("version") + ".json";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(JSON.toJSONString(receipt, com.alibaba.fastjson2.JSONWriter.Feature.WriteNulls)
                        .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 教师侧提交列表（批阅待办，支持按任务/状态/学号筛选）
     */
    @PreAuthorize("@ss.hasPermi('edu:review:list')")
    @GetMapping("/list")
    public AjaxResult list(EduSubmission query)
    {
        return success(eduSubmissionService.selectSubmissionList(query));
    }

    @PreAuthorize("@ss.hasPermi('edu:review:query')")
    @GetMapping("/{submissionId}")
    public AjaxResult getInfo(@PathVariable Long submissionId)
    {
        EduSubmission submission = eduSubmissionService.selectById(submissionId);
        if (submission == null)
        {
            return error("提交记录不存在");
        }
        eduSubmissionService.requireReviewAccess(submission.getTaskId());
        AjaxResult result = success(submission);
        if (Integer.valueOf(1).equals(submission.getSubjectType()))
        {
            result.put("teamMembers", teams.members(submission.getTaskId(), submission.getGroupId()));
        }
        return result;
    }

    /**
     * 草稿保存（ST-02-02）：不生成版本、不进待批列表
     */
    @Log(title = "作业草稿", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:submission:edit')")
    @PostMapping("/draft")
    public AjaxResult draft(@RequestBody Map<String, Object> body)
    {
        Long taskId = toLong(body.get("taskId"));
        String content = (String) body.get("content");
        return success(eduSubmissionService.saveDraft(taskId, content, parseFiles(body.get("files"))));
    }

    /**
     * 提交（ST-02-03，幂等）
     */
    @Log(title = "作业提交", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:submission:submit')")
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody Map<String, Object> body)
    {
        Long taskId = toLong(body.get("taskId"));
        String content = (String) body.get("content");
        return success(eduSubmissionService.submit(taskId, content, parseFiles(body.get("files"))));
    }

    /**
     * 撤回（ST-02-03）：仅截止前可撤回
     */
    @Log(title = "作业撤回", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:submission:submit')")
    @PostMapping("/{submissionId}/withdraw")
    public AjaxResult withdraw(@PathVariable Long submissionId)
    {
        return success(eduSubmissionService.withdraw(submissionId));
    }

    /**
     * 逾期补交申请（ST-02-05）
     */
    @Log(title = "补交申请", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:submission:apply')")
    @PostMapping("/{submissionId}/late-apply")
    public AjaxResult lateApply(@PathVariable Long submissionId, @RequestParam String reason)
    {
        return success(eduSubmissionService.lateApply(submissionId, reason));
    }

    @Log(title = "补交申请", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:submission:apply')")
    @PostMapping("/task/{taskId}/late-apply")
    public AjaxResult lateApplyForTask(@PathVariable Long taskId, @RequestParam String reason)
    {
        return success(eduSubmissionService.lateApplyForTask(taskId, reason));
    }

    /**
     * 教师审批补交（ST-02-05）
     * approve=true 时 deadline 为补交截止时间，不传则默认 3 天后。
     */
    @Log(title = "补交审批", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:review:audit')")
    @PutMapping("/{submissionId}/late-approve")
    public AjaxResult lateApprove(@PathVariable Long submissionId,
            @RequestParam boolean approve,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    pattern = "yyyy-MM-dd HH:mm:ss") Date deadline,
            @RequestParam(required = false) String remark)
    {
        return success(eduSubmissionService.lateApprove(submissionId, approve, deadline, remark));
    }

    /**
     * 历史版本（TC-04-02）
     */
    @PreAuthorize("@ss.hasPermi('edu:review:list')")
    @GetMapping("/{submissionId}/versions")
    public AjaxResult versions(@PathVariable Long submissionId)
    {
        return success(eduSubmissionService.versions(submissionId));
    }

    /**
     * 指定版本附件（教师逐版对照）
     */
    @PreAuthorize("@ss.hasPermi('edu:review:query')")
    @GetMapping("/{submissionId}/files")
    public AjaxResult files(@PathVariable Long submissionId, @RequestParam(required = false) Integer version)
    {
        return success(eduSubmissionService.files(submissionId, version));
    }

    /**
     * 批阅附件受控读取（F01）
     * 学生提交附件落在受控目录，禁止静态直链。教师须有该任务的批阅权限，
     * 且 fileId 必须属于该 submission 的某个版本，才允许读取内容。
     */
    @PreAuthorize("@ss.hasPermi('edu:review:query')")
    @GetMapping("/{submissionId}/files/{fileId}/content")
    public ResponseEntity<FileSystemResource> attachmentContent(@PathVariable Long submissionId,
            @PathVariable String fileId, @RequestParam(defaultValue = "false") boolean download) throws IOException
    {
        EduSubmission submission = eduSubmissionService.selectById(submissionId);
        if (submission == null)
        {
            return ResponseEntity.notFound().build();
        }
        eduSubmissionService.requireReviewAccess(submission.getTaskId());
        // 仅允许读取确实登记在该提交任一版本下的附件，防止越权读取任意受控文件
        EduSubmissionFile matched = null;
        int version = submission.getVersion() == null ? 0 : submission.getVersion();
        for (int v = 1; v <= version; v++)
        {
            for (EduSubmissionFile f : eduSubmissionService.files(submissionId, v))
            {
                if (fileId.equals(f.getFileId()))
                {
                    matched = f;
                    break;
                }
            }
            if (matched != null) break;
        }
        if (matched == null)
        {
            return ResponseEntity.notFound().build();
        }
        return StudentLearningController.serveFile(matched.getFileId(), matched.getFileName(),
                matched.getFileExt(), download);
    }

    // ---------- 参数转换 ----------

    private Long toLong(Object v)
    {
        if (v == null)
        {
            return null;
        }
        if (v instanceof Number)
        {
            return ((Number) v).longValue();
        }
        return Long.valueOf(v.toString());
    }

    @SuppressWarnings("unchecked")
    private List<EduSubmissionFile> parseFiles(Object raw)
    {
        if (!(raw instanceof List))
        {
            return null;
        }
        List<EduSubmissionFile> files = new java.util.ArrayList<>();
        for (Object it : (List<Object>) raw)
        {
            if (!(it instanceof Map))
            {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) it;
            EduSubmissionFile f = new EduSubmissionFile();
            f.setFileId((String) m.get("fileId"));
            f.setFileName((String) m.get("fileName"));
            Object size = m.get("fileSize");
            if (size instanceof Number)
            {
                f.setFileSize(((Number) size).longValue());
            }
            f.setFileExt((String) m.get("fileExt"));
            f.setMimeType((String) m.get("mimeType"));
            files.add(f);
        }
        return files;
    }
}
