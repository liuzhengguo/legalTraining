package com.legaltraining.web.controller.edu;

import java.util.List;
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
import com.legaltraining.business.edu.domain.EduGroup;
import com.legaltraining.business.edu.service.IEduGroupService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;

/**
 * 分组（E1 平台管理 / MG-01-03）
 */
@RestController
@RequestMapping("/edu/group")
public class EduGroupController extends BaseController
{
    @Autowired
    private IEduGroupService eduGroupService;

    @PreAuthorize("@ss.hasPermi('edu:group:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduGroup eduGroup)
    {
        startPage();
        List<EduGroup> list = eduGroupService.selectEduGroupList(eduGroup);
        return getDataTable(list);
    }

    /**
     * 跨班分组开关状态（前端据此决定学生选择器是否限定本班）
     */
    @PreAuthorize("@ss.hasPermi('edu:group:list')")
    @GetMapping("/cross-class")
    public AjaxResult crossClass()
    {
        return success(eduGroupService.isCrossClassEnabled());
    }

    @PreAuthorize("@ss.hasPermi('edu:group:query')")
    @GetMapping("/{groupId}")
    public AjaxResult getInfo(@PathVariable Long groupId)
    {
        return success(eduGroupService.selectEduGroupById(groupId));
    }

    @Log(title = "分组", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:group:add')")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody EduGroup eduGroup)
    {
        return toAjax(eduGroupService.insertEduGroup(eduGroup));
    }

    @Log(title = "分组", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:group:edit')")
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody EduGroup eduGroup)
    {
        return toAjax(eduGroupService.updateEduGroup(eduGroup));
    }

    @Log(title = "分组", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:group:remove')")
    @DeleteMapping("/{groupIds}")
    public AjaxResult remove(@PathVariable Long[] groupIds)
    {
        return toAjax(eduGroupService.deleteEduGroupByIds(groupIds));
    }
}
