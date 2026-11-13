package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduResource;
import com.legaltraining.business.edu.domain.EduResourceCategory;

/**
 * 教学资源 数据层（TC-02-01~04）
 */
public interface EduResourceMapper
{
    EduResource selectEduResourceById(Long resourceId);

    List<EduResource> selectEduResourceList(EduResource eduResource);

    int insertEduResource(EduResource eduResource);

    int updateEduResource(EduResource eduResource);

    int deleteEduResourceById(Long resourceId);

    /** 引用计数 +1 / -1（delta 为 1 或 -1） */
    int changeRefCount(@Param("resourceId") Long resourceId, @Param("delta") int delta);

    /** 引用计数重算（以任务/展项实际关联为准） */
    int recountRef(@Param("resourceId") Long resourceId);

    /** 课程-资源关联 */
    List<Long> selectCourseIdsByResource(Long resourceId);

    int deleteCourseResourceByResource(Long resourceId);

    int insertCourseResource(@Param("courseId") Long courseId, @Param("resourceId") Long resourceId,
            @Param("sortNo") Integer sortNo);

    int deleteCourseResource(@Param("courseId") Long courseId, @Param("resourceId") Long resourceId);

    /** 课程资源清单 */
    List<EduResource> selectResourcesByCourse(@Param("courseId") Long courseId);

    // ---------- 分类 ----------

    List<EduResourceCategory> selectCategoryList(EduResourceCategory query);

    EduResourceCategory selectCategoryById(Long categoryId);

    int insertCategory(EduResourceCategory category);

    int updateCategory(EduResourceCategory category);

    int deleteCategoryById(Long categoryId);

    /** 子分类数量（删除前校验） */
    int countChildCategory(Long categoryId);

    /** 分类下资源数量（删除前校验） */
    int countResourceByCategory(Long categoryId);

    /** 同名分类校验（同父级、同归属人） */
    int countCategoryByName(@Param("parentId") Long parentId, @Param("categoryName") String categoryName,
            @Param("ownerId") Long ownerId, @Param("excludeId") Long excludeId);
}
