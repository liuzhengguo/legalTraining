package com.legaltraining.web.controller.exh;

import java.security.SecureRandom;
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

        StringBuilder where = new StringBuilder(" where stat_date between ? and ?");
        List<Object> args = new ArrayList<>(List.of(start, end));
        if (targetType != null && !targetType.isBlank())
        {
            where.append(" and target_type=?");
            args.add(targetType);
        }
        if (targetId != null)
        {
            where.append(" and target_id=?");
            args.add(targetId);
        }
        List<Map<String, Object>> trend = jdbc.queryForList("select date_format(stat_date,'%Y-%m-%d') as statDate,"
                + "sum(pv) as pv,sum(uv) as uv,round(avg(avg_duration)) as avgDuration from exh_access_daily"
                + where + " group by stat_date order by stat_date", args.toArray());
        List<Map<String, Object>> byChannel = jdbc.queryForList("select channel,sum(pv) as pv,sum(uv) as uv,"
                + "round(avg(avg_duration)) as avgDuration from exh_access_daily" + where
                + " group by channel order by pv desc", args.toArray());
        List<Map<String, Object>> byTarget = jdbc.queryForList("select target_type as targetType,target_id as targetId,"
                + "sum(pv) as pv,sum(uv) as uv from exh_access_daily" + where
                + " group by target_type,target_id order by pv desc limit 20", args.toArray());

        Map<String, Object> totals = new LinkedHashMap<>();
        long pv = 0;
        long uv = 0;
        for (Map<String, Object> row : trend)
        {
            pv += number(row.get("pv"));
            uv += number(row.get("uv"));
        }
        totals.put("pv", pv);
        totals.put("uv", uv);
        totals.put("days", trend.size());
        totals.put("avgPvPerDay", trend.isEmpty() ? 0 : pv / trend.size());

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
        String base = jdbc.queryForList("select config_value from sys_config where config_key='biz.exhibit.shareBase'",
                String.class).stream().findFirst().orElse("https://exh.legaltraining.cn/s/");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("linkId", row.get("linkId"));
        result.put("shortCode", row.get("shortCode"));
        result.put("shortUrl", base + row.get("shortCode"));
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
