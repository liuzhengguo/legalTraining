package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import com.legaltraining.business.edu.domain.EduResource;
import com.legaltraining.business.edu.domain.EduResourceCategory;

/**
 * 教学资源 服务层（TC-02-01~04）
 */
public interface IEduResourceService
{
    EduResource selectEduResourceById(Long resourceId);

    List<EduResource> selectEduResourceList(EduResource eduResource);

    /** 登记资源（TC-02-01）：文件本体由文件服务托管，这里只落元数据 */
    int insertEduResource(EduResource eduResource);

    int updateEduResource(EduResource eduResource);

    /** 删除：ref_count>0 或已关联课程时禁止，避免任务/展项引用断链（TC-02-03） */
    int deleteEduResourceById(Long resourceId);

    /** 上架/下架：status 0 上架 1 下架 */
    int changeStatus(Long resourceId, Integer status);

    /**
     * 下载授权（TC-02-04）
     * allow_download='0' 时拒绝并给出在线预览地址；返回 fileId / fileName / url
     */
    Map<String, Object> authorizeDownload(Long resourceId);

    /** 课程关联资源 */
    int linkCourse(Long courseId, Long resourceId);

    int unlinkCourse(Long courseId, Long resourceId);

    List<EduResource> selectResourcesByCourse(Long courseId);

    /** 重算引用计数（任务调整关联后调用） */
    int recountRef(Long resourceId);

    // ---------- 分类（TC-02-02） ----------

    /** 分类树 */
    List<EduResourceCategory> selectCategoryTree(EduResourceCategory query);

    EduResourceCategory selectCategoryById(Long categoryId);

    int insertCategory(EduResourceCategory category);

    int updateCategory(EduResourceCategory category);

    /** 删除分类：有子分类或分类下有资源时禁止 */
    int deleteCategoryById(Long categoryId);
}
