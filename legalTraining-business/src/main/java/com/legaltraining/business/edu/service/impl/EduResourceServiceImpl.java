package com.legaltraining.business.edu.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.legaltraining.business.edu.domain.EduResource;
import com.legaltraining.business.edu.domain.EduResourceCategory;
import com.legaltraining.business.edu.mapper.EduResourceMapper;
import com.legaltraining.business.edu.service.IEduResourceService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 教学资源 服务实现
 *
 * 关键规则：
 * 1) 资源类型决定必填项：link 必须有 externalUrl，其余必须有 fileId。
 * 2) 引用计数 ref_count > 0 时不允许删除，只能下架（TC-02-03）。
 * 3) 已关联课程的资源不允许删除，先解除课程关联。
 * 4) allow_download='0' 时下载接口返回 403 语义的提示，只给在线预览地址（TC-02-04）。
 * 5) 教师只能维护自己上传的资源；管理员不受限。
 */
@Service
public class EduResourceServiceImpl implements IEduResourceService
{
    private static final int STATUS_ON = 0;
    private static final int STATUS_OFF = 1;

    private static final String SCOPE_SELF = "0";
    private static final String SCOPE_COURSE = "1";
    private static final String SCOPE_CLASS = "2";
    private static final String SCOPE_ALL = "3";

    @Autowired
    private EduResourceMapper eduResourceMapper;

    @Override
    public EduResource selectEduResourceById(Long resourceId)
    {
        return eduResourceMapper.selectEduResourceById(resourceId);
    }

    @Override
    public List<EduResource> selectEduResourceList(EduResource eduResource)
    {
        return eduResourceMapper.selectEduResourceList(eduResource);
    }

    @Override
    public int insertEduResource(EduResource eduResource)
    {
        checkTypeFields(eduResource);
        if (eduResource.getVisibleScope() == null)
        {
            eduResource.setVisibleScope(1);
        }
        if (StringUtils.isEmpty(eduResource.getAllowDownload()))
        {
            eduResource.setAllowDownload("1");
        }
        if (eduResource.getStatus() == null)
        {
            eduResource.setStatus(STATUS_ON);
        }
        eduResource.setOwnerId(currentUserId());
        eduResource.setRefCount(0);
        int rows = eduResourceMapper.insertEduResource(eduResource);
        if (eduResource.getCourseId() != null)
        {
            eduResourceMapper.insertCourseResource(eduResource.getCourseId(), eduResource.getResourceId(), 0);
        }
        return rows;
    }

