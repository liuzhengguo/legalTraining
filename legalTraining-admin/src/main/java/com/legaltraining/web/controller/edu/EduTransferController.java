package com.legaltraining.web.controller.edu;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduMembership;
import com.legaltraining.business.edu.domain.EduTransferLog;
import com.legaltraining.business.edu.service.IEduTransferService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.utils.poi.ExcelUtil;

/**
 * 转班、转组与历史归属（E1 平台管理 / MG-01-06）
 */
@RestController
@RequestMapping("/edu/transfer")
public class EduTransferController extends BaseController
{
    @Autowired
    private IEduTransferService eduTransferService;

    /**
     * 转班
     */
    @Log(title = "学生转班", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:student:transfer')")
    @PostMapping("/class")
    public AjaxResult transferClass(@RequestParam Long userId,
                                    @RequestParam Long toClassId,
                                    @RequestParam(required = false) String remark)
    {
        return success(eduTransferService.transferClass(userId, toClassId, remark));
    }

    /**
     * 转组（同班内）
     */
    @Log(title = "学生转组", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:student:transfer')")
    @PostMapping("/group")
    public AjaxResult transferGroup(@RequestParam Long userId,
                                    @RequestParam Long fromGroupId,
                                    @RequestParam Long toGroupId,
                                    @RequestParam(required = false) String remark)
    {
        return success(eduTransferService.transferGroup(userId, fromGroupId, toGroupId, remark));
    }

    /**
     * 归属记录：currentOnly=true 当前归属，false 全部历史（含转班前后）
     */
    @PreAuthorize("@ss.hasPermi('edu:student:query')")
    @GetMapping("/memberships/{userId}")
    public AjaxResult memberships(@PathVariable Long userId,
                                  @RequestParam(defaultValue = "false") boolean currentOnly)
    {
        List<EduMembership> list = eduTransferService.selectMemberships(userId, currentOnly);
        return success(list);
    }

    /**
     * 转班/转组记录
     */
    @PreAuthorize("@ss.hasPermi('edu:student:list')")
    @GetMapping("/logs")
    public TableDataInfo logs(EduTransferLog query)
    {
        startPage();
        List<EduTransferLog> list = eduTransferService.selectTransferLogs(query);
        return getDataTable(list);
    }

    @Log(title = "转班记录", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('edu:student:export')")
    @PostMapping("/logs/export")
    public void exportLogs(HttpServletResponse response, EduTransferLog query)
    {
        List<EduTransferLog> list = eduTransferService.selectTransferLogs(query);
        ExcelUtil<EduTransferLog> util = new ExcelUtil<EduTransferLog>(EduTransferLog.class);
        util.exportExcel(response, list, "转班转组记录");
    }
}
