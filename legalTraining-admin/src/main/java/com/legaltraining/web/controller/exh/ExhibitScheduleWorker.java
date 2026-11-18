package com.legaltraining.web.controller.exh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.web.controller.ops.NotificationDispatcher;

@Component
public class ExhibitScheduleWorker
{
    private static final Logger log = LoggerFactory.getLogger(ExhibitScheduleWorker.class);
    private final JdbcTemplate jdbc;
    private final ExhibitController exhibits;
    private final TransactionTemplate transactions;
    private final NotificationDispatcher dispatcher;

    public ExhibitScheduleWorker(JdbcTemplate jdbc, ExhibitController exhibits,
            TransactionTemplate transactions, NotificationDispatcher dispatcher)
    {
        this.jdbc = jdbc;
        this.exhibits = exhibits;
        this.transactions = transactions;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${legaltraining.exhibit.schedule-delay-ms:15000}")
    public void runDueJobs()
    {
        List<Long> ids = jdbc.queryForList("select item_id from exh_publish_job "
                + "where status=0 and plan_time<=utc_timestamp() order by plan_time,job_id limit 50", Long.class);
        for (Long id : ids)
        {
            try
            {
                exhibits.executeSchedule(id);
            }
            catch (RuntimeException failure)
            {
                recordFailure(id, failure);
            }
        }
    }

    private void recordFailure(long itemId, RuntimeException failure)
    {
        String reason = failure instanceof ServiceException ? failure.getMessage() : "定时发布执行失败，请查看服务器日志";
        transactions.executeWithoutResult(transaction -> {
            int changed = jdbc.update("update exh_publish_job set "
                    + "plan_time=case when retry_count>=3 then plan_time "
                    + "else timestampadd(minute,case retry_count when 0 then 1 when 1 then 5 else 15 end,utc_timestamp()) end, "
                    + "status=case when retry_count>=3 then 2 else 0 end, "
                    + "execute_time=case when retry_count>=3 then now() else null end, "
                    + "retry_count=least(retry_count+1,3),fail_reason=? where item_id=? and status=0 and plan_time<=utc_timestamp()",
                    reason, itemId);
            if (changed == 0) return;
            log.warn("定时发布展项 {} 失败", itemId, failure);
            notifyFinalFailure(itemId, reason);
        });
    }

    /** 终次失败按模板通知操作人、管理员与教务，便于运营复验 */
    private void notifyFinalFailure(long itemId, String reason)
    {
        List<Long> receivers = new java.util.ArrayList<>(jdbc.queryForList(
                "select distinct operator_id from exh_publish_job where item_id=? and operator_id is not null",
                Long.class, itemId));
        receivers.add(1L);
        receivers.addAll(jdbc.queryForList(
                "select distinct ur.user_id from sys_user_role ur join sys_role r on r.role_id=ur.role_id "
                + "where r.role_key='registrar'", Long.class));
        String title = jdbc.queryForObject("select title from exh_item where item_id=?", String.class, itemId);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("itemTitle", title);
        vars.put("failReason", reason);
        vars.put("retry", 3);
        for (Long receiver : receivers.stream().distinct().toList())
        {
            dispatcher.send("NT-11", receiver, vars, "exhibit", itemId);
        }
    }
}
