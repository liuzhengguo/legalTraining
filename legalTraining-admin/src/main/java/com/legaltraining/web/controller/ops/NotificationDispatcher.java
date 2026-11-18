package com.legaltraining.web.controller.ops;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 站内通知统一出口（11.2 / MG-06-04）
 *
 * 从 ops_notice_template 读取标题与正文模板并渲染占位符；同一事件、接收人、业务对象
 * 在去重窗口内只发送一次，避免调度重跑或重复触发造成骚扰。
 */
@Component
public class NotificationDispatcher
{
    /** 同一事件 + 接收人 + 业务对象的去重窗口（分钟） */
    private static final int DEDUPE_MINUTES = 10;

    private final JdbcTemplate jdbc;

    public NotificationDispatcher(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    /** 模板是否启用 */
    public boolean isEnabled(String eventCode)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select status from ops_notice_template where event_code=?", eventCode);
        return !rows.isEmpty() && "0".equals(String.valueOf(rows.get(0).get("status")));
    }

    /**
     * 发送一条模板通知；模板停用或命中去重窗口时返回 0。
     */
    public int send(String eventCode, Long receiverId, Map<String, Object> variables,
            String bizType, Long bizId)
    {
        if (receiverId == null || !isEnabled(eventCode))
        {
            return 0;
        }
        List<Map<String, Object>> templates = jdbc.queryForList(
                "select title_tpl, content_tpl from ops_notice_template where event_code=?", eventCode);
        if (templates.isEmpty())
        {
            return 0;
        }
        String title = render(String.valueOf(templates.get(0).get("title_tpl")), variables);
        String content = render(String.valueOf(templates.get(0).get("content_tpl")), variables);
        Long dedupeKey = bizId == null ? 0L : bizId;
        Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusMinutes(DEDUPE_MINUTES));
        Integer sent = jdbc.queryForObject(
                "select count(1) from ops_notification where event_code=? and receiver_id=? "
                + "and biz_id=? and send_time>?", Integer.class, eventCode, receiverId, dedupeKey, since);
        if (sent != null && sent > 0)
        {
            return 0;
        }
        return jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,"
                + "channel,is_read,send_time) values(?,?,?,?,?,?,'inner','0',now())",
                eventCode, receiverId, title, content, bizType, dedupeKey);
    }

    private static String render(String template, Map<String, Object> variables)
    {
        if (template == null)
        {
            return "";
        }
        String result = template;
        if (variables != null)
        {
            for (Map.Entry<String, Object> entry : variables.entrySet())
            {
                result = result.replace("{" + entry.getKey() + "}",
                        entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
