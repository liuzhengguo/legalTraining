package com.legaltraining.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取项目相关配置
 * 
 */
@Component
@ConfigurationProperties(prefix = "legal-training")
public class LegalTrainingConfig
{
    /** 项目名称 */
    private String name;

    /** 版本 */
    private String version;

    /** 版权年份 */
    private String copyrightYear;

    /** 上传路径 */
    private static String profile;

    /** 获取地址开关 */
    private static boolean addressEnabled;

    /** 验证码类型 */
    private static String captchaType;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getCopyrightYear()
    {
        return copyrightYear;
    }

    public void setCopyrightYear(String copyrightYear)
    {
        this.copyrightYear = copyrightYear;
    }

    public static String getProfile()
    {
        return profile;
    }

    public void setProfile(String profile)
    {
        LegalTrainingConfig.profile = profile;
    }

    public static boolean isAddressEnabled()
    {
        return addressEnabled;
    }

    public void setAddressEnabled(boolean addressEnabled)
    {
        LegalTrainingConfig.addressEnabled = addressEnabled;
    }

    public static String getCaptchaType() {
        return captchaType;
    }

    public void setCaptchaType(String captchaType) {
        LegalTrainingConfig.captchaType = captchaType;
    }

    /**
     * 获取导入上传路径
     */
    public static String getImportPath()
    {
        return getProfile() + "/import";
    }

    /**
     * 获取头像上传路径
     */
    public static String getAvatarPath()
    {
        return getProfile() + "/avatar";
    }

    /**
     * 获取下载路径
     */
    public static String getDownloadPath()
    {
        return getProfile() + "/download/";
    }

    /**
     * 获取上传路径（公开文件：展厅素材、导入文件等）
     */
    public static String getUploadPath()
    {
        return getProfile() + "/upload";
    }

    /**
     * 受控教学文件专用目录（教学资源、学生提交附件、任务模板）。
     * 该前缀下的文件禁止通过 /profile/** 静态直链访问，必须走带业务鉴权的接口读取（F01）。
     */
    public static String getSecureUploadPath()
    {
        return getUploadPath() + "/secure";
    }

    /** 受控文件在数据库中的 URL 前缀 */
    public static final String SECURE_UPLOAD_URL_PREFIX = "/profile/upload/secure/";
}
