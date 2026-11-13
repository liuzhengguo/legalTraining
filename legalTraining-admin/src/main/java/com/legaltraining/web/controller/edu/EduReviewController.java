package com.legaltraining.web.controller.edu;

import java.util.Date;
import java.util.List;
import java.util.Map;
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
import com.legaltraining.business.edu.domain.EduReview;
import com.legaltraining.business.edu.service.IEduReviewService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.enums.BusinessType;

/**
 * 批阅（TC-04-01~06）
 */
@RestController
@RequestMapping("/edu/review")
public class EduReviewController extends BaseController
{
    @Autowired
    private IEduReviewService eduReviewService;

    /**
     * 待批列表（TC-04-01）：可按 taskId / courseId / 学号 / 姓名筛选
     */
    @PreAuthorize("@ss.hasPermi('edu:review:list')")
    @GetMapping("/pending")
    public AjaxResult pending(EduReview query)
    {
        return success(eduReviewService.selectPendingList(query));
    }

    /**
     * 已批阅列表
     */
    @PreAuthorize("@ss.hasPermi('edu:review:list')")
    @GetMapping("/list")
    public AjaxResult list(EduReview query)
    {
        return success(eduReviewService.selectReviewList(query));
    }

    @PreAuthorize("@ss.hasPermi('edu:review:query')")
    @GetMapping("/{reviewId}")
    public AjaxResult getInfo(@PathVariable Long reviewId)
    {
        return success(eduReviewService.selectById(reviewId));
    }

    /**
     * 保存批阅（TC-04-03）
     * status=0 存草稿，status=1 直接发布；多维度加权时须传 scores 逐项得分。
     */
    @Log(title = "批阅", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:review:add')")
    @PostMapping
    public AjaxResult save(@RequestBody EduReview review)
    {
        return success(eduReviewService.save(review));
    }

    /**
     * 发布批阅草稿
     */
    @Log(title = "批阅发布", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:review:add')")
    @PostMapping("/{reviewId}/publish")
    public AjaxResult publish(@PathVariable Long reviewId)
    {
        return success(eduReviewService.publish(reviewId));
    }

    /**
     * 退回（TC-04-04）：原因 ≥20 字，必须给重交截止
     */
    @Log(title = "作业退回", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:review:return')")
    @PostMapping("/{submissionId}/return")
    public AjaxResult returnBack(@PathVariable Long submissionId,
            @RequestParam String reason,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    pattern = "yyyy-MM-dd HH:mm:ss") Date deadline)
    {
        return success(eduReviewService.returnBack(submissionId, reason, deadline));
    }

    /**
     * 批量批阅（TC-04-05）：[{submissionId, totalScore, reviewComment}]
     */
    @Log(title = "批量批阅", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:review:batch')")
    @PostMapping("/batch")
    public AjaxResult batch(@RequestBody List<Map<String, Object>> items)
    {
        return success(eduReviewService.batch(items));
    }

    /**
     * 批阅进度（MG-03-02）
     */
    @PreAuthorize("@ss.hasPermi('edu:review:list')")
    @GetMapping("/progress")
    public AjaxResult progress(@RequestParam(required = false) Long courseId)
    {
        return success(eduReviewService.progress(courseId));
    }
}
