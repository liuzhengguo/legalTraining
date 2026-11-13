package com.legaltraining.web.controller.exh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 展厅内容编辑：动线排序、热点、展项详情、编辑锁、自动保存、素材、四视角预览、发布前校验。
 *
 * 对应功能点 EM-01-02 / EM-01-03 / EM-02-02 / EM-02-03 / EM-02-04 / EM-02-05 / EM-03-01。
 */
@RestController
public class ExhibitContentController extends BaseController
{
    /** 编辑锁租约：10 分钟无心跳自动释放（EM-02-04）。 */
    private static final int LOCK_LEASE_MINUTES = 10;
    private static final List<String> VIEWS = List.of("pc", "mobile", "screen", "lite");
    private static final String ITEM_FIELDS = "item_id as itemId, zone_id as zoneId, item_code as itemCode, title, cover, "
            + "summary, content, item_type as itemType, author_name as authorName, source_rec as sourceRec, "
            + "view_level as viewLevel, status, version, lock_user as lockUser, "
            + "date_format(lock_time,'%Y-%m-%d %H:%i:%s') as lockTime, "
            + "date_format(publish_time,'%Y-%m-%d %H:%i:%s') as publishTime";
    private final JdbcTemplate jdbc;

    public ExhibitContentController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    public record RouteOrder(@NotNull Long zoneId, @NotNull Integer routeNo) {}
    public record SpotForm(@Size(max = 100) String spotName,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal posX,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal posY,
            @Size(max = 255) String icon,
            @NotBlank @Pattern(regexp = "zone|item") String targetType,
            @NotNull Long targetId, Integer sortNo) {}
    public record LockForm(@NotBlank @Pattern(regexp = "acquire|release|renew") String action) {}
    public record DraftForm(@Size(max = 200) String title, @Size(max = 120) String summary,
            @Size(max = 50000) String content, @Size(max = 500) String cover, Long zoneId,
            @Pattern(regexp = "0|1|2|3") String itemType) {}
    public record MediaForm(@NotBlank @Pattern(regexp = "image|video|audio|link") String mediaType,
            @NotBlank @Size(max = 500) String url, @Size(max = 500) String cover,
            Integer duration, Long fileSize) {}

    // ------------------------------------------------------------------ 展区动线 / 热点

    @PutMapping("/exh/zone/sort")
    @PreAuthorize("@ss.hasPermi('exh:zone:list')")
    @Transactional
    public AjaxResult sortZones(@RequestBody @NotEmpty List<RouteOrder> orders)
    {
        for (RouteOrder order : orders)
        {
            jdbc.update("update exh_zone set route_no=?, update_time=now() where zone_id=? and del_flag='0'",
                    order.routeNo(), order.zoneId());
        }
        return success(jdbc.queryForList("select zone_id as zoneId,route_no as routeNo from exh_zone "
                + "where del_flag='0' order by route_no, zone_id"));
    }

    @GetMapping("/exh/zone/{zoneId}/spots")
    @PreAuthorize("@ss.hasPermi('exh:zone:list')")
    public AjaxResult spots(@PathVariable long zoneId)
    {
        zone(zoneId);
        return success(spotsOf(zoneId));
    }

    @PutMapping("/exh/zone/{zoneId}/spots")
    @PreAuthorize("@ss.hasPermi('exh:zone:list')")
    @Transactional
    public AjaxResult saveSpots(@PathVariable long zoneId, @RequestBody @NotEmpty List<@Valid SpotForm> forms)
    {
        zone(zoneId);
        for (SpotForm form : forms)
        {
            boolean exists = "zone".equals(form.targetType())
                    ? count("select count(1) from exh_zone where zone_id=? and del_flag='0'", form.targetId()) > 0
                    : count("select count(1) from exh_item where item_id=? and del_flag='0'", form.targetId()) > 0;
            if (!exists) throw new ServiceException("热点指向的" + ("zone".equals(form.targetType()) ? "展区" : "展项") + "不存在");
        }
        jdbc.update("delete from exh_zone_spot where zone_id=?", zoneId);
        int index = 0;
        for (SpotForm form : forms)
        {
            jdbc.update("insert into exh_zone_spot(zone_id,spot_name,pos_x,pos_y,icon,target_type,target_id,sort_no) "
                    + "values(?,?,?,?,?,?,?,?)", zoneId, form.spotName(), form.posX(), form.posY(), form.icon(),
                    form.targetType(), form.targetId(), form.sortNo() == null ? index : form.sortNo());
            index++;
        }
        jdbc.update("update exh_zone set update_time=now() where zone_id=?", zoneId);
        return success(spotsOf(zoneId));
    }

