package com.legaltraining.web.controller.exh;

import java.security.SecureRandom;
import com.legaltraining.common.annotation.Anonymous;
import com.legaltraining.common.annotation.RateLimiter;
import com.legaltraining.common.enums.LimitType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 终端设备（TM-01-01 ~ TM-01-03、TM-04-02）。
 *
 * 设备码一码一机：注册生成激活码，激活后签发长期令牌，可吊销；
 * 远程配置（模式/超时/音量/亮度）统一存 config_json，终端拉取后本地生效。
 */
@RestController
public class ExhibitDeviceController extends BaseController
{
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String STATUS = "case when d.status=1 and "
            + "(d.last_heartbeat is null or d.last_heartbeat < timestampadd(second,-90,now())) then 2 else d.status end";
    private static final String FIELDS = "d.device_id as deviceId,d.device_code as deviceCode,d.site_no as siteNo,"
            + "d.bind_zone as bindZone,z.zone_name as bindZoneName,d.bind_playlist as bindPlaylist,d.mode,"
            + "d.config_json as configJson,d.cache_version as cacheVersion,d.cache_size as cacheSize,"
            + STATUS + " as status,d.offline_hours as offlineHours,"
            + "date_format(d.auth_expire,'%Y-%m-%d %H:%i:%s') as authExpire,"
            + "date_format(d.activate_time,'%Y-%m-%d %H:%i:%s') as activateTime,"
            + "date_format(d.last_heartbeat,'%Y-%m-%d %H:%i:%s') as lastHeartbeat";
    private final JdbcTemplate jdbc;

    public ExhibitDeviceController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    public record DeviceForm(@NotBlank @Size(max = 64) String deviceCode, @Size(max = 64) String siteNo,
            @Pattern(regexp = "immersive|lite|kiosk") String mode, Long bindZone) {}
    public record ActivateForm(@NotBlank @Size(max = 64) String deviceCode,
            @NotBlank @Size(max = 16) String activateCode) {}
    public record BindForm(Long bindZone, Long bindPlaylist) {}
    public record ConfigForm(@Pattern(regexp = "immersive|lite|kiosk") String mode,
            Integer timeoutSeconds, Integer volume, Integer brightness) {}

