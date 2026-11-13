package com.legaltraining.business.ops.domain;

/**
 * IP 白名单 ops_app_ip（OP-03-02）
 */
public class OpsAppIp
{
    private Long id;

    private Long appId;

    /** 单 IP 或 CIDR，如 10.0.0.0/24 */
    private String ipAddr;

    private String remark;

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

    public String getIpAddr()
    {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr)
    {
        this.ipAddr = ipAddr;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
