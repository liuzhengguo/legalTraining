package com.legaltraining.web.controller.exh;

import java.security.SecureRandom;
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
    private static final String FIELDS = "d.device_id as deviceId,d.device_code as deviceCode,d.site_no as siteNo,"
            + "d.bind_zone as bindZone,z.zone_name as bindZoneName,d.bind_playlist as bindPlaylist,d.mode,"
            + "d.config_json as configJson,d.cache_version as cacheVersion,d.cache_size as cacheSize,"
            + "d.status,d.offline_hours as offlineHours,"
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
                + (status == null ? "" : " and d.status=?") + " order by d.device_id desc";
        List<Map<String, Object>> rows = status == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, status);
        for (Map<String, Object> row : rows)
        {
            row.put("config", row.get("configJson") == null ? Map.of() : JSON.parseObject((String) row.get("configJson")));
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
        result.put("activateExpireMinutes", 60L);
        return success(result);
    }

    @PostMapping("/exh/device/activate")
    @PreAuthorize("@ss.hasPermi('exh:device:list')")
    @Transactional
    public AjaxResult activate(@Valid @RequestBody ActivateForm form)
    {
        List<Map<String, Object>> rows = jdbc.queryForList("select device_id as deviceId,status,config_json as config "
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
        return success(Map.of("mode", mode, "config", config));
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

    private void zone(long zoneId)
    {
        Integer total = jdbc.queryForObject("select count(1) from exh_zone where zone_id=? and del_flag='0'",
                Integer.class, zoneId);
        if (total == null || total == 0) throw new ServiceException("展区不存在");
    }
}
