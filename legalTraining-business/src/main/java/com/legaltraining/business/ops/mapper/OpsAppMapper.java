package com.legaltraining.business.ops.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.ops.domain.OpsApp;
import com.legaltraining.business.ops.domain.OpsAppApi;
import com.legaltraining.business.ops.domain.OpsAppIp;
import com.legaltraining.business.ops.domain.OpsCallLog;

/**
 * 应用接入 数据层（OP-03）
 */
public interface OpsAppMapper
{
    public List<OpsApp> selectOpsAppList(OpsApp query);

    public OpsApp selectOpsAppById(Long appId);

    public OpsApp selectByAppCode(String appCode);

    public int insertOpsApp(OpsApp app);

    public int updateOpsApp(OpsApp app);

    public int deleteOpsAppByIds(Long[] appIds);

    public int updateSecret(@Param("appId") Long appId, @Param("appSecret") String appSecret);

    public List<OpsAppApi> selectApis(Long appId);

    public int deleteApis(Long appId);

    public int batchInsertApis(@Param("list") List<OpsAppApi> list);

    public List<OpsAppIp> selectIps(Long appId);

    public int deleteIps(Long appId);

    public int batchInsertIps(@Param("list") List<OpsAppIp> list);

    /** 该应用是否被授权访问某接口（enable='1' 才算） */
    public OpsAppApi selectApiGrant(@Param("appId") Long appId, @Param("apiCode") String apiCode);

    public int insertCallLog(OpsCallLog log);

    public List<OpsCallLog> selectCallLogList(OpsCallLog query);
}
