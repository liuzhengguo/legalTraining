package com.legaltraining.web.controller.ops;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V3.0 事件调度（11.2 通知清单）
 *
 * NT-02 截止前提醒、NT-12 终端离线超时、NT-13 审核积压。三类任务共用模板分发器，
 * 由模板状态控制开关，并由分发器保证同一事件在去重窗口内不重复送达。
 */
@Component
public class V3EventScheduler
{
    private static final String SCAN_DELAY = "${legal-training.notice.scan-delay-ms:300000}";

    private final JdbcTemplate jdbc;
    private final NotificationDispatcher dispatcher;

    public V3EventScheduler(JdbcTemplate jdbc, NotificationDispatcher dispatcher)
    {
        this.jdbc = jdbc;
        this.dispatcher = dispatcher;
    }

    /** NT-02：截止前 24 小时提醒尚未提交的任务参与者 */
    @Scheduled(fixedDelayString = SCAN_DELAY, initialDelayString = SCAN_DELAY)
    public void deadlineReminders()
    {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "select t.task_id as taskId, t.title, t.deadline, c.course_name as courseName "
                + "from edu_task t join edu_course c on c.course_id=t.course_id "
                + "where t.del_flag='0' and t.status=1 and t.deadline>? and t.deadline<=?",
                Timestamp.valueOf(now), Timestamp.valueOf(now.plusHours(24)));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (Map<String, Object> task : tasks)
        {
            Long taskId = ((Number) task.get("taskId")).longValue();
            List<Long> receivers = jdbc.queryForList(
                    "select par.user_id from edu_task_participant par "
                    + "left join edu_submission s on s.task_id=par.task_id and s.user_id=par.user_id "
                    + "where par.task_id=? and (s.submission_id is null or s.status not in (2,3))",
                    Long.class, taskId);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("taskName", task.get("title"));
            vars.put("courseName", task.get("courseName"));
            vars.put("deadline", task.get("deadline") instanceof java.util.Date date
                    ? format.format(date) : String.valueOf(task.get("deadline")));
            for (Long receiver : receivers)
            {
                dispatcher.send("NT-02", receiver, vars, "task", taskId);
            }
        }
    }

    /** NT-12：设备离线超过 24 小时，提醒管理员与教务 */
    @Scheduled(fixedDelayString = SCAN_DELAY, initialDelayString = SCAN_DELAY)
    public void offlineDevices()
    {
        List<Map<String, Object>> devices = jdbc.queryForList(
                "select device_code as deviceCode, site_no as siteNo, offline_hours as hours "
                + "from exh_device where del_flag='0' and status=2 and offline_hours>=24");
        if (devices.isEmpty())
        {
            return;
        }
        List<Long> receivers = operators("admin", "registrar");
        for (Map<String, Object> device : devices)
        {
            for (Long receiver : receivers)
            {
                dispatcher.send("NT-12", receiver, device, "device", deviceId(device));
            }
        }
    }

    /** NT-13：待审成果积压，提醒审核员 */
    @Scheduled(fixedDelayString = SCAN_DELAY, initialDelayString = SCAN_DELAY)
    public void auditBacklog()
    {
        Integer pending = jdbc.queryForObject(
                "select count(1) from edu_recommendation where status=1", Integer.class);
        if (pending == null || pending == 0)
        {
            return;
        }
        List<Timestamp> oldest = jdbc.queryForList(
                "select create_time from edu_recommendation where status=1 order by create_time limit 1",
                Timestamp.class);
        long days = 0;
        if (!oldest.isEmpty() && oldest.get(0) != null)
        {
            days = Duration.between(oldest.get(0).toLocalDateTime(), LocalDateTime.now()).toDays();
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("count", pending);
        vars.put("days", days);
        for (Long receiver : operators("auditor"))
        {
            dispatcher.send("NT-13", receiver, vars, "rec", 0L);
        }
    }

    private List<Long> operators(String... roleKeys)
    {
        StringBuilder sql = new StringBuilder("select distinct u.user_id from sys_user u "
                + "join sys_user_role ur on ur.user_id=u.user_id "
                + "join sys_role r on r.role_id=ur.role_id where u.del_flag='0' and r.role_key in (");
        for (int i = 0; i < roleKeys.length; i++)
        {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');
        return jdbc.queryForList(sql.toString(), Long.class, (Object[]) roleKeys);
    }

    private static long deviceId(Map<String, Object> device)
    {
        Object code = device.get("deviceCode");
        return code == null ? 0L : Math.abs(code.toString().hashCode());
    }
}
