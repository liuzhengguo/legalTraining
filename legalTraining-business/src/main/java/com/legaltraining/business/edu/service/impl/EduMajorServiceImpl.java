package com.legaltraining.business.edu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.legaltraining.business.edu.domain.EduMajor;
import com.legaltraining.business.edu.mapper.EduMajorMapper;
import com.legaltraining.business.edu.service.IEduMajorService;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;

/**
 * 专业 服务实现
 */
@Service
public class EduMajorServiceImpl implements IEduMajorService
{
    @Autowired
    private EduMajorMapper eduMajorMapper;

    @Override
    public EduMajor selectEduMajorById(Long majorId)
    {
        return eduMajorMapper.selectEduMajorById(majorId);
    }

    @Override
    public List<EduMajor> selectEduMajorList(EduMajor eduMajor)
    {
        return eduMajorMapper.selectEduMajorList(eduMajor);
    }

    @Override
    public int insertEduMajor(EduMajor eduMajor)
    {
        if (eduMajorMapper.countByCode(eduMajor.getMajorCode(), null) > 0)
        {
            throw new ServiceException("专业编码「" + eduMajor.getMajorCode() + "」已存在");
        }
        if (StringUtils.isEmpty(eduMajor.getStatus()))
        {
            eduMajor.setStatus("0");
        }
        eduMajor.setCreateBy(currentUsername());
        return eduMajorMapper.insertEduMajor(eduMajor);
    }

    @Override
    public int updateEduMajor(EduMajor eduMajor)
    {
        EduMajor exist = eduMajorMapper.selectEduMajorById(eduMajor.getMajorId());
        if (exist == null)
        {
            throw new ServiceException("专业不存在");
        }
        if (StringUtils.isNotEmpty(eduMajor.getMajorCode())
                && eduMajorMapper.countByCode(eduMajor.getMajorCode(), eduMajor.getMajorId()) > 0)
        {
            throw new ServiceException("专业编码「" + eduMajor.getMajorCode() + "」已存在");
        }
        eduMajor.setUpdateBy(currentUsername());
        return eduMajorMapper.updateEduMajor(eduMajor);
    }

    @Override
    public int deleteEduMajorByIds(Long[] majorIds)
    {
        if (majorIds == null || majorIds.length == 0)
        {
            return 0;
        }
        for (Long majorId : majorIds)
        {
            int used = eduMajorMapper.countReferencedByClass(majorId);
            if (used > 0)
            {
                EduMajor major = eduMajorMapper.selectEduMajorById(majorId);
                String name = major == null ? String.valueOf(majorId) : major.getMajorName();
                throw new ServiceException("专业「" + name + "」已被 " + used + " 个班级引用，不允许删除");
            }
        }
        return eduMajorMapper.deleteEduMajorByIds(majorIds);
    }

    private String currentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
