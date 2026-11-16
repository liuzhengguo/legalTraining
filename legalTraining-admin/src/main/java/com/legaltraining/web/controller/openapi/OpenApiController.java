package com.legaltraining.web.controller.openapi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.legaltraining.business.ops.domain.OpsApp;
import com.legaltraining.web.controller.ops.RemoteRecommendationInbox;
import com.legaltraining.web.interceptor.OpenApiInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * 开放接口业务端点（OP-02-01 ~ OP-02-09）。
 *
 * 鉴权（AppID/签名/配额/IP 白名单/防重放）由 OpenApiInterceptor 统一完成，
 * 本类只负责业务语义；所有响应沿用 code/msg/data 结构。
 */
@RestController
@RequestMapping("/openapi/v1")
public class OpenApiController extends BaseController
{
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final DateTimeFormatter VERSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final String ITEM_COLUMNS = "item_id as itemId,zone_id as zoneId,title,cover,summary,"
            + "item_type as itemType,content,author_name as authorName,view_level as viewLevel,status,version,"
            + "date_format(publish_time,'%Y-%m-%d %H:%i:%s') as publishTime";
    private final JdbcTemplate jdbc;

    private final CaseLibrary caseLibrary;
    private final RemoteRecommendationInbox inbox;
    private final String callbackAppId;
    private final OpenApiVersionPolicy versions;

    public OpenApiController(JdbcTemplate jdbc, CaseLibrary caseLibrary, RemoteRecommendationInbox inbox, OpenApiVersionPolicy versions,
            @Value("${legal-training.recommendation-push.callback-app-id:}") String callbackAppId)
    {
        this.jdbc = jdbc;
        this.caseLibrary = caseLibrary;
        this.inbox = inbox;
        this.callbackAppId = callbackAppId;
        this.versions = versions;
    }

    public record ShareForm(@NotBlank @Pattern(regexp = "zone|item") String targetType,
            @NotNull Long targetId,
            @NotBlank @Pattern(regexp = "qrcode|link|terminal|portal") String channel) {}
    public record PushForm(@NotNull Long recId, Long submissionId, Long teacherId,
            @Size(max = 1000) String reason, Long targetZone, String signMode,
            @Size(max = 16) String level,
            @Pattern(regexp = "online|offline") String authProofType,
            @Size(max = 32) String confirmedAt, @Size(max = 200) String title,
            @Size(max = 65535) String content, @Size(max = 32) String scopeDesc,
            @Pattern(regexp = "withdrawn") String event, @Size(max = 64) String teacherAccount,
            @Size(max = 100) String authorLabel) {}
    public record CallbackForm(@NotNull Long recId, @NotBlank @Pattern(regexp = "passed|rejected") String result,
            Long itemId, @Size(max = 1000) String opinion, Long auditorId, String auditTime,
            @Pattern(regexp = "[a-f0-9-]{36}") String callbackId, Integer exhibitStatus, Long exhibitRevision) {}
    public record DeviceTokenForm(@NotBlank @Size(max = 64) String deviceCode,
            @NotBlank @Size(max = 16) String activateCode) {}
    public record ConsultForm(@NotBlank @Size(max = 100) String topic, Long itemId,
            @Pattern(regexp = "student|teacher|visitor") String identity, @Size(max = 64) String traceId) {}

