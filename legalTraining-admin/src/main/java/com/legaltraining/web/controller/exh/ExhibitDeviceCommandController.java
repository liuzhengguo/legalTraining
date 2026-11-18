package com.legaltraining.web.controller.exh;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.legaltraining.common.annotation.Anonymous;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class ExhibitDeviceCommandController extends BaseController
{
    private final JdbcTemplate db;

    public ExhibitDeviceCommandController(JdbcTemplate db) { this.db = db; }

    public record Request(@Pattern(regexp = "restart|capture|reboot|offline-check") String action) {}
    public record Report(@Pattern(regexp = "[a-f0-9-]{36}") String lease, boolean success,
            @Size(max = 240) String detail, @Size(max = 2800000) String png) {}
    public record Event(@Pattern(regexp = "crash|cache_broken") String type, @Size(max = 500) String detail) {}

    @Anonymous
    @PostMapping("/gallery/v1/device/events")
    @Transactional
    public ResponseEntity<Void> event(@Valid @RequestBody Event event,
            @RequestHeader(value="X-Device-Code",required=false) String code,
            @RequestHeader(value="X-Device-Token",required=false) String token)
    {
        Long id = authorizedDevice(code,token);
        if (id == null) return ResponseEntity.status(401).build();
        if (event.type() == null) return ResponseEntity.badRequest().build();
        db.update("""
                insert into exh_device_event(device_id,event_type,detail,report_time)
                select ?,?,?,now() where not exists (
                  select 1 from exh_device_event where device_id=? and event_type=?
                  and report_time>timestampadd(minute,-2,now()))
                """, id,event.type(),event.detail(),id,event.type());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exh/device/{id}/commands")
    @PreAuthorize("@ss.hasRole('admin')")
    public AjaxResult list(@PathVariable long id)
    {
        return success(db.queryForList("""
                select command_id as commandId,action,status,detail,operator_id as operatorId,
                       screenshot is not null as hasImage,created_at as createdAt,completed_at as completedAt
                from exh_device_command where device_id=? order by command_id desc limit 30
                """, id));
    }

    @PostMapping("/exh/device/{id}/commands")
    @PreAuthorize("@ss.hasRole('admin')")
    @Transactional
    public AjaxResult enqueue(@PathVariable long id, @Valid @RequestBody Request request)
    {
        if (request.action() == null) throw new ServiceException("请选择终端操作");
        if (db.queryForList("""
                select device_id from exh_device where device_id=? and status in (1,2) and del_flag='0' and auth_expire>now()
                for update
                """, Long.class, id).isEmpty()) throw new ServiceException("终端未激活或授权已过期");
        expire(id);
        if (db.queryForObject("select count(*) from exh_device_command where device_id=? and status in (0,1)", Integer.class, id) > 0)
            throw new ServiceException("该设备已有待执行操作");
        db.update("insert into exh_device_command(device_id,action,operator_id) values(?,?,?)", id,request.action(),getUserId());
        return success();
    }

    @GetMapping(value="/exh/device/{id}/commands/{commandId}/image", produces=MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("@ss.hasRole('admin')")
    public ResponseEntity<byte[]> image(@PathVariable long id, @PathVariable long commandId)
    {
        List<byte[]> images = db.query("select screenshot from exh_device_command where device_id=? and command_id=? and status=2",
                (rs, row) -> rs.getBytes(1), id, commandId);
        if (images.isEmpty() || images.get(0) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(images.get(0));
    }

    @Anonymous
    @PostMapping("/gallery/v1/device/commands/claim")
    @Transactional
    public ResponseEntity<AjaxResult> claim(@RequestHeader(value="X-Device-Code",required=false) String code,
            @RequestHeader(value="X-Device-Token",required=false) String token)
    {
        Long id = authorizedDevice(code,token);
        if (id == null) return ResponseEntity.status(401).build();
        expire(id);
        var pending = db.queryForList("""
                select command_id as commandId,action from exh_device_command
                where device_id=? and status=0 order by command_id limit 1 for update
                """, id);
        if (pending.isEmpty()) return ResponseEntity.noContent().build();
        var command = pending.get(0);
        String lease = UUID.randomUUID().toString();
        db.update("update exh_device_command set status=1,claim_token=?,claimed_at=now() where command_id=?",
                lease,command.get("commandId"));
        command.put("lease",lease);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(command));
    }

    @Anonymous
    @PostMapping("/gallery/v1/device/commands/{commandId}/result")
    @Transactional
    public ResponseEntity<Void> result(@PathVariable long commandId, @Valid @RequestBody Report report,
            @RequestHeader(value="X-Device-Code",required=false) String code,
            @RequestHeader(value="X-Device-Token",required=false) String token)
    {
        Long id = authorizedDevice(code,token);
        if (id == null) return ResponseEntity.status(401).build();
        expire(id);
        List<String> actions = db.queryForList("""
                select action from exh_device_command where device_id=? and command_id=? and claim_token=? and status=1 for update
                """, String.class, id,commandId,report.lease());
        if (actions.isEmpty()) return ResponseEntity.notFound().build();
        byte[] png = null;
        if (report.success() && "capture".equals(actions.get(0)))
        {
            try { png = Base64.getDecoder().decode(report.png() == null ? "" : report.png()); }
            catch (IllegalArgumentException ex) { return ResponseEntity.badRequest().build(); }
            if (png.length < 24 || png.length > 2 * 1024 * 1024 || png[0] != (byte) 137 || png[1] != 80 || png[2] != 78 || png[3] != 71)
                return ResponseEntity.badRequest().build();
        }
        db.update("""
                update exh_device_command set status=?,detail=?,screenshot=?,completed_at=now(),claim_token=null where command_id=?
                """, report.success()?2:3,report.detail(),png,commandId);
        return ResponseEntity.noContent().build();
    }

    private Long authorizedDevice(String code, String token)
    {
        if (code == null || code.length() > 64 || token == null || !token.matches("[a-f0-9]{32,64}")) return null;
        return db.queryForList("""
                select device_id from exh_device where device_code=? and binary token=? and status in (1,2)
                and auth_expire>now() and del_flag='0' for update
                """, Long.class, code,token).stream().findFirst().orElse(null);
    }

    private void expire(long deviceId)
    {
        db.update("""
                update exh_device_command set status=3,detail='终端响应超时',completed_at=now(),claim_token=null
                where device_id=? and ((status=0 and created_at<timestampadd(minute,-15,now()))
                       or (status=1 and claimed_at<timestampadd(minute,if(action='reboot',-16,-2),now())))
                """, deviceId);
    }
}
