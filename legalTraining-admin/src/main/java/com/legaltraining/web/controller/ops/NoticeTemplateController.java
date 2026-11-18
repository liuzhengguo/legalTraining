package com.legaltraining.web.controller.ops;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.exception.ServiceException;

/**
 * 通知模板运维（MG-06-04）：模板修改后由 NotificationDispatcher 立即生效。
 */
@RestController
@RequestMapping("/ops/notice-template")
public class NoticeTemplateController extends BaseController
{
    private final JdbcTemplate jdbc;
    private final NotificationDispatcher dispatcher;

    public NoticeTemplateController(JdbcTemplate jdbc, NotificationDispatcher dispatcher)
    {
        this.jdbc = jdbc;
        this.dispatcher = dispatcher;
    }

    @PreAuthorize("@ss.hasPermi('ops:notice:list')")
    @GetMapping("/list")
    public AjaxResult list()
    {
        return success(jdbc.queryForList("select template_id as templateId, event_code as eventCode, "
                + "event_name as eventName, title_tpl as titleTpl, content_tpl as contentTpl, "
                + "channels, closable, status from ops_notice_template order by event_code"));
    }

    @PreAuthorize("@ss.hasPermi('ops:notice:list')")
    @GetMapping("/{eventCode}")
    public AjaxResult detail(@PathVariable String eventCode)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from ops_notice_template where event_code=?", eventCode);
        if (rows.isEmpty())
        {
            throw new ServiceException("通知模板不存在");
        }
        return success(rows.get(0));
    }

    @Log(title = "通知模板", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('ops:notice:add')")
    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> body)
    {
        String eventCode = String.valueOf(body.get("eventCode"));
        if (eventCode == null || eventCode.isBlank())
        {
            throw new ServiceException("事件编码不能为空");
        }
        Integer exists = jdbc.queryForObject(
                "select count(1) from ops_notice_template where event_code=?", Integer.class, eventCode);
        if (exists != null && exists > 0)
        {
            throw new ServiceException("该事件编码的模板已存在");
        }
        jdbc.update("insert into ops_notice_template(event_code,event_name,title_tpl,content_tpl,channels,closable,status) "
                        + "values(?,?,?,?,?,?,?)",
                eventCode, text(body.get("eventName")), text(body.get("titleTpl")), text(body.get("contentTpl")),
                textOrDefault(body.get("channels"), "inner"), textOrDefault(body.get("closable"), "1"),
                textOrDefault(body.get("status"), "0"));
        return success();
    }

    @Log(title = "通知模板", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('ops:notice:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody Map<String, Object> body)
    {
        String eventCode = String.valueOf(body.get("eventCode"));
        if (eventCode == null || eventCode.isBlank())
        {
            throw new ServiceException("事件编码不能为空");
        }
        int changed = jdbc.update("update ops_notice_template set event_name=?,title_tpl=?,content_tpl=?,"
                        + "channels=?,closable=?,status=? where event_code=?",
                text(body.get("eventName")), text(body.get("titleTpl")), text(body.get("contentTpl")),
                textOrDefault(body.get("channels"), "inner"), textOrDefault(body.get("closable"), "1"),
                textOrDefault(body.get("status"), "0"), eventCode);
        return toAjax(changed);
    }

    @Log(title = "通知模板", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('ops:notice:remove')")
    @DeleteMapping("/{eventCode}")
    public AjaxResult remove(@PathVariable String eventCode)
    {
        return toAjax(jdbc.update("delete from ops_notice_template where event_code=?", eventCode));
    }

    /** 用当前账号验证模板渲染与去重是否生效 */
    @PreAuthorize("@ss.hasPermi('ops:notice:edit')")
    @PostMapping("/{eventCode}/test")
    public AjaxResult test(@PathVariable String eventCode)
    {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("taskName", "示例任务");
        sample.put("courseName", "示例课程");
        sample.put("deadline", "2026-01-01 12:00");
        sample.put("deviceCode", "DEMO-01");
        sample.put("siteNo", "示例现场");
        sample.put("hours", 25);
        sample.put("count", 3);
        sample.put("days", 2);
        boolean enabled = dispatcher.isEnabled(eventCode);
        int sent = dispatcher.send(eventCode, getUserId(), sample, "notice-test", null);
        return success(Map.of("enabled", enabled, "sent", sent,
                "message", enabled ? (sent > 0 ? "测试通知已发送" : "模板启用，但处于去重窗口内") : "模板已停用，未发送"));
    }

    private static String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static String textOrDefault(Object value, String fallback)
    {
        String text = text(value);
        return text == null || text.isBlank() ? fallback : text;
    }
}