    @GetMapping("/version")
    public ResponseEntity<AjaxResult> version()
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(success(versions.current()));
    }

    // --------------------------------------------------------------- OP-02-01 展区列表

    @GetMapping("/zones")
    public ResponseEntity<AjaxResult> zones(@RequestParam(defaultValue = "public") String level,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        int openLevel = "internal".equals(level) ? 1 : 2;
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String countSql = "select count(1) from exh_zone where status=1 and open_level>=? and del_flag='0'";
        Long total = jdbc.queryForObject(countSql, Long.class, openLevel);
        List<Map<String, Object>> rows = jdbc.queryForList("select zone_id as zoneId,zone_code as zoneCode,"
                + "zone_name as zoneName,cover,intro,route_no as routeNo,open_level as openLevel,layout,"
                + "(select count(1) from exh_item i where i.zone_id=exh_zone.zone_id and i.status=2 "
                + "and i.view_level>=? and i.del_flag='0') as itemCount "
                + "from exh_zone where status=1 and open_level>=? and del_flag='0' "
                + "order by route_no,zone_id limit ? offset ?",
                openLevel, openLevel, safePageSize, (safePageNum - 1) * safePageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total == null ? 0 : total);
        data.put("pageNum", safePageNum);
        data.put("pageSize", safePageSize);
        data.put("rows", rows);
        return cache(AjaxResult.success(data), 300);
    }

    // --------------------------------------------------------------- OP-02-02 展项详情

    @GetMapping("/items/{itemId}")
    public ResponseEntity<AjaxResult> item(@PathVariable long itemId,
            @RequestParam(required = false) String channel)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select " + ITEM_COLUMNS + ",offline_reason as offlineReason "
                + "from exh_item where item_id=? and del_flag='0'", itemId);
        if (rows.isEmpty()) return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).build();
        Map<String, Object> item = rows.get(0);
        int status = ((Number) item.get("status")).intValue();
        Map<String, Object> zone = zone(((Number) item.get("zoneId")).longValue());
        boolean zoneOpen = zone != null && ((Number) zone.get("status")).intValue() == 1
                && ((Number) zone.get("openLevel")).intValue() >= 1;
        if (status == 3)
        {
            AjaxResult gone = AjaxResult.error(410, "内容已下线");
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("reasonType", offlineReasonType((String) item.get("offlineReason")));
            reason.put("zoneId", item.get("zoneId"));
            gone.put("data", reason);
            return ResponseEntity.status(410).cacheControl(CacheControl.noStore()).body(gone);
        }
        if (status != 2 || ((Number) item.get("viewLevel")).intValue() < 1 || !zoneOpen)
        {
            return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).build();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", item.get("itemId"));
        data.put("zoneId", item.get("zoneId"));
        data.put("zoneName", zone.get("zoneName"));
        data.put("title", item.get("title"));
        data.put("summary", item.get("summary"));
        data.put("type", item.get("itemType"));
        data.put("cover", item.get("cover"));
        data.put("content", parseContent((String) item.get("content")));
        data.put("authorName", item.get("authorName"));
        data.put("media", jdbc.queryForList("select media_type as type,url,cover,duration from exh_item_media "
                + "where item_id=? and status='0' order by sort_no, media_id", itemId));
        data.put("prevItemId", neighbour(itemId, (Long) item.get("zoneId"), true));
        data.put("nextItemId", neighbour(itemId, (Long) item.get("zoneId"), false));
        data.put("relatedLinks", relatedLinks((Long) item.get("zoneId"), itemId));
        data.put("version", item.get("version"));
        data.put("publishTime", item.get("publishTime"));
        data.put("channel", channel);
        return cache(AjaxResult.success(data), 300);
    }

    // --------------------------------------------------------------- OP-02-03 发布状态

    @GetMapping("/publish-state")
    public ResponseEntity<AjaxResult> publishState(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime since)
    {
        String version = jdbc.queryForList("select config_value from sys_config where config_key='biz.exhibit.contentVersion'",
                String.class).stream().findFirst().orElse(null);
        if (version == null || version.isBlank())
        {
            String stamp = jdbc.queryForObject("select date_format(coalesce(max(update_time),now()),'%Y%m%d-%H%i') "
                    + "from exh_item", String.class);
            version = stamp;
        }
        List<Long> zoneIds = jdbc.queryForList("select zone_id from exh_zone where status=1 and open_level>=1 "
                + "and del_flag='0' order by route_no,zone_id", Long.class);
        List<Long> changed = since == null
                ? jdbc.queryForList("select item_id from exh_item where status=2 and del_flag='0' order by item_id", Long.class)
                : jdbc.queryForList("select item_id from exh_item where status=2 and del_flag='0' and update_time>=? "
                        + "order by item_id", Long.class, since);
        List<Long> removed = since == null ? List.of()
                : jdbc.queryForList("select item_id from exh_item where status=3 and del_flag='0' and offline_time>=? "
                        + "order by item_id", Long.class, since);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contentVersion", version);
        data.put("updateTime", LocalDateTime.now(ZONE).withNano(0).toString().replace('T', ' '));
        data.put("zones", zoneIds);
        data.put("changedItems", changed);
        data.put("removedItems", removed);
        data.put("since", since == null ? null : since.toString().replace('T', ' '));
        return cache(AjaxResult.success(data), 60);
    }

    // --------------------------------------------------------------- OP-02-04 分享

    @PostMapping("/share")
    public AjaxResult share(@Valid @RequestBody ShareForm form)
    {
        Integer exists = jdbc.queryForObject("item".equals(form.targetType())
                ? "select count(1) from exh_item where item_id=? and del_flag='0'"
                : "select count(1) from exh_zone where zone_id=? and del_flag='0'", Integer.class, form.targetId());
        if (exists == null || exists == 0) throw new ServiceException("分享对象不存在");
        List<Map<String, Object>> rows = jdbc.queryForList("select short_code as shortCode,qr_path as qrPath "
                + "from exh_share_link where target_type=? and target_id=? and channel=? order by link_id desc limit 1",
                form.targetType(), form.targetId(), form.channel());
        String code;
        String qr;
        if (rows.isEmpty())
        {
            code = nextCode();
            qr = "/files/qr/" + code + ".png";
            jdbc.update("insert into exh_share_link(target_type,target_id,channel,short_code,qr_path,create_time) "
                    + "values(?,?,?,?,?,now())", form.targetType(), form.targetId(), form.channel(), code, qr);
        }
        else
        {
            code = (String) rows.get(0).get("shortCode");
            qr = (String) rows.get(0).get("qrPath");
        }
        String base = config("biz.exhibit.shareBase", "https://exh.legaltraining.cn/s/");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shortUrl", base + code);
        data.put("qrUrl", qr);
        data.put("shortCode", code);
        data.put("expireAt", null);
        return AjaxResult.success(data);
    }

    // --------------------------------------------------------------- OP-02-05 推荐推送

    @PostMapping("/rec/push")
    @Transactional
    public ResponseEntity<AjaxResult> push(@RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(OpenApiInterceptor.ATTR_APP) OpsApp app, @Valid @RequestBody PushForm form)
    {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && !idempotencyKey.equals(String.valueOf(form.recId())))
        {
            return ResponseEntity.status(400).body(AjaxResult.error(400, "幂等键与 recId 不一致"));
        }
        if (inbox.enabled())
        {
            if (!inbox.accepts(app.getAppCode())) return ResponseEntity.status(403).body(AjaxResult.error(403, "应用未绑定到成果接收端"));
            if (idempotencyKey == null || idempotencyKey.isBlank())
                return ResponseEntity.badRequest().body(AjaxResult.error(400, "缺少幂等键"));
            if (form.recId() <= 0) return ResponseEntity.badRequest().body(AjaxResult.error(400, "推荐编号无效"));
            if ("withdrawn".equals(form.event())) inbox.withdraw(app.getAppCode(), form.recId());
            else inbox.receive(app.getAppCode(), form.recId(), form.title(), form.content(), form.reason(),
                        form.signMode(), form.scopeDesc(), form.authProofType(), form.confirmedAt(),
                        form.teacherAccount(), form.authorLabel());
            return ResponseEntity.ok(AjaxResult.success(Map.of("accepted", true, "recId", form.recId())));
        }
        List<Map<String, Object>> rows = jdbc.queryForList("select rec_id as recId,status,push_status as pushStatus "
                + "from edu_recommendation where rec_id=? for update", form.recId());
        if (rows.isEmpty()) return ResponseEntity.status(404).body(AjaxResult.error(404, "推荐记录不存在"));
        Map<String, Object> rec = rows.get(0);
        int status = ((Number) rec.get("status")).intValue();
        if (status == 0 || status == 4)
        {
            return ResponseEntity.status(409).body(AjaxResult.error(605, "推荐尚未获得学生授权或已撤回"));
        }
        int pushStatus = ((Number) rec.get("pushStatus")).intValue();
        if (pushStatus != 1)
        {
            jdbc.update("update edu_recommendation set push_status=1,push_retry=0,update_time=now() where rec_id=?",
                    form.recId());
        }
        return ResponseEntity.ok(AjaxResult.success(Map.of("accepted", true, "recId", form.recId())));
    }

    // --------------------------------------------------------------- OP-02-06 审核回写

    @PostMapping("/rec/callback")
    @Transactional
    public ResponseEntity<AjaxResult> callback(@RequestAttribute(OpenApiInterceptor.ATTR_APP) OpsApp app,
            @Valid @RequestBody CallbackForm form)
    {
        boolean remote = form.callbackId() != null;
        if (remote != !callbackAppId.isBlank() || remote && !callbackAppId.equals(app.getAppCode()))
            return ResponseEntity.status(403).body(AjaxResult.error(403, "应用未绑定到审核回写"));
        List<Map<String, Object>> rows = jdbc.queryForList("select r.rec_id as recId,r.status,r.teacher_id as teacherId,"
                + "s.user_id as studentId,r.exhibit_id as exhibitId from edu_recommendation r "
                + "join edu_submission s on s.submission_id=r.submission_id where r.rec_id=? for update", form.recId());
        if (rows.isEmpty()) return ResponseEntity.status(404).body(AjaxResult.error(404, "推荐记录不存在"));
        Map<String, Object> rec = rows.get(0);
        int status = ((Number) rec.get("status")).intValue();
        String fingerprint = null;
        if (form.callbackId() != null)
        {
            if (jdbc.queryForList("select rec_id from ops_rec_push_job where rec_id=?", Long.class, form.recId()).isEmpty())
                return ResponseEntity.status(404).body(AjaxResult.error(404, "推荐未发送到独立展厅"));
            fingerprint = com.legaltraining.business.ops.support.OpenApiSign.hmacSha256("callback-payload", JSON.toJSONString(form));
            List<String> receipts = jdbc.queryForList(
                    "select payload_hash from ops_rec_callback_receipt where rec_id=? and callback_id=?",
                    String.class, form.recId(), form.callbackId());
            if (!receipts.isEmpty())
                return receipts.get(0).equals(fingerprint) ? ResponseEntity.ok(AjaxResult.success(Map.of("ok", true)))
                        : ResponseEntity.status(409).body(AjaxResult.error(606, "回写编号与已受理内容冲突"));
            if (form.exhibitStatus() != null || form.exhibitRevision() != null)
                return recordExhibitStatus(form, fingerprint, rec, status);
            if (form.itemId() != null)
            {
                if (form.itemId() <= 0 || status != 2 || rec.get("exhibitId") != null || !"passed".equals(form.result()))
                    return ResponseEntity.status(409).body(AjaxResult.error(606, "当前推荐无法关联展项"));
                jdbc.update("update edu_recommendation set exhibit_id=?,update_time=now() where rec_id=?",
                        form.itemId(), form.recId());
                recordRemoteCallback(form, fingerprint);
                return ResponseEntity.ok(AjaxResult.success(Map.of("ok", true)));
            }
        }
        if (status != 1)
        {
            return ResponseEntity.status(409).body(AjaxResult.error(606, "该推荐已回写过审核结果"));
        }
        boolean passed = "passed".equals(form.result());
        if (!passed && (form.opinion() == null || form.opinion().isBlank()))
        {
            return ResponseEntity.status(400).body(AjaxResult.error(400, "驳回须填写审核意见"));
        }
        if (!remote)
        {
            if (form.auditorId() == null) return ResponseEntity.badRequest().body(AjaxResult.error(400, "缺少审核人"));
            jdbc.update("insert into exh_review(rec_id,auditor_id,check_items,result,opinion,audit_time) values(?,?,?,?,?,now())",
                    form.recId(), form.auditorId(), null, passed ? 1 : 2, form.opinion());
        }
        jdbc.update("update edu_recommendation set status=?,exhibit_id=coalesce(?,exhibit_id),review_opinion=?,"
                + "auditor_id=?,audit_time=now(),push_status=1,update_time=now() where rec_id=?",
                passed ? 2 : 3, form.itemId(), form.opinion(), remote ? null : form.auditorId(), form.recId());
        String title = passed ? "成果审核通过" : "成果审核驳回";
        jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time) "
                + "values('NT-10',?,?,?,'rec',?,now())", rec.get("teacherId"), title,
                form.opinion() == null ? "" : form.opinion(), form.recId());
        if (fingerprint != null)
        {
            recordRemoteCallback(form, fingerprint);
            if (!rec.get("teacherId").equals(rec.get("studentId")))
                jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time) "
                        + "values('NT-10',?,?,?,'rec',?,now())", rec.get("studentId"), title,
                        form.opinion() == null ? "" : form.opinion(), form.recId());
        }
        return ResponseEntity.ok(AjaxResult.success(Map.of("ok", true)));
    }

    private void recordRemoteCallback(CallbackForm form, String fingerprint)
    {
        jdbc.update("insert into ops_rec_callback_receipt(rec_id,callback_id,payload_hash,external_auditor_id) values(?,?,?,?)",
                form.recId(), form.callbackId(), fingerprint, form.auditorId());
    }

    private ResponseEntity<AjaxResult> recordExhibitStatus(CallbackForm form, String fingerprint, Map<String, Object> rec, int status)
    {
        if (form.exhibitStatus() == null || !List.of(2, 3).contains(form.exhibitStatus())
                || form.exhibitRevision() == null || form.exhibitRevision() <= 0 || form.itemId() == null)
            return ResponseEntity.badRequest().body(AjaxResult.error(400, "展项状态或版本无效"));
        if (status != 2 || !form.itemId().equals(rec.get("exhibitId")) || !"passed".equals(form.result()))
            return ResponseEntity.status(409).body(AjaxResult.error(606, "当前推荐无法更新展项状态"));
        List<Long> revisions = jdbc.queryForList("select revision from ops_rec_exhibit_state where rec_id=?",
                Long.class, form.recId());
        if (revisions.isEmpty() || form.exhibitRevision() > revisions.get(0))
        {
            jdbc.update("""
                    insert into ops_rec_exhibit_state(rec_id,item_id,exhibit_status,revision) values(?,?,?,?)
                    on duplicate key update exhibit_status=values(exhibit_status),revision=values(revision)
                    """, form.recId(), form.itemId(), form.exhibitStatus(), form.exhibitRevision());
            String title = form.exhibitStatus() == 2 ? "成果展项已发布" : "成果展项已下架";
            jdbc.update("""
                    insert into ops_notification(event_code,receiver_id,title,biz_type,biz_id,send_time)
                    select 'NT-10',receiver,?,'rec',?,now() from (select ? as receiver union select ?) users
                    """, title, form.recId(), rec.get("teacherId"), rec.get("studentId"));
        }
        recordRemoteCallback(form, fingerprint);
        return ResponseEntity.ok(AjaxResult.success(Map.of("ok", true)));
    }

    // --------------------------------------------------------------- OP-02-07 设备鉴权

    @PostMapping("/device/token")
    @Transactional
    public ResponseEntity<AjaxResult> deviceToken(@Valid @RequestBody DeviceTokenForm form)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select device_id as deviceId,status,mode,timestampdiff(second,create_time,now()) as codeAge,"
                + "cache_version as cacheVersion,config_json as config from exh_device "
                + "where device_code=? and del_flag='0' for update", form.deviceCode());
        if (rows.isEmpty()) return ResponseEntity.status(404).body(AjaxResult.error(404, "设备码不存在"));
        Map<String, Object> device = rows.get(0);
        int status = ((Number) device.get("status")).intValue();
        if (status == 1) return ResponseEntity.status(403).body(AjaxResult.error(403, "设备已激活"));
        if (status == 3) return ResponseEntity.status(403).body(AjaxResult.error(403, "设备已被吊销"));
        JSONObject config = device.get("config") == null ? new JSONObject()
                : JSON.parseObject((String) device.get("config"));
        if (!form.activateCode().equals(config.getString("activateCode")))
        {
            return ResponseEntity.status(403).body(AjaxResult.error(403, "激活码不正确"));
        }
        Number codeAge = (Number) device.get("codeAge");
        if (codeAge == null || codeAge.longValue() >= 86400)
        {
            return ResponseEntity.status(403).body(AjaxResult.error(403, "激活码已过期"));
        }
        config.remove("activateCode");
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expireAt = LocalDateTime.now(ZONE).plusYears(1).withNano(0);
        jdbc.update("update exh_device set status=1,token=?,auth_expire=?,activate_time=now(),last_heartbeat=now(),"
                + "config_json=?,update_time=now() where device_id=?", token, expireAt, config.toJSONString(),
                device.get("deviceId"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("expireAt", expireAt.toString().replace('T', ' '));
        data.put("mode", device.get("mode"));
        data.put("cacheVersion", device.get("cacheVersion"));
        return ResponseEntity.ok(AjaxResult.success(data));
    }

    // --------------------------------------------------------------- OP-02-08 案例检索

    @GetMapping("/cases")
    public AjaxResult cases(@RequestParam(required = false) @Size(max = 200) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        return success(caseLibrary.cases(keyword, pageNum, pageSize));
    }

    @GetMapping("/cases/{id}")
    public AjaxResult caseDetail(@PathVariable String id)
    {
        return success(caseLibrary.caseDetail(id));
    }

    // --------------------------------------------------------------- OP-02-09 AI 咨询

    @PostMapping("/ai/consult")
    public AjaxResult consult(@Valid @RequestBody ConsultForm form)
    {
        String base = config("biz.ai.consultUrl", "");
        Map<String, Object> data = new LinkedHashMap<>();
        if (base.isBlank())
        {
            data.put("consultUrl", null);
            data.put("degrade", true);
            data.put("degradeReason", "AI 咨询地址未配置，请先在参数设置中维护 biz.ai.consultUrl");
            return AjaxResult.success(data);
        }
        String traceId = form.traceId() == null || form.traceId().isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : form.traceId();
        LocalDateTime expireAt = LocalDateTime.now(ZONE).plusHours(2).withNano(0);
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? "&" : "?");
        url.append("topic=").append(encode(form.topic()));
        if (form.itemId() != null) url.append("&itemId=").append(form.itemId());
        url.append("&identity=").append(form.identity() == null ? "visitor" : form.identity());
        url.append("&traceId=").append(encode(traceId));
        data.put("consultUrl", url.toString());
        data.put("expireAt", expireAt.toString().replace('T', ' '));
        data.put("traceId", traceId);
        data.put("degrade", false);
        // 只透出必要标识与身份类型，不携带姓名/学号/手机号
        data.put("privacy", "minimal");
        return AjaxResult.success(data);
    }

    // --------------------------------------------------------------- 内部方法

    private Map<String, Object> zone(long zoneId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select zone_id as zoneId,zone_name as zoneName,"
                + "open_level as openLevel,status,route_no as routeNo from exh_zone where zone_id=? and del_flag='0'",
                zoneId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Object parseContent(String content)
    {
        if (content == null || content.isBlank()) return null;
        try
        {
            return JSON.parse(content);
        }
        catch (Exception ignored)
        {
            return Map.of("text", content);
        }
    }

    private Long neighbour(long itemId, Long zoneId, boolean previous)
    {
        List<Long> ids = jdbc.queryForList("select item_id from exh_item where zone_id=? and status=2 "
                + "and view_level>=1 and del_flag='0' and item_id " + (previous ? "<" : ">") + " ? "
                + "order by item_id " + (previous ? "desc" : "asc") + " limit 1", Long.class, zoneId, itemId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<Map<String, Object>> relatedLinks(Long zoneId, long itemId)
    {
        return jdbc.queryForList("select item_id as itemId,title from exh_item where zone_id=? and item_id<>? "
                + "and status=2 and view_level>=1 and del_flag='0' order by item_id limit 5", zoneId, itemId);
    }

    private String offlineReasonType(String reason)
    {
        if (reason == null || reason.isBlank()) return "已下架";
        if (reason.contains("版权")) return "版权投诉";
        if (reason.contains("合规")) return "合规要求";
        return reason.length() > 20 ? reason.substring(0, 20) : reason;
    }

    private String config(String key, String fallback)
    {
        return jdbc.queryForList("select config_value from sys_config where config_key=?", String.class, key)
                .stream().findFirst().filter(value -> !value.isBlank()).orElse(fallback);
    }

    private String nextCode()
    {
        for (int attempt = 0; attempt < 20; attempt++)
        {
            StringBuilder code = new StringBuilder(6);
            for (int i = 0; i < 6; i++)
            {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            Integer exists = jdbc.queryForObject("select count(1) from exh_share_link where short_code=?",
                    Integer.class, code.toString());
            if (exists == null || exists == 0) return code.toString();
        }
        throw new ServiceException("短链生成失败，请重试");
    }

    private String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ResponseEntity<AjaxResult> cache(AjaxResult result, int seconds)
    {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(seconds)).cachePublic())
                .body(result);
    }
}
