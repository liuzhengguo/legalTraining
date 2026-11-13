package com.legaltraining.web.controller.edu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.legaltraining.business.edu.domain.EduResource;
import com.legaltraining.business.edu.domain.EduResourceCategory;
import com.legaltraining.business.edu.service.IEduResourceService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.enums.BusinessType;

/**
 * 教学资源（TC-02-01~04）
 */
@RestController
@RequestMapping("/edu/resource")
public class EduResourceController extends BaseController
{
    @Autowired
    private IEduResourceService eduResourceService;

    @PreAuthorize("@ss.hasPermi('edu:resource:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduResource eduResource)
    {
        startPage();
        List<EduResource> list = eduResourceService.selectEduResourceList(eduResource);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('edu:resource:query')")
    @GetMapping("/{resourceId}")
    public AjaxResult getInfo(@PathVariable Long resourceId)
    {
        return success(eduResourceService.selectEduResourceById(resourceId));
    }

    /**
     * 登记资源（TC-02-01）
     * 文件先走文件服务上传，拿到 file_id 后再调本接口登记；外链资源直接传 externalUrl。
     */
    @Log(title = "教学资源", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:resource:add')")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody EduResource eduResource)
    {
        return toAjax(eduResourceService.insertEduResource(eduResource));
    }

    /**
     * 修改（部分字段更新，不做整体 Bean 校验，类型必填项在服务层合并后校验）
     */
    @Log(title = "教学资源", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:resource:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody EduResource eduResource)
    {
        return toAjax(eduResourceService.updateEduResource(eduResource));
    }

    @Log(title = "教学资源", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:resource:remove')")
    @DeleteMapping("/{resourceId}")
    public AjaxResult remove(@PathVariable Long resourceId)
    {
        return toAjax(eduResourceService.deleteEduResourceById(resourceId));
    }

    /**
     * 上架/下架（TC-02-03）
     */
    @Log(title = "资源上下架", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:resource:edit')")
    @PutMapping("/{resourceId}/status")
    public AjaxResult changeStatus(@PathVariable Long resourceId, @RequestParam Integer status)
    {
        return toAjax(eduResourceService.changeStatus(resourceId, status));
    }

    /**
     * 下载授权（TC-02-04）
     * allow_download='0' 时 downloadable=false，只返回在线预览地址。
     */
    @PreAuthorize("@ss.hasPermi('edu:resource:query')")
    @GetMapping("/{resourceId}/download")
    public AjaxResult download(@PathVariable Long resourceId)
    {
        return success(eduResourceService.authorizeDownload(resourceId));
    }

    /**
     * 资源关联到课程
     */
    @Log(title = "资源关联课程", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:resource:edit')")
    @PostMapping("/{resourceId}/course/{courseId}")
    public AjaxResult linkCourse(@PathVariable Long resourceId, @PathVariable Long courseId)
    {
        return toAjax(eduResourceService.linkCourse(courseId, resourceId));
    }

    @Log(title = "资源解除课程关联", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:resource:edit')")
    @DeleteMapping("/{resourceId}/course/{courseId}")
    public AjaxResult unlinkCourse(@PathVariable Long resourceId, @PathVariable Long courseId)
    {
        return toAjax(eduResourceService.unlinkCourse(courseId, resourceId));
    }

    /**
     * 某课程下的资源清单（学生端课程资源列表也走这里）
     */
    @PreAuthorize("@ss.hasPermi('edu:resource:list')")
    @GetMapping("/course/{courseId}")
    public AjaxResult byCourse(@PathVariable Long courseId)
    {
        return success(eduResourceService.selectResourcesByCourse(courseId));
    }

    /**
     * 重算引用计数（任务关联调整后可由前端触发校准）
     */
    @PreAuthorize("@ss.hasPermi('edu:resource:edit')")
    @PutMapping("/{resourceId}/recount")
    public AjaxResult recount(@PathVariable Long resourceId)
    {
        return toAjax(eduResourceService.recountRef(resourceId));
    }

    // ==================== 分类（TC-02-02） ====================

    @PreAuthorize("@ss.hasPermi('edu:resource:list')")
    @GetMapping("/category/tree")
    public AjaxResult categoryTree(EduResourceCategory query)
    {
        return success(eduResourceService.selectCategoryTree(query));
    }

    @PreAuthorize("@ss.hasPermi('edu:resource:query')")
    @GetMapping("/category/{categoryId}")
    public AjaxResult categoryInfo(@PathVariable Long categoryId)
    {
        return success(eduResourceService.selectCategoryById(categoryId));
    }

    @Log(title = "资源分类", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('edu:resource:add')")
    @PostMapping("/category")
    public AjaxResult addCategory(@Validated @RequestBody EduResourceCategory category)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        int rows = eduResourceService.insertCategory(category);
        result.put("rows", rows);
        result.put("categoryId", category.getCategoryId());
        return success(result);
    }

    @Log(title = "资源分类", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:resource:edit')")
    @PutMapping("/category")
    public AjaxResult editCategory(@Validated @RequestBody EduResourceCategory category)
    {
        return toAjax(eduResourceService.updateCategory(category));
    }

    @Log(title = "资源分类", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('edu:resource:remove')")
    @DeleteMapping("/category/{categoryId}")
    public AjaxResult removeCategory(@PathVariable Long categoryId)
    {
        return toAjax(eduResourceService.deleteCategoryById(categoryId));
    }
}
