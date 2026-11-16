package com.legaltraining.web.controller.ops;

import java.net.URI;
import java.io.InputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.business.ops.support.OpenApiSign;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class RemoteRecommendationInbox
{
    public static final Set<String> CHECKS = Set.of("accuracy", "compliance", "copyright", "privacy", "usability");
    private static final Set<String> SIGN_MODES = Set.of("实名", "部分匿名", "完全匿名");
    private static final Set<String> SCOPES = Set.of("仅展厅内", "含对外分享");
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final String sourceApp;
    private final URI callback;
    private final String callbackApp;
    private final String callbackKey;
    private final int retrySeconds;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public RemoteRecommendationInbox(JdbcTemplate db, TransactionTemplate transactions,
            @Value("${legal-training.recommendation-inbox.source-app-id:}") String sourceApp,
            @Value("${legal-training.recommendation-inbox.callback-endpoint:}") String callback,
            @Value("${legal-training.recommendation-inbox.callback-app-id:}") String callbackApp,
            @Value("${legal-training.recommendation-inbox.callback-app-secret:}") String callbackSecret,
            @Value("${legal-training.recommendation-inbox.retry-seconds:60}") int retrySeconds)
    {
        this.db = db;
        this.transactions = transactions;
        this.sourceApp = sourceApp;
        this.callback = callback.isBlank() ? null : URI.create(callback);
        this.callbackApp = callbackApp;
        this.callbackKey = OpenApiSign.signKey(callbackSecret);
        this.retrySeconds = Math.min(3600, Math.max(1, retrySeconds));
        if (!sourceApp.isBlank() && (this.callback == null || callbackApp.isBlank() || callbackSecret.isBlank()
                || !trustedEndpoint(this.callback)))
            throw new IllegalArgumentException("External recommendation callback configuration is invalid");
    }

    public boolean enabled() { return !sourceApp.isBlank(); }

    public boolean accepts(String appCode) { return sourceApp.equals(appCode); }

    public void withdraw(String appCode, long recId)
    {
        db.update("""
                insert into ops_rec_inbox(source_app,source_rec_id,title,sign_mode,scope_desc,proof_type,confirmed_at,status)
                values(?,?,'','','','','',4) on duplicate key update
                status=4,content=null,reason=null,source_teacher=null,title='',sign_mode='',author_label='匿名',
                scope_desc='',proof_type='',confirmed_at='',
                callback_state=null,lease_until=null
                """, appCode, recId);
        db.update("""
                update exh_item i join ops_rec_inbox r on r.item_id=i.item_id
                set i.status=3,i.offline_time=now(),i.offline_reason='学生撤回展示授权',i.update_time=now()
                where r.source_app=? and r.source_rec_id=?
                """, appCode, recId);
        db.update("""
                update exh_publish_job j join ops_rec_inbox r on r.item_id=j.item_id
                set j.status=3,j.fail_reason='学生撤回展示授权'
                where r.source_app=? and r.source_rec_id=? and j.status=0
                """, appCode, recId);
    }

    public long createDraft(long id, long zoneId, long userId)
    {
        if (!enabled()) throw new ServiceException("独立成果接收未启用");
        return transactions.execute(transaction -> {
            var rows = db.queryForList("""
                    select status,item_id,callback_state,title,content,author_label from ops_rec_inbox
                    where id=? and source_app=? for update
                    """, id, sourceApp);
            if (rows.isEmpty() || ((Number) rows.get(0).get("status")).intValue() != 2)
                throw new ServiceException("只有审核通过的成果可生成展项");
            var row = rows.get(0);
            if (row.get("item_id") instanceof Number existing) return existing.longValue();
            if (!(row.get("callback_state") instanceof Number state) || state.intValue() != 1)
                throw new ServiceException("请等待教学端确认审核结果后生成展项");
            if (db.queryForList("select zone_id from exh_zone where zone_id=? and del_flag='0'", Long.class, zoneId).isEmpty())
                throw new ServiceException("目标展区不存在");
            var keys = new GeneratedKeyHolder();
            db.update(connection -> {
                var statement = connection.prepareStatement("""
                        insert into exh_item(zone_id,title,content,author_name,editor_id,create_time)
                        values(?,?,?,?,?,now())
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, zoneId);
                statement.setString(2, (String) row.get("title"));
                statement.setString(3, (String) row.get("content"));
                statement.setString(4, (String) row.get("author_label"));
                statement.setLong(5, userId);
                return statement;
            }, keys);
            long itemId = keys.getKey().longValue();
            db.update("""
                    update ops_rec_inbox set item_id=?,callback_state=0,attempts=0,due_at=now(),lease_id=?,lease_until=null
                    where id=?
                    """, itemId, UUID.randomUUID().toString(), id);
            return itemId;
        });
    }

    public void requirePublicationScope(long itemId, int level)
    {
        var rows = db.queryForList("select status,scope_desc,callback_state,exhibit_revision from ops_rec_inbox where item_id=?", itemId);
        if (rows.isEmpty()) return;
        var row = rows.get(0);
        if (((Number) row.get("status")).intValue() != 2
                || ((Number) row.get("exhibit_revision")).longValue() == 0
                    && (!(row.get("callback_state") instanceof Number state) || state.intValue() != 1)
                || level == 2 && !"含对外分享".equals(row.get("scope_desc")))
            throw new ServiceException("来源授权状态、回写状态或范围不允许发布");
    }

    public void exhibitChanged(long itemId, int state)
    {
        db.update("""
                update ops_rec_inbox set exhibit_status=?,exhibit_revision=exhibit_revision+1,
                    callback_state=0,attempts=0,due_at=now(),lease_id=?,lease_until=null
                where item_id=? and status=2 and (?=2 or exhibit_revision>0)
                    and (exhibit_status is null or exhibit_status<>?)
                """, state, UUID.randomUUID().toString(), itemId, state, state);
    }

    public void receive(String appCode, long recId, String title, String content, String reason,
            String signMode, String scope, String proof, String confirmedAt, String teacherAccount, String authorLabel)
    {
        if (recId <= 0 || title == null || title.isBlank() || signMode == null || !SIGN_MODES.contains(signMode)
                || scope == null || !SCOPES.contains(scope) || proof == null || confirmedAt == null
                || teacherAccount == null || teacherAccount.isBlank())
            throw new ServiceException("需要成果标题及有效的授权凭证");
        try { LocalDateTime.parse(confirmedAt.replace(' ', 'T')); }
        catch (java.time.format.DateTimeParseException ex) { throw new ServiceException("授权确认时间无效"); }
        String author = switch (signMode) {
            case "实名" -> authorLabel != null && !authorLabel.isBlank() ? authorLabel : "匿名";
            case "部分匿名" -> authorLabel != null && authorLabel.matches("学号尾号 .{1,4}") ? authorLabel : "匿名";
            default -> "匿名";
        };
        db.update("""
                insert into ops_rec_inbox(source_app,source_rec_id,title,content,reason,sign_mode,scope_desc,
                    proof_type,confirmed_at,source_teacher,author_label)
                values(?,?,?,?,?,?,?,?,?,?,?) on duplicate key update id=id
                """, appCode, recId, title, content, reason, signMode, scope, proof, confirmedAt, teacherAccount, author);
    }

    public void review(long id, long userId, String account, boolean approve, Set<String> checks, String opinion)
    {
        if (!enabled()) throw new ServiceException("独立成果接收未启用");
        if (approve && !CHECKS.equals(checks)) throw new ServiceException("须完成五项审核");
        if (!approve && (opinion == null || opinion.isBlank())) throw new ServiceException("请填写驳回原因");
        int changed = db.update("""
                update ops_rec_inbox set status=?,decision_checks=?,opinion=?,reviewer_id=?,reviewed_at=now(),
                    callback_state=0,due_at=now(),lease_id=?
                where id=? and source_app=? and status=1 and source_teacher<>?
                """, approve ? 2 : 3, JSON.toJSONString(checks), opinion, userId, UUID.randomUUID().toString(), id, sourceApp, account);
        if (changed != 1) throw new ServiceException("成果不在待审状态，或当前账号是推荐人");
    }

    public List<Map<String, Object>> list(boolean auditor)
    {
        if (!enabled()) return List.of();
        return db.queryForList("""
                select id,source_rec_id as sourceRecId,title,reason,sign_mode as signMode,scope_desc as scopeDesc,
                    proof_type as proofType,confirmed_at as confirmedAt,status,opinion,item_id as itemId,callback_state as callbackState
                from ops_rec_inbox where source_app=? and (status=2 or (? and status in (1,3))) order by id desc limit 200
                """, sourceApp, auditor);
    }

    public Map<String, Object> detail(long id)
    {
        if (!enabled()) throw new ServiceException("独立成果接收未启用");
        return db.queryForList("""
                select title,content,reason,proof_type as proofType,confirmed_at as confirmedAt,sign_mode as signMode,
                    scope_desc as scopeDesc,opinion,status,callback_state as callbackState,decision_checks as checks
                from ops_rec_inbox where id=? and source_app=? and status<>4
                """, id, sourceApp).stream().findFirst().orElseThrow(() -> new ServiceException("成果不存在"));
    }

    public List<?> evidence(long id)
    {
        try
        {
            var response = http.send(evidenceRequest(id, ""), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return List.of();
            var payload = JSON.parseObject(response.body());
            if (response.statusCode() != 200 || payload == null || payload.getIntValue("code") != 200)
                throw new ServiceException("教学侧证据清单暂不可用");
            return payload.getJSONArray("data");
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("获取教学侧证据清单中断");
        }
        catch (IOException ex) { throw new ServiceException("教学侧证据清单暂不可用"); }
    }

    public ResponseEntity<StreamingResponseBody> evidenceFile(long id, String token) throws IOException, InterruptedException
    {
        if (!token.matches("authorization|[0-9]{1,19}")) return ResponseEntity.notFound().build();
        var response = http.send(evidenceRequest(id, "/" + token), HttpResponse.BodyHandlers.ofInputStream());
        InputStream stream = response.body();
        if (response.statusCode() != 200 || response.headers().firstValueAsLong("Content-Length").orElse(-1) < 0
                || response.headers().firstValue("Content-Type")
                    .filter(MediaType.APPLICATION_OCTET_STREAM_VALUE::equalsIgnoreCase).isEmpty())
        {
            stream.close();
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", response.headers().firstValue("Content-Disposition").orElse("attachment"))
                .contentLength(response.headers().firstValueAsLong("Content-Length").getAsLong())
                .body(output -> { try (stream) { stream.transferTo(output); } });
    }

    private HttpRequest evidenceRequest(long id, String suffix)
    {
        if (!enabled()) throw new ServiceException("独立成果接收未启用");
        Long recId = db.queryForList("""
                select source_rec_id from ops_rec_inbox where id=? and source_app=? and status in (1,2,3)
                """, Long.class, id, sourceApp).stream().findFirst().orElseThrow(() -> new ServiceException("成果不存在"));
        URI endpoint = callback.resolve(recId + "/evidence" + suffix);
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        return HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("X-App-Id", callbackApp).header("X-Timestamp", timestamp).header("X-Nonce", nonce)
                .header("X-Sign", OpenApiSign.sign(callbackKey, callbackApp, timestamp, nonce, endpoint.getPath())).GET().build();
    }

    @Scheduled(fixedDelayString = "${legal-training.recommendation-inbox.poll-ms:10000}")
    public void deliver()
    {
        if (!enabled()) return;
        for (int index = 0; index < 5; index++)
        {
            Job job = transactions.execute(status -> claim());
            if (job == null) return;
            int outcome = send(job);
            int attempts = job.attempts() + 1;
            int state = outcome == 200 ? 1 : outcome == 409 || attempts >= 6 ? 2 : 0;
            long delay = (long) retrySeconds << (attempts - 1);
            db.update("""
                    update ops_rec_inbox set callback_state=?,attempts=?,due_at=timestampadd(second,?,now()),
                        lease_until=null where id=? and lease_until=? and callback_state=0
                    """, state, attempts, delay, job.id(), job.leaseUntil());
        }
    }

    private Job claim()
    {
        var rows = db.queryForList("""
                select id,source_rec_id as recId,if(status=2,'passed','rejected') as result,opinion,
                    reviewer_id as auditorId,item_id as itemId,exhibit_status as exhibitStatus,
                    nullif(exhibit_revision,0) as exhibitRevision,
                    lease_id as callbackId,attempts from ops_rec_inbox
                where callback_state=0 and due_at<=now() and (lease_until is null or lease_until<now())
                order by due_at,id limit 1 for update skip locked
                """);
        if (rows.isEmpty()) return null;
        var data = rows.get(0);
        long id = ((Number) data.remove("id")).longValue();
        int attempts = ((Number) data.remove("attempts")).intValue();
        var leaseUntil = db.queryForObject("select timestampadd(second,30,now())", LocalDateTime.class);
        db.update("update ops_rec_inbox set lease_until=? where id=?", leaseUntil, id);
        return new Job(id, attempts, leaseUntil, JSON.toJSONString(data));
    }

    private int send(Job job)
    {
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        try
        {
            var request = HttpRequest.newBuilder(callback).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json").header("X-App-Id", callbackApp)
                    .header("X-Timestamp", timestamp).header("X-Nonce", nonce)
                    .header("X-Sign", OpenApiSign.sign(callbackKey, callbackApp, timestamp, nonce, callback.getPath()))
                    .POST(HttpRequest.BodyPublishers.ofString(job.data())).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) return 409;
            var body = JSON.parseObject(response.body(), AjaxResult.class);
            return response.statusCode() == 200 && body != null && body.isSuccess() ? 200 : 0;
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return 0;
        }
        catch (java.io.IOException | RuntimeException ex) { return 0; }
    }

    private static boolean trustedEndpoint(URI uri)
    {
        return uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
                && uri.getQuery() == null && uri.getPath().endsWith("/openapi/v1/rec/callback")
                && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && List.of("localhost", "127.0.0.1").contains(uri.getHost()));
    }

    private record Job(long id, int attempts, LocalDateTime leaseUntil, String data) {}
}