    @Override
    public int updateEduResource(EduResource eduResource)
    {
        EduResource exist = mustExist(eduResource.getResourceId());
        checkOwner(exist);
        if (StringUtils.isNotEmpty(eduResource.getResType()) || StringUtils.isNotEmpty(eduResource.getExternalUrl()))
        {
            EduResource merged = new EduResource();
            merged.setResType(StringUtils.isNotEmpty(eduResource.getResType())
                    ? eduResource.getResType() : exist.getResType());
            merged.setFileId(StringUtils.isNotEmpty(eduResource.getFileId())
                    ? eduResource.getFileId() : exist.getFileId());
            merged.setExternalUrl(StringUtils.isNotEmpty(eduResource.getExternalUrl())
                    ? eduResource.getExternalUrl() : exist.getExternalUrl());
            checkTypeFields(merged);
        }
        return eduResourceMapper.updateEduResource(eduResource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteEduResourceById(Long resourceId)
    {
        EduResource exist = mustExist(resourceId);
        checkOwner(exist);
        eduResourceMapper.recountRef(resourceId);
        EduResource latest = eduResourceMapper.selectEduResourceById(resourceId);
        if (latest != null && latest.getRefCount() != null && latest.getRefCount() > 0)
        {
            throw new ServiceException("该资源已被 " + latest.getRefCount()
                    + " 个任务引用，不可删除；如需停止使用请下架");
        }
        List<Long> courseIds = eduResourceMapper.selectCourseIdsByResource(resourceId);
        if (StringUtils.isNotEmpty(courseIds))
        {
            throw new ServiceException("该资源已关联 " + courseIds.size() + " 门课程，请先解除课程关联再删除");
        }
        return eduResourceMapper.deleteEduResourceById(resourceId);
    }

    @Override
    public int changeStatus(Long resourceId, Integer status)
    {
        if (status == null || (status != STATUS_ON && status != STATUS_OFF))
        {
            throw new ServiceException("状态值只能是 0（上架）或 1（下架）");
        }
        EduResource exist = mustExist(resourceId);
        checkOwner(exist);
        EduResource update = new EduResource();
        update.setResourceId(resourceId);
        update.setStatus(status);
        return eduResourceMapper.updateEduResource(update);
    }

    @Override
    public Map<String, Object> authorizeDownload(Long resourceId)
    {
        EduResource res = mustExist(resourceId);
        if (res.getStatus() != null && res.getStatus() == STATUS_OFF)
        {
            throw new ServiceException("资源已下架，暂不可访问");
        }
        boolean allowed = "1".equals(res.getAllowDownload());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resourceId);
        result.put("title", res.getTitle());
        result.put("resType", res.getResType());
        result.put("allowDownload", res.getAllowDownload());
        result.put("downloadable", allowed);
        result.put("visibleScope", res.getVisibleScope());
        if (!allowed)
        {
            // 仅返回在线预览入口，不返回原始文件标识，避免绕过下载限制
            result.put("previewUrl", "/edu/resource/" + resourceId + "/preview");
            result.put("message", "该资源仅允许在线预览，未开放下载");
            return result;
        }
        result.put("fileId", res.getFileId());
        result.put("fileName", res.getFileName());
        result.put("externalUrl", res.getExternalUrl());
        result.put("message", "可下载");
        return result;
    }

    @Override
    public int linkCourse(Long courseId, Long resourceId)
    {
        if (courseId == null || resourceId == null)
        {
            throw new ServiceException("课程与资源不能为空");
        }
        mustExist(resourceId);
        List<Long> exists = eduResourceMapper.selectCourseIdsByResource(resourceId);
        if (exists != null && exists.contains(courseId))
        {
            throw new ServiceException("该资源已关联此课程");
        }
        return eduResourceMapper.insertCourseResource(courseId, resourceId, 0);
    }

    @Override
    public int unlinkCourse(Long courseId, Long resourceId)
    {
        return eduResourceMapper.deleteCourseResource(courseId, resourceId);
    }

    @Override
    public List<EduResource> selectResourcesByCourse(Long courseId)
    {
        return eduResourceMapper.selectResourcesByCourse(courseId);
    }

    @Override
    public int recountRef(Long resourceId)
    {
        mustExist(resourceId);
        return eduResourceMapper.recountRef(resourceId);
    }

    // ---------- 分类 ----------

    @Override
    public List<EduResourceCategory> selectCategoryTree(EduResourceCategory query)
    {
        List<EduResourceCategory> all = eduResourceMapper.selectCategoryList(query);
        return buildTree(all);
    }

    @Override
    public EduResourceCategory selectCategoryById(Long categoryId)
    {
        return eduResourceMapper.selectCategoryById(categoryId);
    }

    @Override
    public int insertCategory(EduResourceCategory category)
    {
        if (category.getParentId() == null)
        {
            category.setParentId(0L);
        }
        if (category.getParentId() != 0L && eduResourceMapper.selectCategoryById(category.getParentId()) == null)
        {
            throw new ServiceException("上级分类不存在");
        }
        if (StringUtils.isEmpty(category.getStatus()))
        {
            category.setStatus("0");
        }
        if (category.getOrderNum() == null)
        {
            category.setOrderNum(0);
        }
        // owner_id 为空的分类为全校公共分类，只有管理员能建
        if (category.getOwnerId() == null && !isAdmin())
        {
            category.setOwnerId(currentUserId());
        }
        if (eduResourceMapper.countCategoryByName(category.getParentId(), category.getCategoryName(),
                category.getOwnerId(), null) > 0)
        {
            throw new ServiceException("同级下已存在分类「" + category.getCategoryName() + "」");
        }
        return eduResourceMapper.insertCategory(category);
    }

    @Override
    public int updateCategory(EduResourceCategory category)
    {
        EduResourceCategory exist = eduResourceMapper.selectCategoryById(category.getCategoryId());
        if (exist == null)
        {
            throw new ServiceException("分类不存在");
        }
        if (category.getParentId() != null && category.getParentId().equals(category.getCategoryId()))
        {
            throw new ServiceException("上级分类不能选择自己");
        }
        if (StringUtils.isNotEmpty(category.getCategoryName())
                && eduResourceMapper.countCategoryByName(
                        category.getParentId() == null ? exist.getParentId() : category.getParentId(),
                        category.getCategoryName(),
                        exist.getOwnerId(), category.getCategoryId()) > 0)
        {
            throw new ServiceException("同级下已存在分类「" + category.getCategoryName() + "」");
        }
        return eduResourceMapper.updateCategory(category);
    }

    @Override
    public int deleteCategoryById(Long categoryId)
    {
        EduResourceCategory exist = eduResourceMapper.selectCategoryById(categoryId);
        if (exist == null)
        {
            throw new ServiceException("分类不存在");
        }
        if (eduResourceMapper.countChildCategory(categoryId) > 0)
        {
            throw new ServiceException("该分类下还有子分类，请先删除子分类");
        }
        int resourceCount = eduResourceMapper.countResourceByCategory(categoryId);
        if (resourceCount > 0)
        {
            throw new ServiceException("该分类下还有 " + resourceCount + " 个资源，请先移出资源");
        }
        return eduResourceMapper.deleteCategoryById(categoryId);
    }

    // ---------- 内部 ----------

    private List<EduResourceCategory> buildTree(List<EduResourceCategory> all)
    {
        List<EduResourceCategory> roots = new ArrayList<>();
        Map<Long, EduResourceCategory> index = new LinkedHashMap<>();
        for (EduResourceCategory c : all)
        {
            index.put(c.getCategoryId(), c);
        }
        for (EduResourceCategory c : all)
        {
            Long pid = c.getParentId();
            EduResourceCategory parent = pid == null ? null : index.get(pid);
            if (parent != null)
            {
                parent.getChildren().add(c);
            }
            else
            {
                roots.add(c);
            }
        }
        return roots;
    }

    private void checkTypeFields(EduResource res)
    {
        String type = res.getResType();
        if ("link".equals(type))
        {
            if (StringUtils.isEmpty(res.getExternalUrl()))
            {
                throw new ServiceException("外链资源必须填写访问地址");
            }
            return;
        }
        if (StringUtils.isEmpty(res.getFileId()))
        {
            throw new ServiceException("请先上传文件，再登记为 " + type + " 类型资源");
        }
    }

    private EduResource mustExist(Long resourceId)
    {
        EduResource res = eduResourceMapper.selectEduResourceById(resourceId);
        if (res == null)
        {
            throw new ServiceException("资源不存在或已删除");
        }
        return res;
    }

    /** 教师只能操作自己上传的资源 */
    private void checkOwner(EduResource res)
    {
        if (isAdmin())
        {
            return;
        }
        Long uid = currentUserId();
        if (res.getOwnerId() != null && !res.getOwnerId().equals(uid))
        {
            throw new ServiceException("只能维护自己上传的资源");
        }
    }

    private boolean isAdmin()
    {
        try
        {
            return SecurityUtils.isAdmin(SecurityUtils.getUserId());
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private Long currentUserId()
    {
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception e)
        {
            return 0L;
        }
    }
}
