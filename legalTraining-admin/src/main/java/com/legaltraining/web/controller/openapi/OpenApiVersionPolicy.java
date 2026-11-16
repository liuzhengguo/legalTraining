package com.legaltraining.web.controller.openapi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Size;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.system.domain.SysConfig;
import com.legaltraining.system.service.ISysConfigService;
import org.springframework.stereotype.Service;

@Service
public class OpenApiVersionPolicy
{
    private static final String KEY = "biz.openapi.v1.lifecycle";
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Shanghai");
    private final ISysConfigService configs;

    public OpenApiVersionPolicy(ISysConfigService configs) { this.configs = configs; }

    public record Schedule(LocalDateTime noticeFrom, LocalDateTime sunsetAt, @Size(max = 160) String message) {}

    public record Version(String version, String status, Schedule schedule) {}

    public Schedule schedule()
    {
        String stored = configs.selectConfigByKey(KEY);
        return stored == null || stored.isBlank() ? new Schedule(null, null, null) : JSON.parseObject(stored, Schedule.class);
    }

    public Version current()
    {
        Schedule schedule = schedule();
        LocalDateTime now = LocalDateTime.now(TIMEZONE);
        String status = schedule.sunsetAt() != null && !now.isBefore(schedule.sunsetAt()) ? "retired"
                : schedule.noticeFrom() != null && !now.isBefore(schedule.noticeFrom()) ? "deprecated" : "active";
        return new Version("v1", status, schedule);
    }

    public synchronized void save(Schedule schedule, String operator)
    {
        boolean clearing = schedule.noticeFrom() == null && schedule.sunsetAt() == null;
        if (!clearing && (schedule.noticeFrom() == null || schedule.sunsetAt() == null
                || !schedule.sunsetAt().isAfter(schedule.noticeFrom())
                || schedule.message() == null || schedule.message().isBlank()))
            throw new ServiceException("请填写通知时间、晚于通知时间的下线时间及下线说明");
        String value = JSON.toJSONString(schedule);
        if (value.length() > 500) throw new ServiceException("下线说明过长");
        SysConfig query = new SysConfig();
        query.setConfigKey(KEY);
        SysConfig config = configs.selectConfigList(query).stream().filter(entry -> KEY.equals(entry.getConfigKey()))
                .findFirst().orElseGet(SysConfig::new);
        config.setConfigKey(KEY);
        config.setConfigName("开放接口 v1 下线计划");
        config.setConfigType("Y");
        config.setConfigValue(value);
        if (config.getConfigId() == null)
        {
            config.setCreateBy(operator);
            configs.insertConfig(config);
        }
        else
        {
            config.setUpdateBy(operator);
            configs.updateConfig(config);
        }
    }

    public void writeHeaders(Version version, HttpServletResponse response, String contextPath)
    {
        response.setHeader("X-API-Version", version.version());
        if ("active".equals(version.status())) return;
        Schedule schedule = version.schedule();
        Instant deprecation = schedule.noticeFrom().atZone(TIMEZONE).toInstant();
        Instant sunset = schedule.sunsetAt().atZone(TIMEZONE).toInstant();
        response.setHeader("Deprecation", "@" + deprecation.getEpochSecond());
        response.setHeader("Sunset", DateTimeFormatter.ofPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'", java.util.Locale.US)
                .format(sunset.atOffset(ZoneOffset.UTC)));
        response.addHeader("Link", "<" + contextPath + "/openapi/v1/version>; rel=\"deprecation\"");
    }
}
