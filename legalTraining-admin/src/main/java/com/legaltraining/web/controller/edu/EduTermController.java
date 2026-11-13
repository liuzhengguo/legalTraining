package com.legaltraining.web.controller.edu;

import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.service.IEduTermService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.utils.poi.ExcelUtil;

/**
 * 学年学期（E1 平台管理 / MG-01-01）
 */
@RestController
@RequestMapping("/edu/term")
public class EduTermController extends BaseController
{
    @Autowired
    private IEduTermService eduTermService;

    /**
     * 查询学年学期列表
     */
    @PreAuthorize("@ss.hasPermi('edu:term:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduTerm eduTerm)
    {
        startPage();
        List<EduTerm> list = eduTermService.selectEduTermList(eduTerm);
        return getDataTable(list);
    }

    /**
     * 导出学年学期
     */
    @Log(title = "学年学期", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('edu:term:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduTerm eduTerm)
    {
        List<EduTerm> list = eduTermService.selectEduTermList(eduTerm);
        ExcelUtil<EduTerm> util = new ExcelUtil<EduTerm>(EduTerm.class);
        util.exportExcel(response, list, "学年学期");
    }

    /**
     * 获取当前学期
     */
    @PreAuthorize("@ss.hasPermi('edu:term:list')")
    @GetMapping("/current")
    public AjaxResult current()
    {
        return success(eduTermService.selectCurrentTerm());
    }

    /**
     * 获取学年学期详情
     */
    @PreAuthorize("@ss.hasPermi('edu:term:query')")
    @GetMapping("/{termId}")
    public AjaxResult getInfo(@PathVariable Long termId)
    {
        return success(eduTermService.selectEduTermById(termId));
    }

    /**
     * 新增学年学期
     */
    @Log(title = "学年学期", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:term:add')")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody EduTerm eduTerm)
    {
        return toAjax(eduTermService.insertEduTerm(eduTerm));
    }

    /**
     * 修改学年学期
     */
    @Log(title = "学年学期", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:term:edit')")
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody EduTerm eduTerm)
    {
        return toAjax(eduTermService.updateEduTerm(eduTerm));
    }

    /**
     * 切换当前学期（返回"仍有 x 个任务进行中"提示）
     */
    @Log(title = "学年学期", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:term:switch')")
    @PutMapping("/{termId}/current")
    public AjaxResult switchCurrent(@PathVariable Long termId)
    {
        return success(eduTermService.switchCurrent(termId));
    }

    /**
     * 归档前置检查
     */
    @PreAuthorize("@ss.hasPermi('edu:term:archive')")
    @GetMapping("/{termId}/archive-check")
    public AjaxResult archiveCheck(@PathVariable Long termId)
    {
        return success(eduTermService.checkArchive(termId));
    }

    /**
     * 学期归档
     *
     * @param force  存在未批阅提交时是否强制归档
     * @param reason 强制归档原因
     */
    @Log(title = "学期归档", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:term:archive')")
    @PostMapping("/{termId}/archive")
    public AjaxResult archive(@PathVariable Long termId,
                              @RequestParam(defaultValue = "false") boolean force,
                              @RequestParam(required = false) String reason)
    {
        eduTermService.archive(termId, force, reason);
        return success("归档完成");
    }

    /**
     * 删除学年学期
     */
    @Log(title = "学年学期", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:term:remove')")
    @DeleteMapping("/{termIds}")
    public AjaxResult remove(@PathVariable Long[] termIds)
    {
        return toAjax(eduTermService.deleteEduTermByIds(termIds));
    }
}
