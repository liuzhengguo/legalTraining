package com.legaltraining.web.controller.exh;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import javax.imageio.ImageIO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.legaltraining.common.annotation.Anonymous;
import com.legaltraining.common.annotation.RateLimiter;
import com.legaltraining.common.enums.LimitType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 分享链接与访问统计（EM-05-01 / EM-05-02 / EF-05-01）。
 *
 * 短链长期有效、不带时效；访问统计只落到日聚合表，不采身份信息。
 */
@RestController
public class ExhibitShareController extends BaseController
{
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;

    public ExhibitShareController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    public record ShareForm(@NotBlank @Pattern(regexp = "zone|item") String targetType,
            @NotNull Long targetId,
            @NotBlank @Pattern(regexp = "qrcode|link|terminal|portal") String channel) {}

    @PostMapping("/exh/share")
    @PreAuthorize("@ss.hasPermi('exh:share:list')")
    public AjaxResult create(@Valid @RequestBody ShareForm form)
    {
        ensureTarget(form.targetType(), form.targetId());
        List<Map<String, Object>> existing = jdbc.queryForList("select link_id as linkId,short_code as shortCode,"
                + "qr_path as qrPath,view_count as viewCount from exh_share_link "
                + "where target_type=? and target_id=? and channel=? order by link_id desc limit 1",
                form.targetType(), form.targetId(), form.channel());
        if (!existing.isEmpty())
        {
            return success(describe(existing.get(0), form.targetType(), form.targetId(), form.channel()));
        }
        String code = nextCode();
        jdbc.update("insert into exh_share_link(target_type,target_id,channel,short_code,qr_path,create_by,create_time) "
                + "values(?,?,?,?,?,?,now())", form.targetType(), form.targetId(), form.channel(), code,
                "/files/qr/" + code + ".png", SecurityUtils.getUserId());
        Map<String, Object> row = jdbc.queryForMap("select link_id as linkId,short_code as shortCode,"
                + "qr_path as qrPath,view_count as viewCount from exh_share_link where short_code=?", code);
        return success(describe(row, form.targetType(), form.targetId(), form.channel()));
    }

    @GetMapping("/exh/share/options")
    @PreAuthorize("@ss.hasPermi('exh:share:list')")
    public AjaxResult options()
    {
        return success(jdbc.queryForList("""
                select 'zone' as targetType,zone_id as targetId,zone_name as targetName
                from exh_zone where status=1 and open_level=2 and del_flag='0'
                union all
                select 'item',item_id,title from exh_item where status=2 and view_level=2 and del_flag='0'
                order by targetType,targetName,targetId
                """));
    }