    @GetMapping("/exh/device/list")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    public AjaxResult list(@RequestParam(required = false) Integer status)
    {
        String sql = "select " + FIELDS + " from exh_device d "
                + "left join exh_zone z on z.zone_id=d.bind_zone where d.del_flag='0'"
                + (status == null ? "" : " and " + STATUS + "=?") + " order by d.device_id desc";
        List<Map<String, Object>> rows = status == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, status);
        for (Map<String, Object> row : rows)
        {
            row.put("config", deviceConfig((String) row.get("configJson")));
            row.remove("configJson");
        }
        return success(rows);
    }

    @PostMapping("/exh/device")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    @Transactional
    public AjaxResult register(@Valid @RequestBody DeviceForm form)
    {
        Integer exists = jdbc.queryForObject("select count(1) from exh_device where device_code=? and del_flag='0'",
                Integer.class, form.deviceCode());
        if (exists != null && exists > 0) throw new ServiceException("设备码已存在，一个设备码只能绑定一台设备");
        if (form.bindZone() != null) zone(form.bindZone());
        String activateCode = String.format("%06d", RANDOM.nextInt(1000000));
        JSONObject config = new JSONObject();
        config.put("activateCode", activateCode);
        config.put("timeoutSeconds", 180);
        config.put("volume", 60);
        config.put("brightness", 80);
        jdbc.update("insert into exh_device(device_code,site_no,bind_zone,mode,config_json,status,create_time) "
                + "values(?,?,?,?,?,0,now())", form.deviceCode(), form.siteNo(), form.bindZone(),
                form.mode() == null ? "kiosk" : form.mode(), config.toJSONString());
        Map<String, Object> row = jdbc.queryForMap("select device_id as deviceId,device_code as deviceCode "
                + "from exh_device where device_code=? and del_flag='0'", form.deviceCode());
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("activateCode", activateCode);
        result.put("activateExpireMinutes", 1440L);
        return success(result);
    }

    @PostMapping("/exh/device/activate")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    @Transactional
    public AjaxResult activate(@Valid @RequestBody ActivateForm form)
    {
        return activateDevice(form);
    }

    @Anonymous
    @RateLimiter(time = 60, count = 12, limitType = LimitType.IP)
    @PostMapping("/gallery/v1/device/activate")
    @Transactional
    public ResponseEntity<AjaxResult> activateKiosk(@Valid @RequestBody ActivateForm form)
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(activateDevice(form));
    }

    private AjaxResult activateDevice(ActivateForm form)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select device_id as deviceId,status,config_json as config,timestampdiff(second,create_time,now()) as codeAge "
                + "from exh_device where device_code=? and del_flag='0' for update", form.deviceCode());
        if (rows.isEmpty()) throw new ServiceException("设备码不存在");
        Map<String, Object> device = rows.get(0);
        int status = ((Number) device.get("status")).intValue();
        if (status == 1) throw new ServiceException("设备已激活，如需重新激活请先停用");
        if (status == 3) throw new ServiceException("设备已停用");
        JSONObject config = device.get("config") == null ? new JSONObject()
                : JSON.parseObject((String) device.get("config"));
        if (!form.activateCode().equals(config.getString("activateCode")))
        {
            throw new ServiceException("激活码不正确");
        }
        Number codeAge = (Number) device.get("codeAge");
        if (codeAge == null || codeAge.longValue() >= 86400)
        {
            throw new ServiceException("激活码已过期，请联系管理员重新登记设备");
        }
        config.remove("activateCode");
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("update exh_device set status=1,token=?,auth_expire=?,activate_time=now(),last_heartbeat=now(),"
                + "config_json=?,update_time=now() where device_id=?", token,
                LocalDateTime.now(ZONE).plusYears(1), config.toJSONString(), device.get("deviceId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", device.get("deviceId"));
        result.put("token", token);
        result.put("expireAt", LocalDateTime.now(ZONE).plusYears(1).withNano(0).toString());
        return success(result);
    }

    @PutMapping("/exh/device/{deviceId}/bind")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    public AjaxResult bind(@PathVariable long deviceId, @Valid @RequestBody BindForm form)
    {
        if (form.bindZone() != null && form.bindPlaylist() != null)
        {
            throw new ServiceException("请选择一个展区或播放列表");
        }
        if (form.bindZone() != null) zone(form.bindZone());
        if (form.bindPlaylist() != null)
        {
            Integer playlist = jdbc.queryForObject("select count(1) from exh_playlist where playlist_id=?",
                    Integer.class, form.bindPlaylist());
            if (playlist == null || playlist == 0) throw new ServiceException("播放列表不存在");
        }
        int changed = jdbc.update("update exh_device set bind_zone=?,bind_playlist=?,update_time=now() "
                + "where device_id=? and del_flag='0'", form.bindZone(), form.bindPlaylist(), deviceId);
        if (changed == 0) throw new ServiceException("设备不存在");
        return success();
    }

    @PutMapping("/exh/device/{deviceId}/config")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    public AjaxResult config(@PathVariable long deviceId, @Valid @RequestBody ConfigForm form)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select config_json as config,mode from exh_device "
                + "where device_id=? and del_flag='0'", deviceId);
        if (rows.isEmpty()) throw new ServiceException("设备不存在");
        JSONObject config = rows.get(0).get("config") == null ? new JSONObject()
                : JSON.parseObject((String) rows.get(0).get("config"));
        if (form.timeoutSeconds() != null)
        {
            if (form.timeoutSeconds() < 30 || form.timeoutSeconds() > 3600) throw new ServiceException("无操作超时须在 30—3600 秒");
            config.put("timeoutSeconds", form.timeoutSeconds());
        }
        if (form.volume() != null)
        {
            if (form.volume() < 0 || form.volume() > 100) throw new ServiceException("音量须在 0—100");
            config.put("volume", form.volume());
        }
        if (form.brightness() != null)
        {
            if (form.brightness() < 0 || form.brightness() > 100) throw new ServiceException("亮度须在 0—100");
            config.put("brightness", form.brightness());
        }
        String mode = form.mode() == null ? (String) rows.get(0).get("mode") : form.mode();
        jdbc.update("update exh_device set mode=?,config_json=?,update_time=now() where device_id=?",
                mode, config.toJSONString(), deviceId);
        return success(Map.of("mode", mode, "config", deviceConfig(config.toJSONString())));
    }

    @PutMapping("/exh/device/{deviceId}/revoke")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    public AjaxResult revoke(@PathVariable long deviceId)
    {
        int changed = jdbc.update("update exh_device set status=3,token=null,auth_expire=null,update_time=now() "
                + "where device_id=? and del_flag='0'", deviceId);
        if (changed == 0) throw new ServiceException("设备不存在");
        return success();
    }

    public record PlaylistEntry(@jakarta.validation.constraints.NotNull Long itemId,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(3600) int staySeconds) {}
    public record PlaylistForm(@NotBlank @Size(max = 100) String name,
            @jakarta.validation.constraints.NotEmpty @Size(max = 200) List<@Valid PlaylistEntry> items) {}

    @GetMapping("/exh/device/content-options")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    public AjaxResult contentOptions()
    {
        return success(jdbc.queryForList("""
                select item_id as targetId,title as targetName
                from exh_item where status=2 and view_level=2 and del_flag='0' order by title,item_id
                """));
    }

    @GetMapping("/exh/device/playlists")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    public AjaxResult playlists()
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select playlist_id as playlistId,name from exh_playlist where status='0' order by playlist_id");
        for (Map<String, Object> row : rows)
        {
            row.put("items", jdbc.queryForList("""
                    select item_id as itemId,stay_seconds as staySeconds from exh_playlist_item
                    where playlist_id=? order by sort_no,id
                    """, row.get("playlistId")));
        }
        return success(rows);
    }

    @PostMapping("/exh/device/playlists")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    @Transactional
    public AjaxResult createPlaylist(@Valid @RequestBody PlaylistForm form)
    {
        validateEntries(form);
        org.springframework.jdbc.support.GeneratedKeyHolder key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "insert into exh_playlist(name,create_time) values(?,now())", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, form.name());
            return statement;
        }, key);
        long playlistId = java.util.Objects.requireNonNull(key.getKey()).longValue();
        replaceEntries(playlistId, form);
        return success(Map.of("playlistId", playlistId));
    }

    @PutMapping("/exh/device/playlists/{playlistId}")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    @Transactional
    public AjaxResult updatePlaylist(@PathVariable long playlistId, @Valid @RequestBody PlaylistForm form)
    {
        validateEntries(form);
        if (jdbc.queryForList("select playlist_id from exh_playlist where playlist_id=? and status='0' for update",
                Long.class, playlistId).isEmpty()) throw new ServiceException("播放列表不存在");
        jdbc.update("update exh_playlist set name=? where playlist_id=?", form.name(), playlistId);
        replaceEntries(playlistId, form);
        return success();
    }

    private void validateEntries(PlaylistForm form)
    {
        for (PlaylistEntry entry : form.items())
        {
            if (jdbc.queryForObject("select count(*) from exh_item where item_id=? and del_flag='0'",
                    Integer.class, entry.itemId()) == 0) throw new ServiceException("展项不存在");
        }
    }

    private void replaceEntries(long playlistId, PlaylistForm form)
    {
        jdbc.update("delete from exh_playlist_item where playlist_id=?", playlistId);
        int position = 0;
        for (PlaylistEntry entry : form.items())
        {
            jdbc.update("insert into exh_playlist_item(playlist_id,item_id,stay_seconds,sort_no) values(?,?,?,?)",
                    playlistId, entry.itemId(), entry.staySeconds(), position++);
        }
    }

    public record HeartbeatForm(@Size(max = 32) String cacheVersion, @PositiveOrZero Long cacheSize) {}

    @Anonymous
    @PostMapping("/gallery/v1/device/heartbeat")
    @Transactional
    public ResponseEntity<AjaxResult> heartbeat(
            @RequestHeader(value = "X-Device-Code", required = false) String code,
            @RequestHeader(value = "X-Device-Token", required = false) String token,
            @Valid @RequestBody HeartbeatForm form)
    {
        if (code == null || code.length() > 64 || token == null || !token.matches("[a-f0-9]{32,64}"))
        {
            return ResponseEntity.status(401).build();
        }
        List<Map<String, Object>> devices = jdbc.queryForList("""
                select device_id as id, bind_zone as zoneId, bind_playlist as playlistId, mode, config_json as config
                from exh_device
                where device_code=? and binary token=? and status in (1,2) and auth_expire>now() and del_flag='0'
                for update
                """, code, token);
        if (devices.isEmpty()) return ResponseEntity.status(401).build();
        Map<String, Object> device = devices.get(0);
        jdbc.update("update exh_device set status=1,last_heartbeat=now(),cache_version=?,cache_size=? where device_id=?",
                form.cacheVersion(), form.cacheSize(), device.get("id"));
        List<Map<String, Object>> items = deviceItems(device.get("zoneId"), device.get("playlistId"));
        String version = UUID.nameUUIDFromBytes(JSON.toJSONBytes(items)).toString().replace("-", "");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", device.get("mode"));
        data.put("config", deviceConfig((String) device.get("config")));
        data.put("version", version);
        data.put("changed", !version.equals(form.cacheVersion()));
        if (!version.equals(form.cacheVersion())) data.put("items", items);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(data));
    }

    @GetMapping("/exh/device/events")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    public AjaxResult events()
    {
        return success(jdbc.queryForList("""
                select e.id, d.device_code as deviceCode, e.event_type as eventType,
                       e.detail, date_format(e.report_time,'%Y-%m-%d %H:%i:%s') as reportTime
                from exh_device_event e join exh_device d on d.device_id=e.device_id
                order by e.report_time desc,e.id desc limit 100
                """));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
    @Transactional
    public void markOffline()
    {
        List<Long> ids = jdbc.queryForList("""
                select device_id from exh_device
                where status=1 and last_heartbeat < timestampadd(second,-90,now()) and del_flag='0'
                for update skip locked
                """, Long.class);
        for (Long id : ids)
        {
            jdbc.update("update exh_device set status=2,update_time=now() where device_id=?", id);
            jdbc.update("""
                    insert into exh_device_event(device_id,event_type,detail,report_time)
                    values(?,'offline','超过 90 秒未收到心跳',now())
                    """, id);
        }
    }

    private List<Map<String, Object>> deviceItems(Object zoneId, Object playlistId)
    {
        if (zoneId == null && playlistId == null) return List.of();
        String query = """
                select v.snapshot%s
                from exh_item i
                join exh_item_version v on v.item_id=i.item_id
                  and v.version=(select max(p.version) from exh_item_version p
                                 where p.item_id=i.item_id and p.edit_type in (1,2))
                join exh_zone z on z.zone_id=cast(json_unquote(json_extract(v.snapshot,'$.zoneId')) as unsigned)
                """;
        String visible = " where i.status=2 and i.view_level=2 and i.del_flag='0'"
                + " and z.status=1 and z.open_level=2 and z.del_flag='0' ";
        List<Map<String, Object>> rows = playlistId == null
                ? jdbc.queryForList(query.formatted("") + visible + " and z.zone_id=? order by i.item_id", zoneId)
                : jdbc.queryForList(query.formatted(", pi.stay_seconds as staySeconds")
                        + " join exh_playlist_item pi on pi.item_id=i.item_id"
                        + " join exh_playlist pl on pl.playlist_id=pi.playlist_id"
                        + visible + " and pi.playlist_id=? and pl.status='0' order by pi.sort_no,pi.id", playlistId);
        return rows.stream().map(row -> {
            JSONObject item = JSON.parseObject((String) row.get("snapshot"));
            Map<String, Object> result = new LinkedHashMap<>();
            for (String field : List.of("itemId", "zoneId", "title", "summary", "cover", "content", "media"))
            {
                result.put(field, item.get(field));
            }
            result.put("staySeconds", row.getOrDefault("staySeconds", 15));
            return result;
        }).toList();
    }

    private JSONObject deviceConfig(String json)
    {
        JSONObject config = json == null ? new JSONObject() : JSON.parseObject(json);
        config.remove("activateCode");
        return config;
    }

    private void zone(long zoneId)
    {
        Integer total = jdbc.queryForObject("select count(1) from exh_zone where zone_id=? and del_flag='0'",
                Integer.class, zoneId);
        if (total == null || total == 0) throw new ServiceException("展区不存在");
    }
}
