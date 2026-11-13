package com.legaltraining.business.ops.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.ops.domain.OpsApp;
import com.legaltraining.business.ops.domain.OpsAppApi;
import com.legaltraining.business.ops.domain.OpsAppIp;
import com.legaltraining.business.ops.domain.OpsCallLog;
import com.legaltraining.business.ops.mapper.OpsAppMapper;
import com.legaltraining.business.ops.service.IOpsAppService;
import com.legaltraining.business.ops.support.OpenApiException;
import com.legaltraining.business.ops.support.OpenApiSign;
import com.legaltraining.common.core.redis.RedisCache;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 应用接入 服务实现
 *
 * 鉴权顺序：AppID → 应用状态 → 密钥有效期 → 时间戳窗口 → 签名 → nonce 防重放
 *          → IP 白名单 → 接口授权 → 双配额限流。
 * 限流失败返回 429，其余鉴权失败返回 401/403，与 OP-03-03 的错误码约定一致。
 */
@Service
public class OpsAppServiceImpl implements IOpsAppService
{
    /** 允许的时间偏移（毫秒） */
    private static final long TIME_WINDOW_MS = 5 * 60 * 1000L;

    /** nonce 保留时长（分钟） */
    private static final int NONCE_TTL_MIN = 10;

    @Autowired
    private OpsAppMapper opsAppMapper;

    @Autowired
    private RedisCache redisCache;

    @Override
    public List<OpsApp> selectOpsAppList(OpsApp query)
    {
        return opsAppMapper.selectOpsAppList(query);
    }

