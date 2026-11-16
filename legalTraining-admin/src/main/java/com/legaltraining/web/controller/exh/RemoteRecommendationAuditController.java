package com.legaltraining.web.controller.exh;

import java.util.Set;
import java.io.IOException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.web.controller.ops.RemoteRecommendationInbox;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exh/audit/remote")
public class RemoteRecommendationAuditController extends BaseController
{
    private final RemoteRecommendationInbox inbox;

    public RemoteRecommendationAuditController(RemoteRecommendationInbox inbox)
    {
        this.inbox = inbox;
    }

    public record Decision(boolean approve, Set<String> checks, @Size(max = 1000) String opinion) {}
    public record Draft(@NotNull @Positive Long zoneId) {}

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermi('exh:audit:list,exh:item:list')")
    public AjaxResult list() { return success(inbox.list(SecurityUtils.hasPermi("exh:audit:list"))); }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('exh:audit:list')")
    public AjaxResult detail(@PathVariable long id) { return success(inbox.detail(id)); }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("@ss.hasPermi('exh:audit:list')")
    public AjaxResult evidence(@PathVariable long id) { return success(inbox.evidence(id)); }

    @GetMapping("/{id}/evidence/{token}")
    @PreAuthorize("@ss.hasPermi('exh:audit:list')")
    public ResponseEntity<StreamingResponseBody> evidenceFile(@PathVariable long id, @PathVariable String token)
            throws IOException, InterruptedException
    {
        return inbox.evidenceFile(id, token);
    }

    @PostMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('exh:audit:list')")
    public AjaxResult review(@PathVariable long id, @Valid @RequestBody Decision decision)
    {
        inbox.review(id, getUserId(), getUsername(), decision.approve(), decision.checks(), decision.opinion());
        return success();
    }

    @PostMapping("/{id}/exhibit")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    public AjaxResult exhibit(@PathVariable long id, @Valid @RequestBody Draft draft)
    {
        return success(java.util.Map.of("itemId", inbox.createDraft(id, draft.zoneId(), getUserId())));
    }
}
