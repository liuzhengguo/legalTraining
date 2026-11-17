package com.legaltraining.web.controller.openapi;

import java.util.UUID;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/local-cases")
@PreAuthorize("@ss.hasPermi('edu:case:manage')")
public class LocalCaseController extends BaseController
{
    private final JdbcTemplate db;
    private final CaseLibrary library;

    public LocalCaseController(JdbcTemplate db, CaseLibrary library)
    {
        this.db = db;
        this.library = library;
    }

    @GetMapping
    public AjaxResult list(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int pageNum)
    {
        return success(library.localCases(keyword, pageNum, 20, true));
    }

    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable String id)
    {
        return success(library.localDetail(id));
    }

    @PostMapping
    public AjaxResult create(@RequestBody CaseForm form)
    {
        String id = "local_" + UUID.randomUUID().toString().replace("-", "");
        db.update("insert into ops_local_case(case_id,title,summary,source,content,created_by) values(?,?,?,?,?,?)",
                id, field(form.title(), 250, true), field(form.summary(), 1000, false),
                field(form.source(), 250, false), field(form.content(), 50000, true), getUserId());
        return success(library.localDetail(id));
    }

    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable String id, @RequestBody CaseForm form)
    {
        requireEditable(id);
        db.update("update ops_local_case set title=?,summary=?,source=?,content=? where case_id=?",
                field(form.title(), 250, true), field(form.summary(), 1000, false),
                field(form.source(), 250, false), field(form.content(), 50000, true), id);
        return success();
    }

    @PutMapping("/{id}/status")
    public AjaxResult status(@PathVariable String id, @RequestBody CaseStatus form)
    {
        requireEditable(id);
        db.update("update ops_local_case set active=? where case_id=?", form.active(), id);
        return success();
    }

    private void requireEditable(String id)
    {
        var entry = library.localDetail(id);
        if (!SecurityUtils.isAdmin(getUserId()) && !SecurityUtils.hasRole("registrar")
                && ((Number) entry.get("createdBy")).longValue() != getUserId())
        {
            throw new ServiceException("只有案例创建人和管理员可以维护此案例");
        }
    }

    private String field(String value, int max, boolean required)
    {
        String text = value == null ? "" : value.strip();
        if ((required && text.isEmpty()) || text.codePointCount(0, text.length()) > max)
        {
            throw new ServiceException("请填写必填内容，并检查输入长度");
        }
        return text;
    }

    public record CaseForm(String title, String summary, String source, String content) {}
    public record CaseStatus(boolean active) {}
}
