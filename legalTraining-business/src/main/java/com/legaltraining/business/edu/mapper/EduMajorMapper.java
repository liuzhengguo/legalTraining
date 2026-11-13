package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduMajor;

/**
 * 专业 数据层
 */
public interface EduMajorMapper
{
    public EduMajor selectEduMajorById(Long majorId);

    public List<EduMajor> selectEduMajorList(EduMajor eduMajor);

    public int insertEduMajor(EduMajor eduMajor);

    public int updateEduMajor(EduMajor eduMajor);

    public int deleteEduMajorByIds(Long[] majorIds);

    /**
     * 编码唯一性校验
     *
     * @param excludeId 编辑时排除自身
     */
    public int countByCode(@Param("majorCode") String majorCode, @Param("excludeId") Long excludeId);

    /**
     * 被班级引用的数量（删除前置检查）
     */
    public int countReferencedByClass(Long majorId);
}
