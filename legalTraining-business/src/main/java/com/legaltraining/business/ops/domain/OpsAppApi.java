package com.legaltraining.business.ops.domain;

/**
 * 可访问接口清单 ops_app_api（OP-03-02）
 */
public class OpsAppApi
{
    private Long id;

    private Long appId;

    /** OP-02-01 ~ OP-02-09 */
    private String apiCode;

    /** 该接口每分钟配额 */
    private Integer quotaPerMin;

    /** 1 启用 0 停用 */
    private String enable;

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

    public String getApiCode()
    {
        return apiCode;
    }

    public void setApiCode(String apiCode)
    {
        this.apiCode = apiCode;
    }

    public Integer getQuotaPerMin()
    {
        return quotaPerMin;
    }

    public void setQuotaPerMin(Integer quotaPerMin)
    {
        this.quotaPerMin = quotaPerMin;
    }

    public String getEnable()
    {
        return enable;
    }

    public void setEnable(String enable)
    {
        this.enable = enable;
    }
}
