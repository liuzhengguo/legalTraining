package com.legaltraining.business.ops.domain;

import java.util.Date;
import java.util.List;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 应用注册 ops_app（OP-03-01）
 *
 * app_secret 只存 MD5 摘要，任何接口都不回显；签名用的 signKey 就是该摘要，
 * 因此密钥泄露风险停留在"可撤销、可重置"的层面，不依赖数据库加密。
 */
public class OpsApp extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long appId;

    /** AppID（对外标识，系统生成） */
    @Excel(name = "AppID")
    private String appCode;

    /** AppSecret 的 MD5（不对外返回） */
    private String appSecret;

    @Excel(name = "应用名称")
    private String appName;

    @Excel(name = "所属单位")
    private String ownerUnit;

    @Excel(name = "联系人")
    private String contact;

    /** 0 待启用 1 启用 2 停用 */
    @Excel(name = "状态", readConverterExp = "0=待启用,1=启用,2=停用")
    private Integer status;

    @Excel(name = "密钥有效期", dateFormat = "yyyy-MM-dd HH:mm:ss", width = 30)
    private Date expiredAt;

    /** 每分钟总配额 */
    @Excel(name = "每分钟配额")
    private Integer rateLimit;

    /** 新建或重置时返回一次明文密钥 */
    private String secretPlain;

    /** 可访问接口清单 */
    private List<OpsAppApi> apiList;

    /** IP 白名单 */
    private List<OpsAppIp> ipList;

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

    public String getAppSecret()
    {
        return appSecret;
    }

    public void setAppSecret(String appSecret)
    {
        this.appSecret = appSecret;
    }

    public String getAppName()
    {
        return appName;
    }

    public void setAppName(String appName)
    {
        this.appName = appName;
    }

    public String getOwnerUnit()
    {
        return ownerUnit;
    }

    public void setOwnerUnit(String ownerUnit)
    {
        this.ownerUnit = ownerUnit;
    }

    public String getContact()
    {
        return contact;
    }

    public void setContact(String contact)
    {
        this.contact = contact;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Date getExpiredAt()
    {
        return expiredAt;
    }

    public void setExpiredAt(Date expiredAt)
    {
        this.expiredAt = expiredAt;
    }

    public Integer getRateLimit()
    {
        return rateLimit;
    }

    public void setRateLimit(Integer rateLimit)
    {
        this.rateLimit = rateLimit;
    }

    public String getSecretPlain()
    {
        return secretPlain;
    }

    public void setSecretPlain(String secretPlain)
    {
        this.secretPlain = secretPlain;
    }

    public List<OpsAppApi> getApiList()
    {
        return apiList;
    }

    public void setApiList(List<OpsAppApi> apiList)
    {
        this.apiList = apiList;
    }

    public List<OpsAppIp> getIpList()
    {
        return ipList;
    }

    public void setIpList(List<OpsAppIp> ipList)
    {
        this.ipList = ipList;
    }
}
