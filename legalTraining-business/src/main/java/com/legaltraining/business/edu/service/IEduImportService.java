package com.legaltraining.business.edu.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import com.legaltraining.business.edu.domain.EduImportBatch;
import com.legaltraining.business.edu.domain.EduImportRow;

/**
 * 学生账号批量导入（MG-01-04）
 *
 * 两阶段：preview（只预检，不落账号）→ confirm（按预检结果入库）。
 */
public interface IEduImportService
{
    /**
     * 预检：解析 Excel、逐行校验、写批次与逐行结果，不创建账号
     */
    public Map<String, Object> preview(MultipartFile file, Long termId);

    /**
     * 确认导入：失败率超过 30% 时整批不落库
     */
    public Map<String, Object> confirm(Long batchId);

    public EduImportBatch selectBatchById(Long batchId);

    public List<EduImportBatch> selectBatchList(EduImportBatch query);

    /**
     * 失败清单（含行号与原因，可直接导出回传）
     */
    public List<EduImportRow> selectFailRows(Long batchId);
}
