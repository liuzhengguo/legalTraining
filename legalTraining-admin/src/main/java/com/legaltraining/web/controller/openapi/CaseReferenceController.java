package com.legaltraining.web.controller.openapi;

import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.scope.BusinessScopeChecker;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/case-reference/{type}/{id}")
public class CaseReferenceController extends BaseController
{
    private final JdbcTemplate db;
    private final CaseLibrary library;
    private final BusinessScopeChecker scope;
    private final TransactionTemplate transactions;

    public CaseReferenceController(JdbcTemplate db, CaseLibrary library,
            BusinessScopeChecker scope, TransactionTemplate transactions)
    {
        this.db = db;
        this.library = library;
        this.scope = scope;
        this.transactions = transactions;
    }

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermi('edu:course:edit,edu:task:edit,exh:item:list')")
    public AjaxResult list(@PathVariable String type, @PathVariable long id)
    {
        requireTarget(type, id, false);
        return success(library.references(type, id));
    }

    @GetMapping("/search")
    @PreAuthorize("@ss.hasAnyPermi('edu:course:edit,edu:task:edit,exh:item:list')")
    public AjaxResult search(@PathVariable String type, @PathVariable long id,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int pageNum)
    {
        requireTarget(type, id, false);
        if (keyword.length() > 200) return error("案例检索关键词过长");
        return success(library.cases(keyword, pageNum, 10));
    }

    @GetMapping("/detail/{caseId}")
    @PreAuthorize("@ss.hasAnyPermi('edu:course:edit,edu:task:edit,exh:item:list')")
    public AjaxResult detail(@PathVariable String type, @PathVariable long id, @PathVariable String caseId)
    {
        requireTarget(type, id, false);
        return success(library.caseDetail(caseId));
    }

    @PostMapping("/{caseId}")
    @PreAuthorize("@ss.hasAnyPermi('edu:course:edit,edu:task:edit,exh:item:list')")
    public AjaxResult attach(@PathVariable String type, @PathVariable long id, @PathVariable String caseId)
    {
        requireTarget(type, id, false);
        var found = library.caseDetail(caseId);
        if (!(found.get("case") instanceof Map<?, ?> detail) || !(detail.get("title") instanceof String title)
                || title.isBlank() || title.codePointCount(0, title.length()) > 250)
            return error("无法获取有效的案例详情");
        transactions.executeWithoutResult(status -> {
            requireTarget(type, id, true);
            boolean newReference = db.queryForList("""
                    select case_id from ops_case_reference where target_type=? and target_id=? and case_id=?
                    """, String.class, type, id, caseId).isEmpty();
            if (!newReference) return;
            if (db.queryForObject("select count(*) from ops_case_reference where target_type=? and target_id=?",
                    Integer.class, type, id) >= 30) throw new ServiceException("每项内容最多关联 30 个案例");
            db.update("insert into ops_case_reference(target_type,target_id,case_id,title) values(?,?,?,?)",
                    type, id, caseId, title);
        });
        return success();
    }

    @DeleteMapping("/{caseId}")
    @PreAuthorize("@ss.hasAnyPermi('edu:course:edit,edu:task:edit,exh:item:list')")
    public AjaxResult remove(@PathVariable String type, @PathVariable long id, @PathVariable String caseId)
    {
        transactions.executeWithoutResult(status -> {
            requireTarget(type, id, true);
            db.update("delete from ops_case_reference where target_type=? and target_id=? and case_id=?",
                    type, id, caseId);
        });
        return success();
    }

    private void requireTarget(String type, long id, boolean lock)
    {
        String sql = switch (type) {
            case "course" -> "select course_id from edu_course where course_id=? and del_flag='0' and status<>2";
            case "task" -> "select course_id from edu_task where task_id=? and del_flag='0'";
            case "item" -> "select item_id from exh_item where item_id=? and del_flag='0'";
            default -> throw new ServiceException("当前内容类型不支持关联案例");
        };
        String permission = switch (type) {
            case "course" -> "edu:course:edit";
            case "task" -> "edu:task:edit";
            default -> "exh:item:list";
        };
        if (!SecurityUtils.hasPermi(permission)) throw new ServiceException("无权管理此类案例引用");
        List<Long> owners = db.queryForList(sql + (lock ? " for update" : ""), Long.class, id);
        if (owners.isEmpty()) throw new ServiceException("内容不存在或已归档");
        if (!type.equals("item")) scope.requireCourseAccess(owners.get(0));
    }
}
