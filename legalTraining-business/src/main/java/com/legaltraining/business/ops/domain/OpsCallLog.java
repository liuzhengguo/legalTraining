package com.legaltraining.business.ops.domain;

import java.util.Date;
import com.legaltraining.common.annotation.Excel;

/**
 * 开放接口调用日志 ops_call_log（OP-03-04）
 */
public class OpsCallLog
{
    private Long id;

    private Long appId;

    @Excel(name = "AppID")
    private String appCode;

    @Excel(name = "接口")
    private String apiCode;

    @Excel(name = "请求路径")
    private String requestUri;

    private String method;

    @Excel(name = "来源IP")
    private String clientIp;

    @Excel(name = "HTTP状态")
    private Integer httpStatus;

    @Excel(name = "耗时(ms)")
    private Integer costMs;

    private String reqId;

    @Excel(name = "错误信息", width = 40)
    private String errorMsg;

    @Excel(name = "调用时间", dateFormat = "yyyy-MM-dd HH:mm:ss", width = 30)
    private Date callTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getAppId()
    {
        return appId;
    }

    public void setAppId(Long appId)
    {
        this.appId = appId;
    }

    public String getAppCode()
    {
        return appCode;
    }

    public void setAppCode(String appCode)
    {
        this.appCode = appCode;
    }

    public String getApiCode()
    {
        return apiCode;
    }

    public void setApiCode(String apiCode)
    {
        this.apiCode = apiCode;
    }

    public String getRequestUri()
    {
        return requestUri;
    }

    public void setRequestUri(String requestUri)
    {
        this.requestUri = requestUri;
    }

    public String getMethod()
    {
        return method;
    }

    public void setMethod(String method)
    {
        this.method = method;
    }

    public String getClientIp()
    {
        return clientIp;
    }

    public void setClientIp(String clientIp)
    {
        this.clientIp = clientIp;
    }

    public Integer getHttpStatus()
    {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus)
    {
        this.httpStatus = httpStatus;
    }

    public Integer getCostMs()
    {
        return costMs;
    }

    public void setCostMs(Integer costMs)
    {
        this.costMs = costMs;
    }

    public String getReqId()
    {
        return reqId;
    }

    public void setReqId(String reqId)
    {
        this.reqId = reqId;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public Date getCallTime()
    {
        return callTime;
    }

    public void setCallTime(Date callTime)
    {
        this.callTime = callTime;
    }
}
