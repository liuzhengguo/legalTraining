package com.legaltraining.web.controller.exh;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.common.annotation.Anonymous;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class ExhibitController
{
    private static final ZoneId GALLERY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ZONE_FIELDS = "zone_id as zoneId, zone_code as zoneCode, zone_name as zoneName, "
            + "intro, cover, layout, route_no as routeNo, open_level as openLevel, status";
    private static final String ITEM_FIELDS = "item_id as itemId, zone_id as zoneId, title, cover, summary, "
            + "content, author_name as authorName, view_level as viewLevel, status, version";
    private final JdbcTemplate jdbc;

    public ExhibitController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    public record ZoneForm(@NotBlank @Size(max = 32) String zoneCode, @NotBlank @Size(max = 100) String zoneName,
            @Size(max = 2000) String intro, @Size(max = 500) String cover,
            @NotNull @Pattern(regexp = "grid|flow|timeline") String layout, int routeNo) {}

    public record ItemForm(@NotNull Long zoneId, @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String cover, @Size(max = 120) String summary,
            @Size(max = 50000) String content) {}

    @GetMapping("/exh/zone/list")
    @PreAuthorize("@ss.hasPermi('exh:zone:list')")
    public AjaxResult zones()
    {
        return AjaxResult.success(jdbc.queryForList("select " + ZONE_FIELDS
                + " from exh_zone where del_flag = '0' order by route_no, zone_id"));
    }

    @PostMapping("/exh/zone")
    @PreAuthorize("@ss.hasPermi('exh:zone:list')")
    public AjaxResult createZone(@Valid @RequestBody ZoneForm form)
    {
        jdbc.update("insert into exh_zone(zone_code, zone_name, intro, cover, layout, route_no, create_by, create_time) "
                + "values(?, ?, ?, ?, ?, ?, ?, now())", form.zoneCode(), form.zoneName(), form.intro(),
                mediaUrl(form.cover()), form.layout(), form.routeNo(), SecurityUtils.getUsername());
        return AjaxResult.success();
    }

    @PutMapping("/exh/zone/{zoneId}")
    @PreAuthorize("@ss.hasPermi('exh:zone:list')")
    public AjaxResult editZone(@PathVariable long zoneId, @Valid @RequestBody ZoneForm form)
    {
        requireUpdated(jdbc.update("update exh_zone set zone_code=?, zone_name=?, intro=?, cover=?, layout=?, "
                + "route_no=?, update_time=now() where zone_id=? and del_flag='0'",
                form.zoneCode(), form.zoneName(), form.intro(), mediaUrl(form.cover()), form.layout(), form.routeNo(), zoneId));
        return AjaxResult.success();
    }

    @PutMapping("/exh/zone/{zoneId}/level")
    @PreAuthorize("@businessScope.hasAnyRole('registrar')")
    public AjaxResult zoneLevel(@PathVariable long zoneId, @RequestParam int level)
    {
        if (level < 0 || level > 2) throw new ServiceException("展区开放级别无效");
        requireUpdated(jdbc.update("update exh_zone set open_level=?, status=?, update_time=now() "
                + "where zone_id=? and del_flag='0'", level, level == 0 ? 0 : 1, zoneId));
        return AjaxResult.success();
    }

    @GetMapping("/exh/item/list")
    @PreAuthorize("@ss.hasAnyPermi('exh:item:list,exh:publish:list')")
    public AjaxResult items()
    {
        return AjaxResult.success(jdbc.queryForList("select " + ITEM_FIELDS + ", source_rec as sourceRec"
                + " from exh_item where del_flag='0' order by item_id desc"));
    }

    @GetMapping("/exh/item/{itemId}/source")
    @PreAuthorize("@ss.hasAnyPermi('exh:item:list,exh:publish:list')")
    public AjaxResult itemSource(@PathVariable long itemId)
    {
        return AjaxResult.success(requireRow(jdbc.queryForList(
                "select r.rec_id as recId,t.title as taskTitle,r.reason,r.sign_mode as signMode,"
                + "r.scope_desc as scopeDesc,r.review_opinion as opinion,i.status as exhibitStatus "
                + "from exh_item i join edu_recommendation r on r.rec_id=i.source_rec "
                + "join edu_submission s on s.submission_id=r.submission_id "
                + "join edu_task t on t.task_id=s.task_id where i.item_id=? and i.del_flag='0'", itemId)));
    }

    @PostMapping("/exh/item")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    public AjaxResult createItem(@Valid @RequestBody ItemForm form)
    {
        zone(form.zoneId(), false);
        jdbc.update("insert into exh_item(zone_id,title,cover,summary,content,editor_id,create_by,create_time) "
                + "values(?,?,?,?,?,?,?,now())", form.zoneId(), form.title(), mediaUrl(form.cover()), form.summary(),
                form.content(), SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return AjaxResult.success();
    }

    @PutMapping("/exh/item/{itemId}")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    public AjaxResult editItem(@PathVariable long itemId, @Valid @RequestBody ItemForm form, @RequestParam int version)
    {
        zone(form.zoneId(), false);
        requireUpdated(jdbc.update("update exh_item set zone_id=?, title=?, cover=?, summary=?, content=?, editor_id=?, "
                + "update_time=now(), version=version+1 where item_id=? and version=? and del_flag='0'",
                form.zoneId(), form.title(), mediaUrl(form.cover()), form.summary(), form.content(),
                SecurityUtils.getUserId(), itemId, version));
        return AjaxResult.success();
    }

    @PostMapping("/exh/item/{itemId}/publish")
    @PreAuthorize("@ss.hasPermi('exh:publish:list')")
    @Transactional
    public AjaxResult publish(@PathVariable long itemId, @RequestParam int level)
    {
        Map<String, Object> item = item(itemId, true);
        publishSnapshot(itemId, item, level, null);
        cancelPending(itemId, "展项已手动发布");
        return AjaxResult.success();
    }

    @PostMapping("/exh/item/{itemId}/schedule")
    @PreAuthorize("@ss.hasPermi('exh:publish:list')")
    @Transactional
    public AjaxResult schedule(@PathVariable long itemId, @RequestParam int level,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime planTime)
    {
        if (planTime.isBefore(LocalDateTime.now(GALLERY_ZONE).plusMinutes(5)))
        {
            throw new ServiceException("计划发布时间须至少晚于当前时间五分钟");
        }
        Map<String, Object> item = item(itemId, true);
        validatePublication(item, level);
        jdbc.update("insert into exh_publish_job(item_id,plan_time,view_level,item_version,operator_id,create_time) "
                + "values(?,?,?,?,?,now()) as pending on duplicate key update plan_time=pending.plan_time, "
                + "view_level=pending.view_level,item_version=pending.item_version,operator_id=pending.operator_id, "
                + "status=0,retry_count=0,fail_reason=null,execute_time=null,create_time=now()",
                itemId, planTime.atZone(GALLERY_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                level, item.get("version"), SecurityUtils.getUserId());
        return AjaxResult.success();
    }

    @GetMapping("/exh/item/{itemId}/schedule")
    @PreAuthorize("@ss.hasPermi('exh:publish:list')")
    public AjaxResult scheduleStatus(@PathVariable long itemId)
    {
        return AjaxResult.success(jdbc.queryForList("select date_format(timestampadd(hour,8,plan_time),"
                + "'%Y-%m-%d %H:%i:%s') as planTime,status,retry_count as retryCount,"
                + "fail_reason as failReason from exh_publish_job where item_id=?", itemId));
    }

    @DeleteMapping("/exh/item/{itemId}/schedule")
    @PreAuthorize("@ss.hasPermi('exh:publish:list')")
    @Transactional
    public AjaxResult cancelSchedule(@PathVariable long itemId)
    {
        item(itemId, true);
        cancelPending(itemId, "内容编辑取消计划");
        return AjaxResult.success();
    }

    @GetMapping("/exh/item/{itemId}/versions")
    @PreAuthorize("@ss.hasPermi('exh:publish:list')")
    public AjaxResult versions(@PathVariable long itemId)
    {
        return AjaxResult.success(jdbc.queryForList("select version, snapshot, source_version as sourceVersion, "
                + "operate_time as operateTime from exh_item_version where item_id=? order by version desc", itemId));
    }

    @PostMapping("/exh/item/{itemId}/revert")
    @PreAuthorize("@ss.hasPermi('exh:publish:list')")
    @Transactional
    public AjaxResult revert(@PathVariable long itemId, @RequestParam int version)
    {
        Map<String, Object> current = item(itemId, true);
        List<String> versions = jdbc.queryForList("select snapshot from exh_item_version where item_id=? and version=?",
                String.class, itemId, version);
        if (versions.isEmpty()) throw new ServiceException("发布版本不存在");
        Map<String, Object> snapshot = JSON.parseObject(versions.get(0));
        int level = ((Number) snapshot.get("viewLevel")).intValue();
        snapshot.put("version", current.get("version"));
        snapshot.put("status", current.get("status"));
        publishSnapshot(itemId, snapshot, level, version);
        cancelPending(itemId, "展项已回退并发布");
        jdbc.update("update exh_item set zone_id=?, title=?, cover=?, summary=?, content=? where item_id=?",
                snapshot.get("zoneId"), snapshot.get("title"), snapshot.get("cover"), snapshot.get("summary"),
                snapshot.get("content"), itemId);
        return AjaxResult.success();
    }

    @PostMapping("/exh/item/{itemId}/offline")
    @PreAuthorize("@ss.hasPermi('exh:publish:list')")
    @Transactional
    public AjaxResult offline(@PathVariable long itemId, @RequestParam @NotBlank @Size(max = 500) String reason)
    {
        requireUpdated(jdbc.update("update exh_item set status=3, offline_time=now(), offline_reason=?, "
                + "update_time=now() where item_id=? and status=2 and del_flag='0'", reason, itemId));
        cancelPending(itemId, "展项已下架");
        notifyAchievement(itemId, "成果展项已下架");
        return AjaxResult.success();
    }

    @Anonymous
    @GetMapping("/gallery/v1/zones")
    public ResponseEntity<AjaxResult> galleryZones()
    {
        return noCache(AjaxResult.success(jdbc.queryForList("select " + ZONE_FIELDS
                + " from exh_zone where status=1 and open_level=2 and del_flag='0' order by route_no, zone_id")));
    }

    @Anonymous
    @GetMapping("/gallery/v1/items")
    public ResponseEntity<AjaxResult> galleryItems()
    {
        List<String> snapshots = jdbc.queryForList("select v.snapshot from exh_item i join exh_item_version v "
                + "on v.item_id=i.item_id and v.version=(select max(p.version) from exh_item_version p "
                + "where p.item_id=i.item_id and p.edit_type in (1,2)) "
                + "join exh_zone z on z.zone_id=cast(json_unquote(json_extract(v.snapshot,'$.zoneId')) as unsigned) "
                + "where i.status=2 and i.del_flag='0' and i.view_level=2 "
                + "and z.status=1 and z.open_level=2 and z.del_flag='0' order by z.route_no,i.item_id", String.class);
        List<Map<String, Object>> cards = snapshots.stream().map(snapshot -> {
            Map<String, Object> card = JSON.parseObject(snapshot);
            card.remove("content");
            return card;
        }).toList();
        return noCache(AjaxResult.success(cards));
    }

    @Anonymous
    @GetMapping("/gallery/v1/items/{itemId}")
    public ResponseEntity<AjaxResult> galleryItem(@PathVariable long itemId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select i.status,i.view_level, "
                + "case when z.status=1 and z.open_level=2 and z.del_flag='0' then v.snapshot end as snapshot "
                + "from exh_item i left join exh_item_version v on v.item_id=i.item_id "
                + "and v.version=(select max(p.version) from exh_item_version p where p.item_id=i.item_id and p.edit_type in (1,2)) "
                + "left join exh_zone z on z.zone_id=cast(json_unquote(json_extract(v.snapshot,'$.zoneId')) as unsigned) "
                + "where i.item_id=? and i.del_flag='0'", itemId);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> item = rows.get(0);
        int status = ((Number) item.get("status")).intValue();
        if (status == 3 && ((Number) item.get("view_level")).intValue() == 2)
        {
            return ResponseEntity.status(410).cacheControl(CacheControl.noStore()).build();
        }
        String snapshot = (String) item.get("snapshot");
        if (status != 2 || ((Number) item.get("view_level")).intValue() != 2 || snapshot == null)
        {
            return ResponseEntity.notFound().build();
        }
        return noCache(AjaxResult.success(JSON.parseObject(snapshot)));
    }

    private void publishSnapshot(long itemId, Map<String, Object> item, int level, Integer sourceVersion)
    {
        validatePublication(item, level);
        persistPublication(itemId, item, level, sourceVersion, SecurityUtils.getUserId());
    }

    private void validatePublication(Map<String, Object> item, int level)
    {
        if (level != 1 && level != 2) throw new ServiceException("发布级别无效");
        List<Map<String, Object>> rights = jdbc.queryForList("select i.source_rec,r.status,r.scope_desc "
                + "from exh_item i left join edu_recommendation r on r.rec_id=i.source_rec where i.item_id=?", item.get("itemId"));
        if (!rights.isEmpty() && rights.get(0).get("source_rec") != null)
        {
            Map<String, Object> authorization = rights.get(0);
            if (!(authorization.get("status") instanceof Number status) || status.intValue() != 5
                    || (level == 2 && !"含对外分享".equals(authorization.get("scope_desc"))))
            {
                throw new ServiceException("学生授权状态或范围不允许所选发布级别");
            }
        }
        Map<String, Object> zone = zone(((Number) item.get("zoneId")).longValue(), true);
        if (((Number) zone.get("openLevel")).intValue() < level || ((Number) zone.get("status")).intValue() != 1)
        {
            throw new ServiceException("展区未开放到所选级别");
        }
        String summary = (String) item.get("summary");
        String content = (String) item.get("content");
        if (summary == null || summary.trim().length() < 20 || summary.trim().length() > 120
                || content == null || content.isBlank() || mediaUrl((String) item.get("cover")) == null)
        {
            throw new ServiceException("请补齐封面、20 至 120 字摘要和正文后再发布");
        }
    }

    private void persistPublication(long itemId, Map<String, Object> item, int level, Integer sourceVersion, Long operatorId)
    {
        boolean newlyVisible = ((Number) item.get("status")).intValue() != 2;
        int next = ((Number) item.get("version")).intValue() + 1;
        item.put("version", next);
        item.put("viewLevel", level);
        item.put("status", 2);
        jdbc.update("insert into exh_item_version(item_id,version,snapshot,edit_type,source_version,operator_id,operate_time) "
                + "values(?,?,?,?,?,?,now())", itemId, next, JSON.toJSONString(item),
                sourceVersion == null ? 1 : 2, sourceVersion, operatorId);
        jdbc.update("update exh_item set status=2, view_level=?, version=?, publish_time=now(), "
                + "offline_time=null, offline_reason=null, update_time=now() where item_id=?", level, next, itemId);
        if (newlyVisible) notifyAchievement(itemId, "成果展项已发布");
    }

    private void notifyAchievement(long itemId, String title)
    {
        jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time) "
                + "select 'NT-10',a.user_id,?,i.title,'rec',r.rec_id,now() "
                + "from exh_item i join edu_recommendation r on r.rec_id=i.source_rec "
                + "join edu_authorization a on a.rec_id=r.rec_id and a.status=1 where i.item_id=?", title, itemId);
    }

    @Transactional
    public void executeSchedule(long itemId)
    {
        Map<String, Object> item = item(itemId, true);
        List<Map<String, Object>> due = jdbc.queryForList("select view_level,item_version,operator_id "
                + "from exh_publish_job where item_id=? and status=0 and plan_time<=utc_timestamp() for update", itemId);
        if (due.isEmpty()) return;
        Map<String, Object> job = due.get(0);
        if (((Number) item.get("version")).intValue() != ((Number) job.get("item_version")).intValue())
        {
            cancelPending(itemId, "展项编辑版本已变化");
            return;
        }
        int level = ((Number) job.get("view_level")).intValue();
        validatePublication(item, level);
        Number operator = (Number) job.get("operator_id");
        persistPublication(itemId, item, level, null, operator == null ? null : operator.longValue());
        jdbc.update("update exh_publish_job set status=1,execute_time=now(),fail_reason=null where item_id=?", itemId);
    }

    private void cancelPending(long itemId, String reason)
    {
        jdbc.update("update exh_publish_job set status=3,fail_reason=?,execute_time=now() where item_id=? and status=0",
                reason, itemId);
    }

    private Map<String, Object> zone(long zoneId, boolean lock)
    {
        return requireRow(jdbc.queryForList("select " + ZONE_FIELDS + " from exh_zone "
                + "where zone_id=? and del_flag='0'" + (lock ? " for update" : ""), zoneId));
    }

    private Map<String, Object> item(long itemId, boolean lock)
    {
        return requireRow(jdbc.queryForList("select " + ITEM_FIELDS + " from exh_item "
                + "where item_id=? and del_flag='0'" + (lock ? " for update" : ""), itemId));
    }

    private Map<String, Object> requireRow(List<Map<String, Object>> rows)
    {
        if (rows.isEmpty()) throw new ServiceException("记录不存在");
        return rows.get(0);
    }

    private void requireUpdated(int changed)
    {
        if (changed == 0) throw new ServiceException("记录不存在或已变化，请刷新后重试");
    }

    private String mediaUrl(String value)
    {
        if (value == null || value.isBlank()) return null;
        if (value.matches("/profile/upload/[A-Za-z0-9_./-]+") && !value.contains("..")) return value;
        try
        {
            URI uri = URI.create(value);
            if (uri.getHost() != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null) return value;
        }
        catch (IllegalArgumentException ignored)
        {
            // Return a field-level business error for invalid input.
        }
        throw new ServiceException("封面须为平台上传文件或 HTTPS 地址");
    }

    private ResponseEntity<AjaxResult> noCache(AjaxResult result)
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
