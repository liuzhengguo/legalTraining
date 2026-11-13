package com.legaltraining.business.ops.service;

import java.util.List;
import com.legaltraining.business.ops.domain.OpsApp;
import com.legaltraining.business.ops.domain.OpsAppApi;
import com.legaltraining.business.ops.domain.OpsAppIp;
import com.legaltraining.business.ops.domain.OpsCallLog;

/**
 * 应用接入（OP-03-01~05 / MG-02-04）
 */
public interface IOpsAppService
{
    public List<OpsApp> selectOpsAppList(OpsApp query);

    /** 详情：带接口清单与 IP 白名单 */
    public OpsApp selectOpsAppById(Long appId);

    /** 注册：返回明文密钥（仅此一次） */
    public OpsApp insertOpsApp(OpsApp app);

    public int updateOpsApp(OpsApp app);

    public int deleteOpsAppByIds(Long[] appIds);

    /** 重置密钥：旧密钥立即失效 */
    public String resetSecret(Long appId);

    public int saveApis(Long appId, List<OpsAppApi> apis);

    public int saveIps(Long appId, List<OpsAppIp> ips);

    /**
     * 开放接口鉴权：签名、状态、有效期、IP 白名单、限流
     *
     * @return 通过校验的应用
     */
    public OpsApp verify(String appCode, String timestamp, String nonce, String sign,
                         String uri, String clientIp, String apiCode);

    public void record(OpsCallLog log);

    public List<OpsCallLog> selectCallLogList(OpsCallLog query);
}
