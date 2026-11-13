package com.legaltraining.web.controller.ops;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.ops.domain.OpsApp;
import com.legaltraining.business.ops.domain.OpsAppApi;
import com.legaltraining.business.ops.domain.OpsAppIp;
import com.legaltraining.business.ops.domain.OpsCallLog;
import com.legaltraining.business.ops.service.IOpsAppService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.utils.poi.ExcelUtil;

/**
 * 应用接入与开放接口授权（E1 平台管理 / MG-02-04、OP-03）
 */
@RestController
@RequestMapping("/ops/app")
public class OpsAppController extends BaseController
{
    @Autowired
    private IOpsAppService opsAppService;

    @PreAuthorize("@ss.hasPermi('ops:app:list')")
    @GetMapping("/list")
    public TableDataInfo list(OpsApp query)
    {
        startPage();
        List<OpsApp> list = opsAppService.selectOpsAppList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('ops:app:query')")
    @GetMapping("/{appId}")
    public AjaxResult getInfo(@PathVariable Long appId)
    {
        return success(opsAppService.selectOpsAppById(appId));
    }

    /**
     * 注册应用：响应的 secretPlain 只出现这一次，请当场保存
     */
    @Log(title = "应用接入", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('ops:app:add')")
    @PostMapping
    public AjaxResult add(@RequestBody OpsApp app)
    {
        OpsApp created = opsAppService.insertOpsApp(app);
        return AjaxResult.success("应用已创建，AppSecret 仅显示一次", created);
    }

    @Log(title = "应用接入", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('ops:app:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody OpsApp app)
    {
        return toAjax(opsAppService.updateOpsApp(app));
    }

    /**
     * 启用/停用（1 启用 / 2 停用）
     */
    @Log(title = "应用接入", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('ops:app:edit')")
    @PutMapping("/{appId}/status")
    public AjaxResult changeStatus(@PathVariable Long appId, @RequestParam Integer status)
    {
        OpsApp update = new OpsApp();
        update.setAppId(appId);
        update.setStatus(status);
        return toAjax(opsAppService.updateOpsApp(update));
    }

    /**
     * 重置密钥：旧密钥立即失效
     */
    @Log(title = "应用密钥", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('ops:app:resetSecret')")
    @PutMapping("/{appId}/reset-secret")
    public AjaxResult resetSecret(@PathVariable Long appId)
    {
        return AjaxResult.success("密钥已重置，旧密钥立即失效", opsAppService.resetSecret(appId));
    }

    /**
     * 保存可访问接口清单（覆盖式）
     */
    @Log(title = "接口授权", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('ops:app:edit')")
    @PutMapping("/{appId}/apis")
    public AjaxResult saveApis(@PathVariable Long appId, @RequestBody List<OpsAppApi> apis)
    {
        return toAjax(opsAppService.saveApis(appId, apis));
    }

    /**
     * 保存 IP 白名单（覆盖式，空数组表示不限制）
     */
    @Log(title = "IP白名单", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('ops:app:edit')")
    @PutMapping("/{appId}/ips")
    public AjaxResult saveIps(@PathVariable Long appId, @RequestBody List<OpsAppIp> ips)
    {
        return toAjax(opsAppService.saveIps(appId, ips));
    }

    @Log(title = "应用接入", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('ops:app:remove')")
    @DeleteMapping("/{appIds}")
    public AjaxResult remove(@PathVariable Long[] appIds)
    {
        return toAjax(opsAppService.deleteOpsAppByIds(appIds));
    }

    /**
     * 开放接口调用日志（排障：为什么被拒）
     */
    @PreAuthorize("@ss.hasPermi('ops:app:log')")
    @GetMapping("/log")
    public TableDataInfo logList(OpsCallLog query)
    {
        startPage();
        List<OpsCallLog> list = opsAppService.selectCallLogList(query);
        return getDataTable(list);
    }

    @Log(title = "开放接口日志", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('ops:app:log')")
    @PostMapping("/log/export")
    public void exportLog(HttpServletResponse response, OpsCallLog query)
    {
        List<OpsCallLog> list = opsAppService.selectCallLogList(query);
        ExcelUtil<OpsCallLog> util = new ExcelUtil<OpsCallLog>(OpsCallLog.class);
        util.exportExcel(response, list, "开放接口调用日志");
    }
}
