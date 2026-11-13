package com.legaltraining.business.edu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.legaltraining.business.edu.domain.EduImportBatch;
import com.legaltraining.business.edu.domain.EduImportRow;

/**
 * 导入批次 数据层
 */
public interface EduImportMapper
{
    public int insertBatch(EduImportBatch batch);

    public int updateBatch(EduImportBatch batch);

    public EduImportBatch selectBatchById(Long batchId);

    public List<EduImportBatch> selectBatchList(EduImportBatch query);

    public int deleteRowsByBatch(Long batchId);

    public int batchInsertRows(@Param("list") List<EduImportRow> list);

    public List<EduImportRow> selectRows(@Param("batchId") Long batchId, @Param("result") Integer result);
}
