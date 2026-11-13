package com.legaltraining.web.controller.edu;

import java.util.ArrayList;
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
import org.springframework.web.multipart.MultipartFile;
import com.legaltraining.business.edu.domain.EduImportBatch;
import com.legaltraining.business.edu.domain.EduImportRow;
import com.legaltraining.business.edu.domain.EduStudentImportVo;
import com.legaltraining.business.edu.service.IEduImportService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.utils.poi.ExcelUtil;

/**
 * 学生账号批量导入（E1 平台管理 / MG-01-04）
 *
 * 流程：下载模板 → 上传预检（不落库）→ 确认导入 → 失败清单下载。
 */
@RestController
@RequestMapping("/edu/import")
public class EduImportController extends BaseController
{
    @Autowired
    private IEduImportService eduImportService;

    /**
     * 下载导入模板（只有表头，四列：学号/姓名/班级编码/手机号）
     */
    @PreAuthorize("@ss.hasPermi('edu:student:import')")
    @GetMapping("/template")
    public void template(HttpServletResponse response)
    {
        ExcelUtil<EduStudentImportVo> util = new ExcelUtil<EduStudentImportVo>(EduStudentImportVo.class);
        util.exportExcel(response, new ArrayList<EduStudentImportVo>(), "学生导入模板");
    }

    /**
     * 上传并预检，不创建账号
     *
     * @param file   模板文件（.xlsx）
     * @param termId 目标学期，为空取当前学期
     */
    @Log(title = "学生导入", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('edu:student:import')")
    @PostMapping("/preview")
    public AjaxResult preview(@RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) Long termId)
    {
        return success(eduImportService.preview(file, termId));
    }

    /**
     * 确认导入（失败率超过 30% 时整批不落库）
     */
    @Log(title = "学生导入", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('edu:student:import')")
    @PostMapping("/{batchId}/confirm")
    public AjaxResult confirm(@PathVariable Long batchId)
    {
        return success(eduImportService.confirm(batchId));
    }

    @PreAuthorize("@ss.hasPermi('edu:student:import')")
    @GetMapping("/{batchId}")
    public AjaxResult getInfo(@PathVariable Long batchId)
    {
        return success(eduImportService.selectBatchById(batchId));
    }

    @PreAuthorize("@ss.hasPermi('edu:student:import')")
    @GetMapping("/list")
    public TableDataInfo list(EduImportBatch query)
    {
        startPage();
        List<EduImportBatch> list = eduImportService.selectBatchList(query);
        return getDataTable(list);
    }

    /**
     * 失败清单（JSON，前端表格直接展示）
     */
    @PreAuthorize("@ss.hasPermi('edu:student:import')")
    @GetMapping("/{batchId}/fail-list")
    public AjaxResult failList(@PathVariable Long batchId)
    {
        return success(eduImportService.selectFailRows(batchId));
    }

    /**
     * 失败清单导出（修正后可原样重传）
     */
    @Log(title = "学生导入失败清单", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('edu:student:import')")
    @PostMapping("/{batchId}/fail-list/export")
    public void exportFailList(HttpServletResponse response, @PathVariable Long batchId)
    {
        List<EduImportRow> list = eduImportService.selectFailRows(batchId);
        ExcelUtil<EduImportRow> util = new ExcelUtil<EduImportRow>(EduImportRow.class);
        util.exportExcel(response, list, "导入失败清单");
    }
}