    @Anonymous
    @GetMapping("/gallery/v1/share/{code}")
    public ResponseEntity<AjaxResult> resolve(@PathVariable String code)
    {
        if (!validCode(code))
        {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select target_type as targetType,target_id as targetId,channel
                from exh_share_link s where short_code=?
                and (
                    (s.target_type='zone' and exists (
                        select 1 from exh_zone z where z.zone_id=s.target_id
                        and z.status=1 and z.open_level=2 and z.del_flag='0'))
                    or (s.target_type='item' and exists (
                        select 1 from exh_item i where i.item_id=s.target_id
                        and i.status in (2,3) and i.view_level=2 and i.del_flag='0'))
                )
                """, code);
        if (rows.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }
        jdbc.update("update exh_share_link set view_count=view_count+1 where short_code=?", code);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(rows.get(0)));
    }

    @Anonymous
    @GetMapping(value = "/files/qr/{code}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String code) throws IOException, WriterException
    {
        if (!validCode(code) || jdbc.queryForObject(
                "select count(*) from exh_share_link where short_code=?", Integer.class, code) == 0)
        {
            return ResponseEntity.notFound().build();
        }
        return qrImage(shareBase() + code);
    }

    @Anonymous
    @GetMapping(value = "/gallery/v1/items/{itemId}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> publicItemQr(@PathVariable long itemId) throws IOException, WriterException
    {
        Integer visible = jdbc.queryForObject("""
                select count(*) from exh_item i
                join exh_item_version v on v.item_id=i.item_id
                and v.version=(select max(p.version) from exh_item_version p
                               where p.item_id=i.item_id and p.edit_type in (1,2))
                join exh_zone z on z.zone_id=cast(json_unquote(json_extract(v.snapshot,'$.zoneId')) as unsigned)
                where i.item_id=? and i.status=2 and i.view_level=2 and i.del_flag='0'
                and z.status=1 and z.open_level=2 and z.del_flag='0'
                """, Integer.class, itemId);
        if (visible == null || visible == 0)
        {
            return ResponseEntity.notFound().build();
        }
        String link = java.net.URI.create(shareBase()).resolve("../lite/item/" + itemId + "?channel=qrcode").toString();
        return qrImage(link);
    }

    private ResponseEntity<byte[]> qrImage(String link) throws IOException, WriterException
    {
        var matrix = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 256, 256);
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < matrix.getHeight(); y++)
        {
            for (int x = 0; x < matrix.getWidth(); x++)
            {
                image.setRGB(x, y, matrix.get(x, y) ? 0xff000000 : 0xffffffff);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .contentType(MediaType.IMAGE_PNG).body(bytes.toByteArray());
    }

    private boolean validCode(String code)
    {
        return code.matches("[A-Za-z0-9]{1,16}");
    }

    private String shareBase()
    {
        return jdbc.queryForList("select config_value from sys_config where config_key='biz.exhibit.shareBase'",
                String.class).stream().findFirst().orElse("https://exh.legaltraining.cn/s/");
    }

    @GetMapping("/exh/share/list")
    @PreAuthorize("@ss.hasPermi('exh:share:list')")
    public AjaxResult list(@RequestParam(required = false) String targetType)
    {
        String where = targetType == null || targetType.isBlank() ? "" : " where s.target_type=? ";
        List<Object> args = new ArrayList<>();
        if (!where.isEmpty()) args.add(targetType);
        List<Map<String, Object>> rows = jdbc.queryForList("select s.link_id as linkId,s.target_type as targetType,"
                + "s.target_id as targetId,s.channel,s.short_code as shortCode,s.qr_path as qrPath,"
                + "s.view_count as viewCount,date_format(s.create_time,'%Y-%m-%d %H:%i:%s') as createTime,"
                + "case when s.target_type='item' then (select title from exh_item i where i.item_id=s.target_id) "
                + "else (select zone_name from exh_zone z where z.zone_id=s.target_id) end as targetName "
                + "from exh_share_link s" + where + "order by s.link_id desc", args.toArray());
        return success(rows);
    }

    public record VisitForm(
            @NotBlank @Pattern(regexp = "zone|item") String targetType,
            @NotNull Long targetId,
            @NotBlank @Pattern(regexp = "qrcode|link|terminal|portal|direct") String channel,
            @NotBlank @Pattern(regexp = "immersive|lite") String mode,
            @NotBlank @Pattern(regexp = "[a-f0-9]{32}") String session,
            @Min(0) @Max(3600) int duration) {}

    @Anonymous
    @RateLimiter(time = 60, count = 120, limitType = LimitType.IP)
    @PostMapping("/gallery/v1/visits")
    public ResponseEntity<Void> visit(@Valid @RequestBody VisitForm visit)
    {
        boolean item = "item".equals(visit.targetType());
        String target = item ? """
                select z.zone_id, i.item_id from exh_item i
                join exh_item_version v on v.item_id=i.item_id
                and v.version=(select max(p.version) from exh_item_version p
                               where p.item_id=i.item_id and p.edit_type in (1,2))
                join exh_zone z on z.zone_id=cast(json_unquote(json_extract(v.snapshot,'$.zoneId')) as unsigned)
                where i.item_id=? and i.status=2 and i.view_level=2 and i.del_flag='0'
                """ : "select z.zone_id, null as item_id from exh_zone z where z.zone_id=? ";
        jdbc.update("""
                insert into exh_access_event(channel,mode,anon_id,duration,access_time,zone_id,item_id)
                select ?,?,?,?,now(), target.* from (
                """ + target + " and z.status=1 and z.open_level=2 and z.del_flag='0') target",
                visit.channel(), visit.mode(), visit.session(), visit.duration(), visit.targetId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exh/stat/access")
    @PreAuthorize("@ss.hasPermi('exh:stat:query')")
    public AjaxResult access(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId)
    {
        LocalDate end = dateTo == null ? LocalDate.now() : dateTo;
        LocalDate start = dateFrom == null ? end.minusDays(6) : dateFrom;
        if (start.isAfter(end)) throw new ServiceException("开始日期不能晚于结束日期");
        if (end.toEpochDay() - start.toEpochDay() > 365) throw new ServiceException("统计区间不得超过一年");

        if (targetType != null && !List.of("zone", "item").contains(targetType))
        {
            throw new ServiceException("请选择展区或展项");
        }
        StringBuilder where = new StringBuilder(" where access_time >= ? and access_time < ?");
        List<Object> args = new ArrayList<>(List.of(start, end.plusDays(1)));
        if (targetType != null)
        {
            String column = "zone".equals(targetType) ? "zone_id" : "item_id";
            where.append(" and ").append(column).append(targetId == null ? " is not null" : "=?");
            if (targetId != null) args.add(targetId);
        }
        else if (targetId != null)
        {
            throw new ServiceException("按对象查询时须指定对象类型");
        }
        String metrics = "count(*) as pv,count(distinct anon_id) as uv,round(avg(duration)) as avgDuration";
        String from = " from exh_access_event" + where;
        List<Map<String, Object>> trend = jdbc.queryForList("select date(access_time) as statDate,"
                + metrics + from + " group by date(access_time) order by statDate", args.toArray());
        List<Map<String, Object>> byChannel = jdbc.queryForList("select channel," + metrics + from
                + " group by channel order by pv desc", args.toArray());

        List<Map<String, Object>> byTarget = new ArrayList<>();
        if (!"item".equals(targetType))
        {
            byTarget.addAll(jdbc.queryForList("select 'zone' as targetType,zone_id as targetId," + metrics + from
                    + " and zone_id is not null group by zone_id order by pv desc limit 20", args.toArray()));
        }
        if (!"zone".equals(targetType))
        {
            byTarget.addAll(jdbc.queryForList("select 'item' as targetType,item_id as targetId," + metrics + from
                    + " and item_id is not null group by item_id order by pv desc limit 20", args.toArray()));
        }

        Map<String, Object> totals = jdbc.queryForMap("select " + metrics + from, args.toArray());
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        totals.put("days", days);
        totals.put("avgPvPerDay", number(totals.get("pv")) / days);

        Map<String, Object> realtime = new LinkedHashMap<>();
        realtime.put("todayPv", number(jdbc.queryForObject("select count(1) from exh_access_event "
                + "where access_time >= curdate()", Long.class)));
        realtime.put("todayUv", number(jdbc.queryForObject("select count(distinct anon_id) from exh_access_event "
                + "where access_time >= curdate() and anon_id is not null", Long.class)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dateFrom", start);
        result.put("dateTo", end);
        result.put("totals", totals);
        result.put("trend", trend);
        result.put("byChannel", byChannel);
        result.put("byTarget", byTarget);
        result.put("realtime", realtime);
        return success(result);
    }

    private Map<String, Object> describe(Map<String, Object> row, String targetType, long targetId, String channel)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("linkId", row.get("linkId"));
        result.put("shortCode", row.get("shortCode"));
        result.put("shortUrl", shareBase() + row.get("shortCode"));
        result.put("qrUrl", row.get("qrPath"));
        result.put("targetType", targetType);
        result.put("targetId", targetId);
        result.put("channel", channel);
        result.put("viewCount", row.get("viewCount"));
        result.put("expireAt", null);
        return result;
    }

    private void ensureTarget(String targetType, long targetId)
    {
        String sql = "item".equals(targetType)
                ? "select count(1) from exh_item where item_id=? and del_flag='0'"
                : "select count(1) from exh_zone where zone_id=? and del_flag='0'";
        Integer total = jdbc.queryForObject(sql, Integer.class, targetId);
        if (total == null || total == 0) throw new ServiceException("分享对象不存在");
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

    private long number(Object value)
    {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private long number(Long value)
    {
        return value == null ? 0L : value;
    }
}
