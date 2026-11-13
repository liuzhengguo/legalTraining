package com.legaltraining.web.controller.edu;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.domain.EduStudent;
import com.legaltraining.business.edu.service.IEduStudentService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.utils.poi.ExcelUtil;

/**
 * 学生账号（E1 平台管理 / MG-01-05）
 *
 * 批量导入在 EduImportController（/edu/import），本控制器只做单条新增、启停、重置密码。
 */
@RestController
@RequestMapping("/edu/student")
public class EduStudentController extends BaseController
{
    @Autowired
    private IEduStudentService eduStudentService;

    @PreAuthorize("@ss.hasPermi('edu:student:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduStudent query)
    {
        startPage();
        List<EduStudent> list = eduStudentService.selectStudentList(query);
        return getDataTable(list);
    }

    @Log(title = "学生账号", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('edu:student:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduStudent query)
    {
        List<EduStudent> list = eduStudentService.selectStudentList(query);
        ExcelUtil<EduStudent> util = new ExcelUtil<EduStudent>(EduStudent.class);
        util.exportExcel(response, list, "学生账号");
    }

    @PreAuthorize("@ss.hasPermi('edu:student:query')")
    @GetMapping("/{userId}")
    public AjaxResult getInfo(@PathVariable Long userId)
    {
        return success(eduStudentService.selectStudentByUserId(userId));
    }

    /**
     * 单条新增学生账号
     */
    @Log(title = "学生账号", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:student:add')")
    @PostMapping
    public AjaxResult add(@RequestBody EduStudent student)
    {
        return success(eduStudentService.addStudent(student));
    }

    /**
     * 启停账号（0 正常 / 1 停用）
     */
    @Log(title = "学生账号", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:student:edit')")
    @PutMapping("/{userId}/status")
    public AjaxResult changeStatus(@PathVariable Long userId, @RequestParam String status)
    {
        return toAjax(eduStudentService.changeStatus(userId, status));
    }

    /**
     * 重置密码：返回初始密码明文，仅本次响应可见
     */
    @Log(title = "学生账号", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:student:resetPwd')")
    @PutMapping("/{userId}/reset-pwd")
    public AjaxResult resetPwd(@PathVariable Long userId)
    {
        return AjaxResult.success("密码已重置", eduStudentService.resetPassword(userId));
    }
}
