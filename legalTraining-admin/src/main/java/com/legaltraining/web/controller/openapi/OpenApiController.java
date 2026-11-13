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

    public OpenApiController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    public record ShareForm(@NotBlank @Pattern(regexp = "zone|item") String targetType,
            @NotNull Long targetId,
            @NotBlank @Pattern(regexp = "qrcode|link|terminal|portal") String channel) {}
    public record PushForm(@NotNull Long recId, Long submissionId, Long teacherId,
            @Size(max = 1000) String reason, Long targetZone, String signMode,
            @Size(max = 16) String level,
            @Pattern(regexp = "online|offline") String authProofType,
            String confirmedAt) {}
    public record CallbackForm(@NotNull Long recId, @NotBlank @Pattern(regexp = "passed|rejected") String result,
            Long itemId, @Size(max = 1000) String opinion, Long auditorId, String auditTime) {}
    public record DeviceTokenForm(@NotBlank @Size(max = 64) String deviceCode,
            @NotBlank @Size(max = 16) String activateCode) {}
    public record ConsultForm(@NotBlank @Size(max = 100) String topic, Long itemId,
            @Pattern(regexp = "student|teacher|visitor") String identity, @Size(max = 64) String traceId) {}

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
            @Valid @RequestBody PushForm form)
    {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && !idempotencyKey.equals(String.valueOf(form.recId())))
        {
            return ResponseEntity.status(400).body(AjaxResult.error(400, "幂等键与 recId 不一致"));
        }
        List<Map<String, Object>> rows = jdbc.queryForList("select rec_id as recId,status,push_status as pushStatus "
                + "from edu_recommendation where rec_id=? for update", form.recId());
        if (rows.isEmpty()) return ResponseEntity.status(404).body(AjaxResult.error(404, "推荐记录不存在"));
        Map<String, Object> rec = rows.get(0);
        int pushStatus = ((Number) rec.get("pushStatus")).intValue();
        if (pushStatus == 1)
        {
            return ResponseEntity.status(409).body(AjaxResult.error(604, "已接收过该推荐"));
        }
        int status = ((Number) rec.get("status")).intValue();
        if (status == 0 || status == 4)
        {
            return ResponseEntity.status(409).body(AjaxResult.error(605, "推荐尚未获得学生授权或已撤回"));
        }
        jdbc.update("update edu_recommendation set push_status=1,push_retry=0,target_zone=coalesce(?,target_zone),"
                + "sign_mode=coalesce(?,sign_mode),level_code=coalesce(?,level_code),update_time=now() where rec_id=?",
                form.targetZone(), form.signMode(), form.level(), form.recId());
        jdbc.update("insert into ops_call_log(app_id,api_code,request_uri,method,req_id,http_status,call_time) "
                + "values(null,'OP-02-05','/openapi/v1/rec/push','POST',?,200,now())", String.valueOf(form.recId()));
        return ResponseEntity.ok(AjaxResult.success(Map.of("accepted", true, "recId", form.recId())));
    }

    // --------------------------------------------------------------- OP-02-06 审核回写

    @PostMapping("/rec/callback")
    @Transactional
    public ResponseEntity<AjaxResult> callback(@Valid @RequestBody CallbackForm form)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select r.rec_id as recId,r.status,r.teacher_id as teacherId,"
                + "s.user_id as studentId,r.exhibit_id as exhibitId from edu_recommendation r "
                + "join edu_submission s on s.submission_id=r.submission_id where r.rec_id=? for update", form.recId());
        if (rows.isEmpty()) return ResponseEntity.status(404).body(AjaxResult.error(404, "推荐记录不存在"));
        Map<String, Object> rec = rows.get(0);
        int status = ((Number) rec.get("status")).intValue();
        if (status == 2 || status == 3)
        {
            return ResponseEntity.status(409).body(AjaxResult.error(606, "该推荐已回写过审核结果"));
        }
        boolean passed = "passed".equals(form.result());
        if (!passed && (form.opinion() == null || form.opinion().isBlank()))
        {
            return ResponseEntity.status(400).body(AjaxResult.error(400, "驳回须填写审核意见"));
        }
        jdbc.update("insert into exh_review(rec_id,auditor_id,check_items,result,opinion,audit_time) values(?,?,?,?,?,now())",
                form.recId(), form.auditorId(), null, passed ? 1 : 2, form.opinion());
        jdbc.update("update edu_recommendation set status=?,exhibit_id=coalesce(?,exhibit_id),review_opinion=?,"
                + "auditor_id=?,audit_time=now(),push_status=1,update_time=now() where rec_id=?",
                passed ? 2 : 3, form.itemId(), form.opinion(), form.auditorId(), form.recId());
        String title = passed ? "成果审核通过" : "成果审核驳回";
        jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time) "
                + "values('NT-10',?,?,?,'rec',?,now())", rec.get("teacherId"), title,
                form.opinion() == null ? "" : form.opinion(), form.recId());
        return ResponseEntity.ok(AjaxResult.success(Map.of("ok", true)));
    }

    // --------------------------------------------------------------- OP-02-07 设备鉴权

    @PostMapping("/device/token")
    @Transactional
    public ResponseEntity<AjaxResult> deviceToken(@Valid @RequestBody DeviceTokenForm form)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select device_id as deviceId,status,mode,"
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
    public AjaxResult cases(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNum", safePageNum);
        data.put("pageSize", safePageSize);
        data.put("keyword", keyword);
        List<Map<String, Object>> rows = new ArrayList<>();
        // 外部案例库按配置接入；未配置时降级为空气集，前端隐藏案例区（OP-02-08）
        String caseUrl = config("biz.openapi.caseUrl", "");
        boolean degrade = caseUrl.isBlank();
        data.put("degrade", degrade);
        data.put("total", 0);
        data.put("rows", rows);
        if (degrade)
        {
            data.put("degradeReason", "外部案例库未配置");
        }
        return AjaxResult.success(data);
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
