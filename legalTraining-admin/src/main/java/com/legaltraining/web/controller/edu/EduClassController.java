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
import com.legaltraining.business.edu.domain.EduClass;
import com.legaltraining.business.edu.domain.EduMajor;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.service.IEduClassService;
import com.legaltraining.business.edu.service.IEduMajorService;
import com.legaltraining.business.edu.service.IEduTermService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.domain.entity.SysDept;
import com.legaltraining.common.core.domain.entity.SysRole;
import com.legaltraining.common.core.domain.entity.SysUser;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;
import com.legaltraining.common.utils.poi.ExcelUtil;
import com.legaltraining.system.service.ISysDeptService;
import com.legaltraining.system.service.ISysRoleService;
import com.legaltraining.system.service.ISysUserService;

/**
 * 班级（E1 平台管理 / MG-01-02）
 */
@RestController
@RequestMapping("/edu/class")
public class EduClassController extends BaseController
{
    @Autowired
    private IEduClassService eduClassService;

    @Autowired
    private IEduTermService termService;

    @Autowired
    private IEduMajorService majorService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysUserService userService;

    @PreAuthorize("@ss.hasAnyPermi('edu:class:list,edu:class:add,edu:class:edit')")
    @GetMapping("/editor-options")
    public AjaxResult editorOptions()
    {
        SysDept departments = new SysDept();
        departments.setStatus("0");
        EduMajor majors = new EduMajor();
        majors.setStatus("0");
        SysRole teacherRole = new SysRole();
        teacherRole.setRoleKey("teacher");
        teacherRole.setStatus("0");
        List<?> teachers = roleService.selectRoleList(teacherRole).stream()
                .filter(role -> "teacher".equals(role.getRoleKey()))
                .flatMap(role -> {
                    SysUser query = new SysUser();
                    query.setRoleId(role.getRoleId());
                    return userService.selectAllocatedList(query).stream();
                })
                .filter(user -> "0".equals(user.getStatus()))
                .map(user -> Map.of("value", user.getUserId(), "label", user.getNickName() + "（" + user.getUserName() + "）"))
                .toList();
        return success(Map.of(
                "termId", termService.selectEduTermList(new EduTerm()).stream()
                        .filter(term -> !Integer.valueOf(2).equals(term.getStatus()))
                        .map(term -> Map.of("value", term.getTermId(), "label", term.getTermName())).toList(),
                "deptId", deptService.selectDeptList(departments).stream()
                        .map(dept -> Map.of("value", dept.getDeptId(), "label", dept.getDeptName())).toList(),
                "majorId", majorService.selectEduMajorList(majors).stream()
                        .map(major -> Map.of("value", major.getMajorId(), "label", major.getMajorName(), "deptId", major.getDeptId())).toList(),
                "headTeacher", teachers));
    }

    @PreAuthorize("@ss.hasPermi('edu:class:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduClass eduClass)
    {
        startPage();
        List<EduClass> list = eduClassService.selectEduClassList(eduClass);
        return getDataTable(list);
    }

    /**
     * 按学期取下拉列表（导入、转班、门户筛选共用）
     */
    @PreAuthorize("@ss.hasPermi('edu:class:list')")
    @GetMapping("/option/{termId}")
    public AjaxResult options(@PathVariable Long termId)
    {
        EduClass query = new EduClass();
        query.setTermId(termId);
        query.setStatus(0);
        return success(eduClassService.selectEduClassList(query));
    }

    @Log(title = "班级", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('edu:class:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduClass eduClass)
    {
        List<EduClass> list = eduClassService.selectEduClassList(eduClass);
        ExcelUtil<EduClass> util = new ExcelUtil<EduClass>(EduClass.class);
        util.exportExcel(response, list, "班级");
    }

    @PreAuthorize("@ss.hasPermi('edu:class:query')")
    @GetMapping("/{classId}")
    public AjaxResult getInfo(@PathVariable Long classId)
    {
        return success(eduClassService.selectEduClassById(classId));
    }

    @Log(title = "班级", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:class:add')")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody EduClass eduClass)
    {
        return toAjax(eduClassService.insertEduClass(eduClass));
    }

    @Log(title = "班级", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:class:edit')")
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody EduClass eduClass)
    {
        return toAjax(eduClassService.updateEduClass(eduClass));
    }

    /**
     * 启用/归档
     */
    @Log(title = "班级", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:class:edit')")
    @PutMapping("/{classId}/status")
    public AjaxResult changeStatus(@PathVariable Long classId, @RequestParam Integer status)
    {
        return success(eduClassService.changeStatus(classId, status));
    }

    /**
     * 重算在册人数（成员变更后的兜底接口）
     */
    @PreAuthorize("@ss.hasPermi('edu:class:edit')")
    @PutMapping("/{classId}/recount")
    public AjaxResult recount(@PathVariable Long classId)
    {
        return success(eduClassService.recountStudentCount(classId));
    }

    @Log(title = "班级", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:class:remove')")
    @DeleteMapping("/{classIds}")
    public AjaxResult remove(@PathVariable Long[] classIds)
    {
        return toAjax(eduClassService.deleteEduClassByIds(classIds));
    }
}
