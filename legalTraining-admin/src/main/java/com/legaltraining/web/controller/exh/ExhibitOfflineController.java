package com.legaltraining.web.controller.exh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 紧急下架申请与审批（MG-05-02 / EM-03-04）。
 *
 * 两种路径：
 *   1. 常规：提交申请 → 待审批 → 同意后执行下架；
 *   2. 紧急（先下后批）：提交申请时立即下架，审批记录置为"先下后批"，审批驳回则恢复原发布状态。
 */
@RestController
public class ExhibitOfflineController extends BaseController
{
    private static final List<String> REASON_TYPES = List.of("内容错误", "版权投诉", "合规要求", "其他");
    private static final String FIELDS = "o.req_id as reqId,o.target_type as targetType,o.target_id as targetId,"
            + "o.reason_type as reasonType,o.reason,o.scope_desc as scopeDesc,o.status,"
            + "o.audit_opinion as auditOpinion,"
            + "date_format(o.apply_time,'%Y-%m-%d %H:%i:%s') as applyTime,"
            + "date_format(o.audit_time,'%Y-%m-%d %H:%i:%s') as auditTime,"
            + "o.apply_by as applyBy,u.nick_name as applyByName,o.auditor_id as auditorId,"
            + "case when o.target_type='item' then (select title from exh_item i where i.item_id=o.target_id) "
            + "else (select zone_name from exh_zone z where z.zone_id=o.target_id) end as targetName";
    private final JdbcTemplate jdbc;

    public ExhibitOfflineController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    public record ApplyForm(@NotBlank @Pattern(regexp = "item|zone") String targetType,
            @NotNull Long targetId,
            @NotNull @Min(0) @Max(3) Integer reasonType,
            @NotBlank @Size(max = 1000) String reason,
            @Size(max = 500) String scopeDesc,
            Boolean urgent) {}
    public record ApproveForm(boolean approve, @Size(max = 500) String opinion) {}

    @GetMapping("/exh/offline/list")
    @PreAuthorize("@ss.hasAnyPermi('exh:offline:audit,exh:publish:list')")
    public AjaxResult list(@RequestParam(required = false) Integer status)
    {
        String sql = "select " + FIELDS + " from exh_offline_request o left join sys_user u on u.user_id=o.apply_by"
                + (status == null ? "" : " where o.status=?") + " order by o.req_id desc";
        List<Map<String, Object>> rows = status == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, status);
        for (Map<String, Object> row : rows)
        {
            Object type = row.get("reasonType");
            row.put("reasonTypeName", type instanceof Number index ? REASON_TYPES.get(index.intValue()) : "");
        }
        return success(rows);
    }

    @PostMapping("/exh/offline/apply")
    @PreAuthorize("@ss.hasAnyPermi('exh:offline:audit,exh:publish:list')")
    @Transactional
    public AjaxResult apply(@Valid @RequestBody ApplyForm form)
    {
        Map<String, Object> target = target(form.targetType(), form.targetId());
        Integer pending = jdbc.queryForObject("select count(1) from exh_offline_request where target_type=? "
                + "and target_id=? and status in (0,3)", Integer.class, form.targetType(), form.targetId());
        if (pending != null && pending > 0) throw new ServiceException("该对象已有待处理的下架申请，请勿重复提交");
        boolean urgent = Boolean.TRUE.equals(form.urgent());
        int status = 0;
        jdbc.update("insert into exh_offline_request(target_type,target_id,apply_by,reason_type,reason,scope_desc,"
                + "status,apply_time) values(?,?,?,?,?,?,?,now())", form.targetType(), form.targetId(),
                getUserId(), form.reasonType(), form.reason(), form.scopeDesc(), status);
        Long reqId = jdbc.queryForObject("select max(req_id) from exh_offline_request where apply_by=?",
                Long.class, getUserId());
        if (urgent)
        {
            takeOffline(form.targetType(), form.targetId(), "紧急下架：" + form.reason());
            jdbc.update("update exh_offline_request set status=3 where req_id=?", reqId);
            status = 3;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reqId", reqId);
        result.put("status", status);
        result.put("statusName", status == 3 ? "先下后批" : "待审批");
        result.put("urgent", urgent);
        result.put("targetStatus", target.get("status"));
        return success(result);
    }

    @PostMapping("/exh/offline/{reqId}/approve")
    @PreAuthorize("@ss.hasPermi('exh:offline:audit')")
    @Transactional
    public AjaxResult approve(@PathVariable long reqId, @Valid @RequestBody ApproveForm form)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select target_type as targetType,target_id as targetId,"
                + "status,apply_by as applyBy from exh_offline_request where req_id=? for update", reqId);
        if (rows.isEmpty()) throw new ServiceException("下架申请不存在");
        Map<String, Object> req = rows.get(0);
        int status = ((Number) req.get("status")).intValue();
        if (status != 0 && status != 3) throw new ServiceException("该申请已处理");
        if (!form.approve() && (form.opinion() == null || form.opinion().isBlank()))
        {
            throw new ServiceException("驳回须填写意见");
        }
        String targetType = (String) req.get("targetType");
        long targetId = ((Number) req.get("targetId")).longValue();
        if (form.approve())
        {
            if (status == 0) takeOffline(targetType, targetId, "已批准下架申请 #" + reqId);
            jdbc.update("update exh_offline_request set status=1,auditor_id=?,audit_time=now(),audit_opinion=? "
                    + "where req_id=?", getUserId(), form.opinion(), reqId);
        }
        else
        {
            if (status == 3) restore(targetType, targetId);
            jdbc.update("update exh_offline_request set status=2,auditor_id=?,audit_time=now(),audit_opinion=? "
                    + "where req_id=?", getUserId(), form.opinion(), reqId);
        }
        jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time) "
                + "values('NT-10',?,?,?,'offline',?,now())", req.get("applyBy"),
                form.approve() ? "下架申请已通过" : "下架申请已驳回",
                form.opinion() == null ? "" : form.opinion(), reqId);
        return success(Map.of("reqId", reqId, "status", form.approve() ? 1 : 2));
    }

    private Map<String, Object> target(String targetType, long targetId)
    {
        String sql = "item".equals(targetType)
                ? "select status,title as name from exh_item where item_id=? and del_flag='0'"
                : "select status,zone_name as name from exh_zone where zone_id=? and del_flag='0'";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, targetId);
        if (rows.isEmpty()) throw new ServiceException("下架对象不存在");
        return rows.get(0);
    }

    private void takeOffline(String targetType, long targetId, String reason)
    {
        String text = reason.length() > 500 ? reason.substring(0, 500) : reason;
        if ("item".equals(targetType))
        {
            int changed = jdbc.update("update exh_item set status=3,offline_time=now(),offline_reason=?,update_time=now() "
                    + "where item_id=? and del_flag='0'", text, targetId);
            if (changed == 0) throw new ServiceException("展项不存在");
        }
        else
        {
            int changed = jdbc.update("update exh_zone set status=0,open_level=0,update_time=now() "
                    + "where zone_id=? and del_flag='0'", targetId);
            if (changed == 0) throw new ServiceException("展区不存在");
        }
    }

    private void restore(String targetType, long targetId)
    {
        if ("item".equals(targetType))
        {
            jdbc.update("update exh_item set status=2,offline_time=null,offline_reason=null,update_time=now() "
                    + "where item_id=? and status=3 and del_flag='0'", targetId);
        }
        else
        {
            jdbc.update("update exh_zone set status=1,open_level=2,update_time=now() where zone_id=? and status=0",
                    targetId);
        }
    }
}
