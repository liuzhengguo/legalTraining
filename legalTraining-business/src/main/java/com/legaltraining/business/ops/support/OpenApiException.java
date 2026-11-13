package com.legaltraining.business.ops.support;

/**
 * 开放接口异常：携带 HTTP 状态码，供拦截器直接回写
 *
 * 401 签名/AppID 问题；403 应用状态、IP 白名单、接口未授权；429 超配额；410 内容已下线。
 */
public class OpenApiException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int status;

    public OpenApiException(int status, String message)
    {
        super(message);
        this.status = status;
    }

    public int getStatus()
    {
        return status;
    }
}
