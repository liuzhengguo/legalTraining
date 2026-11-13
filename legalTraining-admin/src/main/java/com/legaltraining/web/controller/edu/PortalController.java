package com.legaltraining.web.controller.edu;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.service.IPortalService;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;

/**
 * 统一门户（E8 / OP-01-01~03）
 */
@RestController
@RequestMapping("/portal")
public class PortalController extends BaseController
{
    @Autowired
    private IPortalService portalService;

    /**
     * 门户首页：账号、当前学期、按权限过滤的入口卡片、待办计数
     */
    @PreAuthorize("@ss.hasPermi('portal:index:view')")
    @GetMapping("/index")
    public AjaxResult index()
    {
        return success(portalService.index());
    }

    /**
     * 我的待办（门户卡片与顶栏红点共用）
     */
    @PreAuthorize("@ss.hasPermi('portal:todo:list')")
    @GetMapping("/todo")
    public AjaxResult todo()
    {
        return success(portalService.todo());
    }
}
