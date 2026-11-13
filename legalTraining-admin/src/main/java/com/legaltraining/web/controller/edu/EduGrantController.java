package com.legaltraining.web.controller.edu;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.scope.BusinessScopeChecker;
import com.legaltraining.business.edu.service.IEduGrantService;
import com.legaltraining.common.annotation.Log;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.domain.entity.SysRole;
import com.legaltraining.common.enums.BusinessType;

/**
 * 账号授权（E1 平台管理 / MG-02-02、MG-02-03）
 */
@RestController
@RequestMapping("/edu/grant")
public class EduGrantController extends BaseController
{
    @Autowired
    private IEduGrantService eduGrantService;

    @Autowired
    private BusinessScopeChecker businessScope;

    @PreAuthorize("@ss.hasPermi('edu:grant:list')")
    @GetMapping("/user-role/{userId}")
    public AjaxResult userRoles(@PathVariable Long userId)
    {
        List<SysRole> roles = eduGrantService.selectUserRoles(userId);
        return success(roles);
    }

    /**
     * 分配角色（覆盖式）
     *
     * @param transferToUserId 撤销教师/审核员且存在待办时必须提供
     */
    @Log(title = "账号授权", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('edu:grant:edit')")
    @PutMapping("/user-role")
    public AjaxResult assignRoles(@RequestParam Long userId,
                                  @RequestParam(required = false) Long[] roleIds,
                                  @RequestParam(required = false) Long transferToUserId)
    {
        return success(eduGrantService.assignRoles(userId, roleIds, transferToUserId));
    }

    /**
     * 数据范围自检：当前登录账号是否有权访问该班级
     */
    @GetMapping("/scope/class/{classId}")
    public AjaxResult checkClassScope(@PathVariable Long classId)
    {
        return success(businessScope.canAccessClass(classId));
    }

    /**
     * 数据范围自检：当前登录账号是否有权访问该课程
     */
    @GetMapping("/scope/course/{courseId}")
    public AjaxResult checkCourseScope(@PathVariable Long courseId)
    {
        return success(businessScope.canAccessCourse(courseId));
    }
}