    // ------------------------------------------------------------------ 展项详情 / 编辑锁

    @GetMapping("/exh/item/{itemId}")
    @PreAuthorize("@ss.hasAnyPermi('exh:item:list,exh:publish:list')")
    public AjaxResult itemDetail(@PathVariable long itemId)
    {
        Map<String, Object> item = item(itemId);
        Map<String, Object> zone = zone(((Number) item.get("zoneId")).longValue());
        Map<String, Object> holder = lockHolder(itemId);
        Map<String, Object> result = new LinkedHashMap<>(item);
        result.put("zone", zone);
        result.put("media", media(itemId));
        result.put("lock", holder);
        result.put("editable", !Boolean.TRUE.equals(holder.get("held")));
        result.put("versions", count("select count(1) from exh_item_version where item_id=?", itemId));
        return success(result);
    }

    @PutMapping("/exh/item/{itemId}/lock")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    public AjaxResult lock(@PathVariable long itemId, @Valid @RequestBody LockForm form)
    {
        item(itemId);
        long userId = SecurityUtils.getUserId();
        String action = form.action();
        if ("release".equals(action))
        {
            jdbc.update("update exh_item set lock_user=null, lock_time=null where item_id=? and lock_user=?", itemId, userId);
            return success(Map.of("held", false, "holder", ""));
        }
        if ("renew".equals(action))
        {
            int changed = jdbc.update("update exh_item set lock_time=now() where item_id=? and lock_user=?", itemId, userId);
            if (changed == 0) throw new ServiceException("编辑锁已失效，请重新获取");
            return success(lockHolder(itemId));
        }
        int changed = jdbc.update("update exh_item set lock_user=?, lock_time=now() where item_id=? and (lock_user is null "
                + "or lock_user=? or lock_time is null or lock_time < date_sub(now(), interval ? minute))",
                userId, itemId, userId, LOCK_LEASE_MINUTES);
        if (changed == 0)
        {
            Map<String, Object> holder = lockHolder(itemId);
            throw new ServiceException("展项正由 " + holder.getOrDefault("holder", "其他编辑") + " 编辑，请稍后再试");
        }
        return success(lockHolder(itemId));
    }

