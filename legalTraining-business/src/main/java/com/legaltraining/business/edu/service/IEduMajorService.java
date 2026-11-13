package com.legaltraining.business.edu.service;

import java.util.List;
import com.legaltraining.business.edu.domain.EduMajor;

/**
 * 专业 服务层
 */
public interface IEduMajorService
{
    public EduMajor selectEduMajorById(Long majorId);

    public List<EduMajor> selectEduMajorList(EduMajor eduMajor);

    public int insertEduMajor(EduMajor eduMajor);

    public int updateEduMajor(EduMajor eduMajor);

    public int deleteEduMajorByIds(Long[] majorIds);
}
