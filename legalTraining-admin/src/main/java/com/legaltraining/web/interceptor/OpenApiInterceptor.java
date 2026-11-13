package com.legaltraining.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.business.ops.domain.OpsApp;
import com.legaltraining.business.ops.domain.OpsCallLog;
import com.legaltraining.business.ops.service.IOpsAppService;
import com.legaltraining.business.ops.support.OpenApiException;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.utils.ip.IpUtils;

/**
 * 开放接口网关（OP-03-01~04）
 *
 * 只拦截 /openapi/v1/**：
 *   1. 按请求路径映射 OP-02-xx 接口编号；
 *   2. AppID + 时间戳 + nonce + 签名 + IP 白名单 + 配额（由 service 完成）；
 *   3. 无论成功失败都写 ops_call_log，便于对外解释"为什么被拒"。
 */
@Component
public class OpenApiInterceptor implements HandlerInterceptor
{
    private static final String ATTR_START = "openapi.start";
    private static final String ATTR_APP = "openapi.app";
    private static final String ATTR_CODE = "openapi.code";

    @Autowired
    private IOpsAppService opsAppService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        request.setAttribute(ATTR_START, System.currentTimeMillis());
        String uri = request.getRequestURI();
        String apiCode = resolveApiCode(uri);
        request.setAttribute(ATTR_CODE, apiCode);

        String appCode = request.getHeader("X-App-Id");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String sign = request.getHeader("X-Sign");
        String clientIp = IpUtils.getIpAddr();

        try
        {
            OpsApp app = opsAppService.verify(appCode, timestamp, nonce, sign, uri, clientIp, apiCode);
            request.setAttribute(ATTR_APP, app);
            return true;
        }
        catch (OpenApiException e)
        {
            writeError(response, e.getStatus(), e.getMessage());
            log(request, clientIp, apiCode, null, e.getStatus(), e.getMessage());
            return false;
        }
        catch (Exception e)
        {
            writeError(response, 500, "开放接口内部错误");
            log(request, clientIp, apiCode, null, 500, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex)
    {
        Object app = request.getAttribute(ATTR_APP);
        if (app == null)
        {
            return;
        }
        String apiCode = (String) request.getAttribute(ATTR_CODE);
        String clientIp = IpUtils.getIpAddr();
        int status = response.getStatus() >= 400 ? response.getStatus() : 200;
        log(request, clientIp, apiCode, (OpsApp) app, status, ex == null ? null : ex.getMessage());
    }

    private void log(HttpServletRequest request, String clientIp, String apiCode,
                     OpsApp app, int status, String errorMsg)
    {
        try
        {
            OpsCallLog log = new OpsCallLog();
            if (app != null)
            {
                log.setAppId(app.getAppId());
                log.setAppCode(app.getAppCode());
            }
            else
            {
                // 鉴权失败时 app 可能为空，仍然记录 AppID 头便于排查
                log.setAppCode(request.getHeader("X-App-Id"));
            }
            log.setApiCode(apiCode);
            log.setRequestUri(request.getRequestURI());
            log.setMethod(request.getMethod());
            log.setClientIp(clientIp);
            log.setHttpStatus(status);
            Object start = request.getAttribute(ATTR_START);
            if (start instanceof Long)
            {
                log.setCostMs((int) (System.currentTimeMillis() - (Long) start));
            }
            log.setReqId(request.getHeader("X-Request-Id"));
            log.setErrorMsg(errorMsg != null && errorMsg.length() > 900
                    ? errorMsg.substring(0, 900) : errorMsg);
            opsAppService.record(log);
        }
        catch (Exception ignore)
        {
            // 日志失败不影响主流程
        }
    }

    private void writeError(HttpServletResponse response, int status, String message)
    {
        try
        {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(AjaxResult.error(status, message)));
        }
        catch (Exception ignore)
        {
            // 响应已提交时忽略
        }
    }

    /**
     * 路径 → OP-02-xx；未匹配返回 null（不校验接口授权，但配额仍然生效）
     */
    private String resolveApiCode(String uri)
    {
        if (uri == null)
        {
            return null;
        }
        if (uri.endsWith("/openapi/v1/zones"))
        {
            return "OP-02-01";
        }
        if (uri.contains("/openapi/v1/items/"))
        {
            return "OP-02-02";
        }
        if (uri.endsWith("/openapi/v1/publish-state"))
        {
            return "OP-02-03";
        }
        if (uri.endsWith("/openapi/v1/share"))
        {
            return "OP-02-04";
        }
        if (uri.endsWith("/openapi/v1/rec/push"))
        {
            return "OP-02-05";
        }
        if (uri.endsWith("/openapi/v1/rec/callback"))
        {
            return "OP-02-06";
        }
        if (uri.endsWith("/openapi/v1/device/token"))
        {
            return "OP-02-07";
        }
        if (uri.endsWith("/openapi/v1/cases"))
        {
            return "OP-02-08";
        }
        if (uri.endsWith("/openapi/v1/ai/consult"))
        {
            return "OP-02-09";
        }
        return null;
    }
}
