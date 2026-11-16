package com.legaltraining.web.controller.ops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.business.ops.support.OpenApiSign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecPushQueue
{
    private static final int MAX_RETRIES = 5;
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final URI endpoint;
    private final String appId;
    private final String signingKey;
    private final int firstRetrySeconds;
    private final boolean independentReceiver;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

    public RecPushQueue(JdbcTemplate db, TransactionTemplate transactions,
            @Value("${legal-training.recommendation-push.endpoint:}") String url, @Value("${legal-training.recommendation-push.app-id:}") String appId,
            @Value("${legal-training.recommendation-push.app-secret:}") String secret,
            @Value("${legal-training.recommendation-push.initial-retry-seconds:60}") int firstRetrySeconds,
            @Value("${legal-training.recommendation-push.independent-receiver:false}") boolean independentReceiver)
    {
        this.db = db;
        this.transactions = transactions;
        this.appId = appId;
        signingKey = secret.isBlank() ? "" : OpenApiSign.signKey(secret);
        this.firstRetrySeconds = Math.max(1, Math.min(3600, firstRetrySeconds));
        this.independentReceiver = independentReceiver;
        endpoint = url.isBlank() ? null : URI.create(url);
        if (endpoint != null && (appId.isBlank() || signingKey.isBlank() || !trustedEndpoint(endpoint)))
            throw new IllegalArgumentException("External recommendation push configuration is invalid");
    }

    public boolean enabled() { return endpoint != null; }
    public boolean independentReceiver() { return enabled() && independentReceiver; }

    public void enqueue(long recId)
    {
        db.update("insert ignore into ops_rec_push_job(rec_id) values(?)", recId);
    }

    public void cancel(long recId)
    {
        if (independentReceiver())
        {
            db.update("delete from ops_rec_evidence_snapshot where rec_id=?", recId);
            db.update("""
                    update ops_rec_push_job set status=0,attempts=0,due_at=now(),lease_id=null,lease_until=null
                    where rec_id=?
                    """, recId);
        }
        else if (enabled()) db.update("update ops_rec_push_job set status=3,lease_id=null,lease_until=null where rec_id=? and status=0", recId);
    }

    public String visibleAuditQueue()
    {
        return enabled() ? "r.status=1 and (" + (independentReceiver ? "" : "r.push_status=1 or ") + "not exists "
                + "(select 1 from ops_rec_push_job j where j.rec_id=r.rec_id))" : "r.status=1";
    }

    @Scheduled(fixedDelayString = "${legal-training.recommendation-push.poll-ms:10000}")
    public void runDueJobs()
    {
        if (!enabled()) return;
        for (int i = 0; i < 5; i++)
        {
            Job job = claim();
            if (job == null) return;
            send(job);
        }
    }

    private Job claim()
    {
        return transactions.execute(status -> {
            List<Map<String,Object>> candidates = db.queryForList("""
                    select j.rec_id as recId,j.attempts,r.status as recStatus,r.teacher_id as teacherId,r.submission_id as submissionId,
                           r.target_zone as targetZone,r.sign_mode as signMode,r.level_code as level,
                           r.reason,a.scope_desc as scopeDesc,t.title,teacher.user_name as teacherAccount,
                           case r.sign_mode when '实名' then coalesce(student.nick_name,'匿名')
                               when '部分匿名' then coalesce(concat('学号尾号 ',right(profile.student_no,4)),'匿名')
                               else '匿名' end as authorLabel,
                           s.version as submissionVersion, case when evidence.rec_id is not null then evidence.source_content
                               else coalesce(v.content,s.content) end as content,
                           case a.auth_type when 1 then 'offline' else 'online' end as authProofType,
                           date_format(a.confirm_time,'%Y-%m-%d %H:%i:%s') as confirmedAt
                    from ops_rec_push_job j join edu_recommendation r on r.rec_id=j.rec_id
                    left join edu_authorization a on a.rec_id=r.rec_id and a.status=1
                    join edu_submission s on s.submission_id=r.submission_id
                    join edu_task t on t.task_id=s.task_id
                    join sys_user teacher on teacher.user_id=r.teacher_id
                    left join sys_user student on student.user_id=s.user_id
                    left join edu_user_profile profile on profile.user_id=s.user_id
                    left join edu_submission_version v on v.submission_id=s.submission_id and v.version=s.version
                    left join ops_rec_evidence_snapshot evidence on evidence.rec_id=r.rec_id
                    where j.status=0 and ((r.status=1 and a.status=1) or (r.status=4 and ?)) and j.due_at<=now()
                      and (j.lease_until is null or j.lease_until<now())
                    order by j.due_at,j.rec_id limit 1 for update skip locked
                    """, independentReceiver);
            if (candidates.isEmpty()) return null;
            var fields = candidates.get(0);
            boolean withdrawn = ((Number) fields.remove("recStatus")).intValue() == 4;
            Object submissionVersion = fields.remove("submissionVersion");
            if (!independentReceiver)
            {
                fields.keySet().removeAll(List.of("reason", "title", "content", "scopeDesc", "teacherAccount", "authorLabel"));
            }
            int tries = ((Number) fields.remove("attempts")).intValue();
            long id = ((Number) fields.get("recId")).longValue();
            if (independentReceiver && !withdrawn)
                db.update("""
                        insert ignore into ops_rec_evidence_snapshot(rec_id,submission_id,submission_version,source_content)
                        values(?,?,?,?)
                        """, id, fields.get("submissionId"), submissionVersion, fields.get("content"));
            String lease = UUID.randomUUID().toString();
            db.update("update ops_rec_push_job set lease_id=?,lease_until=timestampadd(second,45,now()) where rec_id=?", lease,id);
            return new Job(id, tries, lease, JSON.toJSONString(withdrawn
                    ? Map.of("recId", id, "event", "withdrawn") : fields));
        });
    }

    private void send(Job job)
    {
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String signature = OpenApiSign.sign(signingKey, appId, timestamp, nonce, endpoint.getPath());
        String error = null;
        try
        {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json").header("X-App-Id", appId)
                    .header("X-Timestamp", timestamp).header("X-Nonce", nonce).header("X-Sign", signature)
                    .header("X-Idempotency-Key", Long.toString(job.id()))
                    .POST(HttpRequest.BodyPublishers.ofString(job.payload())).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var envelope = JSON.parseObject(response.body());
            var body = envelope == null ? null : envelope.getJSONObject("data");
            if (response.statusCode() != 200 || envelope == null || envelope.getIntValue("code") != 200
                    || body == null || !body.getBooleanValue("accepted") || body.getLongValue("recId") != job.id())
                error = "接收端未受理，HTTP " + response.statusCode();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            error = "推送任务中断";
        }
        catch (Exception ex)
        {
            error = "无法连接接收端或响应无效";
        }
        String failure = error;
        transactions.executeWithoutResult(status -> finish(job, failure));
    }

    private void finish(Job job, String error)
    {
        int attempts = job.tries() + 1;
        int outcome = error == null ? 1 : attempts > MAX_RETRIES ? 2 : 0;
        long delay = (long) firstRetrySeconds << (attempts - 1);
        db.queryForList("select rec_id from edu_recommendation where rec_id=? for update", Long.class, job.id());
        int updated = db.update("""
                update ops_rec_push_job set status=?,attempts=?,last_error=?,lease_id=null,lease_until=null,
                    due_at=timestampadd(second,?,now()) where rec_id=? and lease_id=? and status=0
                """, outcome, attempts, error, delay, job.id(), job.lease());
        if (updated == 0) return;
        db.update("update edu_recommendation set push_status=?,push_retry=? where rec_id=? and status=1",
                outcome, Math.min(MAX_RETRIES, error == null ? job.tries() : attempts), job.id());
        if (outcome == 1 && !independentReceiver)
        {
            db.update("""
                    insert into ops_notification(event_code,receiver_id,title,biz_type,biz_id,send_time)
                    select distinct 'NT-09',ur.user_id,'新成果待审','rec',r.rec_id,now()
                    from edu_recommendation r cross join sys_user_role ur
                    join sys_role role on role.role_id=ur.role_id join sys_user u on u.user_id=ur.user_id
                    where r.rec_id=? and r.status=1 and role.role_key='auditor' and role.status='0'
                    and role.del_flag='0' and u.status='0' and u.del_flag='0'
                    """, job.id());
        }
        else if (outcome == 2)
        {
            db.update("""
                    insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time)
                    select 'REC-PUSH-FAIL',teacher_id,'成果推送失败','自动重试已结束，请联系管理员处理。','rec',rec_id,now()
                    from edu_recommendation where rec_id=? and status=1
                    """, job.id());
        }
    }

    private static boolean trustedEndpoint(URI uri)
    {
        return uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
                && uri.getQuery() == null && uri.getPath().endsWith("/openapi/v1/rec/push")
                && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && List.of("localhost", "127.0.0.1").contains(uri.getHost()));
    }

    private record Job(long id, int tries, String lease, String payload) {}
}
