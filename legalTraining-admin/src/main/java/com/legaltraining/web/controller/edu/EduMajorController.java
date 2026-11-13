package com.legaltraining.web.controller.edu;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduMajor;
import com.legaltraining.business.edu.service.IEduMajorService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.utils.poi.ExcelUtil;

/**
 * 专业（E1 平台管理 / MG-01-02 的院系专业维度）
 */
@RestController
@RequestMapping("/edu/major")
public class EduMajorController extends BaseController
{
    @Autowired
    private IEduMajorService eduMajorService;

    @PreAuthorize("@ss.hasPermi('edu:major:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduMajor eduMajor)
    {
        startPage();
        List<EduMajor> list = eduMajorService.selectEduMajorList(eduMajor);
        return getDataTable(list);
    }

    @Log(title = "专业", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('edu:major:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduMajor eduMajor)
    {
        List<EduMajor> list = eduMajorService.selectEduMajorList(eduMajor);
        ExcelUtil<EduMajor> util = new ExcelUtil<EduMajor>(EduMajor.class);
        util.exportExcel(response, list, "专业");
    }

    @PreAuthorize("@ss.hasPermi('edu:major:query')")
    @GetMapping("/{majorId}")
    public AjaxResult getInfo(@PathVariable Long majorId)
    {
        return success(eduMajorService.selectEduMajorById(majorId));
    }

    @Log(title = "专业", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:major:add')")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody EduMajor eduMajor)
    {
        return toAjax(eduMajorService.insertEduMajor(eduMajor));
    }

    @Log(title = "专业", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:major:edit')")
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody EduMajor eduMajor)
    {
        return toAjax(eduMajorService.updateEduMajor(eduMajor));
    }

    @Log(title = "专业", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:major:remove')")
    @DeleteMapping("/{majorIds}")
    public AjaxResult remove(@PathVariable Long[] majorIds)
    {
        return toAjax(eduMajorService.deleteEduMajorByIds(majorIds));
    }
}
