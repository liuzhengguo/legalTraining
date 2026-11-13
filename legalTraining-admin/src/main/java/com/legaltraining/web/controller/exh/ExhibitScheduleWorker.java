package com.legaltraining.web.controller.exh;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.legaltraining.common.exception.ServiceException;

@Component
public class ExhibitScheduleWorker
{
    private static final Logger log = LoggerFactory.getLogger(ExhibitScheduleWorker.class);
    private final JdbcTemplate jdbc;
    private final ExhibitController exhibits;
    private final TransactionTemplate transactions;

    public ExhibitScheduleWorker(JdbcTemplate jdbc, ExhibitController exhibits, TransactionTemplate transactions)
    {
        this.jdbc = jdbc;
        this.exhibits = exhibits;
        this.transactions = transactions;
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
            jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time) "
                    + "select distinct 'NT-11',u.user_id,'展项定时发布失败',j.fail_reason,'exhibit',j.item_id,now() "
                    + "from exh_publish_job j join sys_user u on u.status='0' and u.del_flag='0' "
                    + "where j.item_id=? and j.status=2 and (u.user_id=j.operator_id or u.user_id=1 "
                    + "or exists(select 1 from sys_user_role ur join sys_role r on r.role_id=ur.role_id "
                    + "where ur.user_id=u.user_id and r.role_key='registrar' and r.status='0' and r.del_flag='0'))", itemId);
        });
    }
}