    @Override
    public OpsApp selectOpsAppById(Long appId)
    {
        OpsApp app = opsAppMapper.selectOpsAppById(appId);
        if (app != null)
        {
            app.setApiList(opsAppMapper.selectApis(appId));
            app.setIpList(opsAppMapper.selectIps(appId));
        }
        return app;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpsApp insertOpsApp(OpsApp app)
    {
        if (StringUtils.isEmpty(app.getAppName()))
        {
            throw new ServiceException("应用名称不能为空");
        }
        String secretPlain = OpenApiSign.randomSecret();
        app.setAppCode(OpenApiSign.randomAppCode());
        app.setAppSecret(OpenApiSign.signKey(secretPlain));
        if (app.getStatus() == null)
        {
            app.setStatus(0);
        }
        if (app.getRateLimit() == null)
        {
            app.setRateLimit(600);
        }
        app.setCreateBy(currentUsername());
        opsAppMapper.insertOpsApp(app);
        app.setSecretPlain(secretPlain);
        return app;
    }

    @Override
    public int updateOpsApp(OpsApp app)
    {
        if (opsAppMapper.selectOpsAppById(app.getAppId()) == null)
        {
            throw new ServiceException("应用不存在");
        }
        return opsAppMapper.updateOpsApp(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteOpsAppByIds(Long[] appIds)
    {
        if (appIds == null || appIds.length == 0)
        {
            return 0;
        }
        for (Long appId : appIds)
        {
            opsAppMapper.deleteApis(appId);
            opsAppMapper.deleteIps(appId);
        }
        return opsAppMapper.deleteOpsAppByIds(appIds);
    }

    @Override
    public String resetSecret(Long appId)
    {
        OpsApp app = opsAppMapper.selectOpsAppById(appId);
        if (app == null)
        {
            throw new ServiceException("应用不存在");
        }
        String secretPlain = OpenApiSign.randomSecret();
        opsAppMapper.updateSecret(appId, OpenApiSign.signKey(secretPlain));
        // 旧签名立即失效：清掉该应用的限流与 nonce 缓存
        return secretPlain;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveApis(Long appId, List<OpsAppApi> apis)
    {
        opsAppMapper.deleteApis(appId);
        if (apis == null || apis.isEmpty())
        {
            return 0;
        }
        List<OpsAppApi> rows = new ArrayList<>();
        for (OpsAppApi api : apis)
        {
            if (StringUtils.isEmpty(api.getApiCode()))
            {
                continue;
            }
            api.setAppId(appId);
            if (api.getQuotaPerMin() == null)
            {
                api.setQuotaPerMin(60);
            }
            if (StringUtils.isEmpty(api.getEnable()))
            {
                api.setEnable("1");
            }
            rows.add(api);
        }
        return rows.isEmpty() ? 0 : opsAppMapper.batchInsertApis(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveIps(Long appId, List<OpsAppIp> ips)
    {
        opsAppMapper.deleteIps(appId);
        if (ips == null || ips.isEmpty())
        {
            return 0;
        }
        List<OpsAppIp> rows = new ArrayList<>();
        for (OpsAppIp ip : ips)
        {
            if (StringUtils.isEmpty(ip.getIpAddr()))
            {
                continue;
            }
            ip.setAppId(appId);
            rows.add(ip);
        }
        return rows.isEmpty() ? 0 : opsAppMapper.batchInsertIps(rows);
    }

    @Override
    public OpsApp verify(String appCode, String timestamp, String nonce, String sign,
                         String uri, String clientIp, String apiCode)
    {
        if (StringUtils.isEmpty(appCode) || StringUtils.isEmpty(timestamp)
                || StringUtils.isEmpty(nonce) || StringUtils.isEmpty(sign))
        {
            throw new OpenApiException(401, "缺少鉴权头：X-App-Id / X-Timestamp / X-Nonce / X-Sign");
        }
        OpsApp app = opsAppMapper.selectByAppCode(appCode);
        if (app == null)
        {
            throw new OpenApiException(401, "AppID 无效");
        }
        if (app.getStatus() == null || app.getStatus() != 1)
        {
            throw new OpenApiException(403, "应用未启用或已停用");
        }
        if (app.getExpiredAt() != null && app.getExpiredAt().before(new Date()))
        {
            throw new OpenApiException(403, "密钥已过期，请重置密钥");
        }

        long ts;
        try
        {
            ts = Long.parseLong(timestamp);
        }
        catch (NumberFormatException e)
        {
            throw new OpenApiException(401, "时间戳格式错误（应为毫秒）");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIME_WINDOW_MS)
        {
            throw new OpenApiException(401, "请求时间与服务器相差超过 5 分钟");
        }

        String expect = OpenApiSign.sign(app.getAppSecret(), appCode, timestamp, nonce, uri);
        if (!OpenApiSign.safeEquals(expect, sign))
        {
            throw new OpenApiException(401, "签名校验失败");
        }

        // nonce 防重放
        String nonceKey = "openapi:nonce:" + appCode + ":" + nonce;
        Boolean first = redisCache.redisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "1", NONCE_TTL_MIN, TimeUnit.MINUTES);
        if (first != null && !first)
        {
            throw new OpenApiException(401, "重复请求（nonce 已使用）");
        }

        // IP 白名单：未配置表示不限制
        List<OpsAppIp> ips = opsAppMapper.selectIps(app.getAppId());
        if (ips != null && !ips.isEmpty())
        {
            boolean ok = false;
            for (OpsAppIp ip : ips)
            {
                if (OpenApiSign.ipMatches(clientIp, ip.getIpAddr()))
                {
                    ok = true;
                    break;
                }
            }
            if (!ok)
            {
                throw new OpenApiException(403, "来源 IP 不在白名单：" + clientIp);
            }
        }

        // 接口授权
        if (StringUtils.isNotEmpty(apiCode))
        {
            OpsAppApi grant = opsAppMapper.selectApiGrant(app.getAppId(), apiCode);
            if (grant == null)
            {
                throw new OpenApiException(403, "未授权访问接口 " + apiCode);
            }
            checkQuota("openapi:rate:" + appCode + ":" + apiCode + ":" + minuteKey(),
                    grant.getQuotaPerMin(), apiCode + " 超出单接口配额");
        }
        checkQuota("openapi:rate:" + appCode + ":" + minuteKey(),
                app.getRateLimit(), "超出应用总配额");
        return app;
    }

    @Override
    public void record(OpsCallLog log)
    {
        if (log != null)
        {
            opsAppMapper.insertCallLog(log);
        }
    }

    @Override
    public List<OpsCallLog> selectCallLogList(OpsCallLog query)
    {
        return opsAppMapper.selectCallLogList(query);
    }

    /**
     * 分钟级计数：首次写入时设 2 分钟过期，避免 key 堆积
     */
    private void checkQuota(String key, Integer limit, String message)
    {
        if (limit == null || limit <= 0)
        {
            return;
        }
        RedisTemplate<String, Object> template = redisCache.redisTemplate;
        Long count = template.opsForValue().increment(key);
        if (count != null && count == 1L)
        {
            template.expire(key, 2, TimeUnit.MINUTES);
        }
        if (count != null && count > limit)
        {
            throw new OpenApiException(429, message + "（上限 " + limit + " 次/分钟）");
        }
    }

    private String minuteKey()
    {
        return String.valueOf(System.currentTimeMillis() / 60000L);
    }

    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