    @PostMapping("/exh/item/{itemId}/autosave")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    public AjaxResult autosave(@PathVariable long itemId, @Valid @RequestBody DraftForm form)
    {
        Map<String, Object> item = item(itemId);
        long userId = SecurityUtils.getUserId();
        Object lockUser = item.get("lockUser");
        if (lockUser != null && ((Number) lockUser).longValue() != userId)
        {
            throw new ServiceException("展项正由他人编辑，草稿未保存");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("update exh_item set update_time=now(), editor_id=?");
        args.add(userId);
        if (form.title() != null && !form.title().isBlank())
        {
            sql.append(", title=?");
            args.add(form.title());
        }
        if (form.summary() != null)
        {
            sql.append(", summary=?");
            args.add(form.summary());
        }
        if (form.content() != null)
        {
            sql.append(", content=?");
            args.add(form.content());
        }
        if (form.cover() != null)
        {
            sql.append(", cover=?");
            args.add(form.cover());
        }
        if (form.zoneId() != null)
        {
            zone(form.zoneId());
            sql.append(", zone_id=?");
            args.add(form.zoneId());
        }
        if (form.itemType() != null)
        {
            sql.append(", item_type=?");
            args.add(Integer.parseInt(form.itemType()));
        }
        sql.append(", lock_user=?, lock_time=now() where item_id=? and del_flag='0'");
        args.add(userId);
        args.add(itemId);
        if (jdbc.update(sql.toString(), args.toArray()) == 0) throw new ServiceException("展项不存在或已删除");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("savedAt", jdbc.queryForObject("select date_format(now(),'%Y-%m-%d %H:%i:%s')", String.class));
        result.put("version", item.get("version"));
        result.put("draft", true);
        return success(result);
    }

    // ------------------------------------------------------------------ 素材

    @PostMapping("/exh/item/{itemId}/media")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    public AjaxResult addMedia(@PathVariable long itemId, @Valid @RequestBody MediaForm form)
    {
        Map<String, Object> item = item(itemId);
        long userId = SecurityUtils.getUserId();
        Object lockUser = item.get("lockUser");
        if (lockUser != null && ((Number) lockUser).longValue() != userId)
        {
            throw new ServiceException("展项正由他人编辑，素材未保存");
        }
        int sortNo = count("select coalesce(max(sort_no),0)+1 from exh_item_media where item_id=? and status='0'", itemId);
        jdbc.update("insert into exh_item_media(item_id,media_type,url,cover,duration,file_size,transcode_status,sort_no) "
                + "values(?,?,?,?,?,?,?,?)", itemId, form.mediaType(), form.url(), form.cover(), form.duration(),
                form.fileSize(), "video".equals(form.mediaType()) || "audio".equals(form.mediaType()) ? 1 : 0, sortNo);
        jdbc.update("update exh_item set update_time=now() where item_id=?", itemId);
        return success(media(itemId));
    }

    @PutMapping("/exh/item/{itemId}/media/sort")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    @Transactional
    public AjaxResult sortMedia(@PathVariable long itemId, @RequestBody @NotEmpty List<Long> mediaIds)
    {
        item(itemId);
        int index = 0;
        for (Long mediaId : mediaIds)
        {
            int changed = jdbc.update("update exh_item_media set sort_no=? where media_id=? and item_id=? and status='0'",
                    index, mediaId, itemId);
            if (changed == 0) throw new ServiceException("素材 " + mediaId + " 不属于该展项");
            index++;
        }
        jdbc.update("update exh_item set update_time=now() where item_id=?", itemId);
        return success(media(itemId));
    }

    @DeleteMapping("/exh/item/{itemId}/media/{mediaId}")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    public AjaxResult removeMedia(@PathVariable long itemId, @PathVariable long mediaId)
    {
        item(itemId);
        int changed = jdbc.update("update exh_item_media set status='1' where media_id=? and item_id=?", mediaId, itemId);
        if (changed == 0) throw new ServiceException("素材不存在");
        jdbc.update("update exh_item set update_time=now() where item_id=?", itemId);
        return success(media(itemId));
    }

    // ------------------------------------------------------------------ 预览 / 发布前校验

    @GetMapping("/exh/item/{itemId}/preview")
    @PreAuthorize("@ss.hasAnyPermi('exh:item:list,exh:publish:list')")
    public AjaxResult preview(@PathVariable long itemId, @RequestParam(defaultValue = "pc") String view)
    {
        if (!VIEWS.contains(view)) throw new ServiceException("预览视角仅支持 pc/mobile/screen/lite");
        Map<String, Object> item = item(itemId);
        Map<String, Object> result = new LinkedHashMap<>(item);
        result.put("zone", zone(((Number) item.get("zoneId")).longValue()));
        result.put("media", media(itemId));
        result.put("view", view);
        result.put("viewport", viewport(view));
        result.put("availableViews", VIEWS);
        result.put("warnings", problems(item, null, true));
        return success(result);
    }

    @PostMapping("/exh/item/{itemId}/validate")
    @PreAuthorize("@ss.hasAnyPermi('exh:item:list,exh:publish:list')")
    public AjaxResult validate(@PathVariable long itemId, @RequestParam(defaultValue = "2") int level)
    {
        if (level != 1 && level != 2) throw new ServiceException("发布级别无效");
        Map<String, Object> item = item(itemId);
        List<String> problems = problems(item, level, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", problems.isEmpty());
        result.put("level", level);
        result.put("problems", problems);
        result.put("warnings", problems(item, null, true));
        return success(result);
    }

    // ------------------------------------------------------------------ 内部方法

    private List<Map<String, Object>> spotsOf(long zoneId)
    {
        return jdbc.queryForList("select spot_id as spotId,zone_id as zoneId,spot_name as spotName,pos_x as posX,"
                + "pos_y as posY,icon,target_type as targetType,target_id as targetId,sort_no as sortNo "
                + "from exh_zone_spot where zone_id=? order by sort_no, spot_id", zoneId);
    }

    private List<Map<String, Object>> media(long itemId)
    {
        return jdbc.queryForList("select media_id as mediaId,media_type as mediaType,url,cover,duration,"
                + "file_size as fileSize,transcode_status as transcodeStatus,sort_no as sortNo "
                + "from exh_item_media where item_id=? and status='0' order by sort_no, media_id", itemId);
    }

    private Map<String, Object> lockHolder(long itemId)
    {
        Map<String, Object> row = jdbc.queryForMap("select i.lock_user as lockUser,u.nick_name as nickName,"
                + "date_format(i.lock_time,'%Y-%m-%d %H:%i:%s') as lockTime,"
                + "case when i.lock_time is null then 0 else timestampdiff(second,i.lock_time,now()) end as ageSeconds "
                + "from exh_item i left join sys_user u on u.user_id=i.lock_user where i.item_id=?", itemId);
        Map<String, Object> result = new LinkedHashMap<>();
        Object lockUser = row.get("lockUser");
        boolean expired = lockUser != null && row.get("ageSeconds") instanceof Number age
                && age.longValue() > LOCK_LEASE_MINUTES * 60L;
        if (lockUser == null || expired)
        {
            result.put("held", false);
            result.put("holder", "");
            return result;
        }
        result.put("held", true);
        result.put("lockUser", lockUser);
        result.put("holder", row.get("nickName") == null ? "用户" + lockUser : row.get("nickName"));
        result.put("lockTime", row.get("lockTime"));
        result.put("leaseSeconds", LOCK_LEASE_MINUTES * 60L - ((Number) row.get("ageSeconds")).longValue());
        return result;
    }

    private Map<String, Object> viewport(String view)
    {
        return switch (view)
        {
            case "mobile" -> Map.of("width", 390, "height", 844, "scale", 1, "mode", "immersive");
            case "screen" -> Map.of("width", 1920, "height", 1080, "scale", 1, "mode", "immersive");
            case "lite" -> Map.of("width", 375, "height", 667, "scale", 1, "mode", "lite");
            default -> Map.of("width", 1440, "height", 900, "scale", 1, "mode", "immersive");
        };
    }

    /**
     * 发布前校验项（EM-03-01）：级别为空时只做通用检查，返回全部告警。
     */
    private List<String> problems(Map<String, Object> item, Integer level, boolean warningsOnly)
    {
        List<String> problems = new ArrayList<>();
        String summary = (String) item.get("summary");
        String content = (String) item.get("content");
        String cover = (String) item.get("cover");
        if (item.get("title") == null || ((String) item.get("title")).isBlank()) problems.add("标题未填写");
        if (summary == null || summary.trim().length() < 20 || summary.trim().length() > 120)
        {
            problems.add("摘要须为 20—120 字，当前 " + (summary == null ? 0 : summary.trim().length()) + " 字");
        }
        if (content == null || content.isBlank()) problems.add("正文内容为空");
        if (cover == null || cover.isBlank()) problems.add("封面未上传");
        if (media(((Number) item.get("itemId")).longValue()).isEmpty()) problems.add("未上传任何素材");
        Object sourceRec = item.get("sourceRec");
        if (sourceRec instanceof Number recId)
        {
            List<Map<String, Object>> recs = jdbc.queryForList("select status,scope_desc from edu_recommendation "
                    + "where rec_id=?", recId.longValue());
            if (!recs.isEmpty())
            {
                Map<String, Object> rec = recs.get(0);
                boolean authorized = rec.get("status") instanceof Number status && status.intValue() == 5;
                if (!authorized) problems.add("来源成果尚未完成授权，不能发布");
                if (level != null && level == 2 && !"含对外分享".equals(rec.get("scope_desc")))
                {
                    problems.add("学生授权范围为“仅展厅内”，不能对外发布");
                }
            }
        }
        if (level != null)
        {
            Map<String, Object> zone = zone(((Number) item.get("zoneId")).longValue());
            int openLevel = ((Number) zone.get("openLevel")).intValue();
            if (((Number) zone.get("status")).intValue() != 1 || openLevel < level)
            {
                problems.add("所属展区开放级别不足，需先调整展区为" + (level == 2 ? "对外开放" : "内部开放"));
            }
        }
        if (warningsOnly)
        {
            List<String> warnings = new ArrayList<>();
            Object version = item.get("version");
            if (version instanceof Number number && number.intValue() == 0) warnings.add("展项从未发布过");
            if ("3".equals(String.valueOf(item.get("status")))) warnings.add("展项当前为已下架状态，需重新发布");
            if (problems.isEmpty() && warnings.isEmpty()) warnings.add("无风险项");
            return warnings;
        }
        return problems;
    }

    private Map<String, Object> zone(long zoneId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select zone_id as zoneId,zone_code as zoneCode,"
                + "zone_name as zoneName,intro,cover,layout,route_no as routeNo,open_level as openLevel,status "
                + "from exh_zone where zone_id=? and del_flag='0'", zoneId);
        if (rows.isEmpty()) throw new ServiceException("展区不存在");
        return rows.get(0);
    }

    private Map<String, Object> item(long itemId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select " + ITEM_FIELDS + " from exh_item "
                + "where item_id=? and del_flag='0'", itemId);
        if (rows.isEmpty()) throw new ServiceException("展项不存在");
        return rows.get(0);
    }

    private int count(String sql, Object... args)
    {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
