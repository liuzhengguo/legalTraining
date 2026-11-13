package com.legaltraining.business.edu.domain;

import java.util.Date;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 导入批次 edu_import_batch（MG-01-04 预检 → 确认导入）
 *
 * 状态：0 预检中 / 1 预检完成 / 2 已确认导入 / 3 已回滚
 * 预检只写批次与逐行结果，不动 sys_user；确认导入才落账号。
 */
public class EduImportBatch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long batchId;

    /** student / teacher / score */
    private String bizType;

    private Long termId;

    private Long classId;

    private String fileName;

    private String filePath;

    private Integer totalRows;

    private Integer successRows;

    /** 已存在账号按"更新班级"处理的行数 */
    private Integer updateRows;

    private Integer failRows;

    /** 0预检中 1预检完成 2已确认导入 3已回滚 */
    private Integer status;

    /** 预检报告 JSON */
    private String previewJson;

    private Date finishTime;

    public Long getBatchId()
    {
        return batchId;
    }

    public void setBatchId(Long batchId)
    {
        this.batchId = batchId;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public Long getTermId()
    {
        return termId;
    }

    public void setTermId(Long termId)
    {
        this.termId = termId;
    }

    public Long getClassId()
    {
        return classId;
    }

    public void setClassId(Long classId)
    {
        this.classId = classId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public Integer getTotalRows()
    {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows)
    {
        this.totalRows = totalRows;
    }

    public Integer getSuccessRows()
    {
        return successRows;
    }

    public void setSuccessRows(Integer successRows)
    {
        this.successRows = successRows;
    }

    public Integer getUpdateRows()
    {
        return updateRows;
    }

    public void setUpdateRows(Integer updateRows)
    {
        this.updateRows = updateRows;
    }

    public Integer getFailRows()
    {
        return failRows;
    }

    public void setFailRows(Integer failRows)
    {
        this.failRows = failRows;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public String getPreviewJson()
    {
        return previewJson;
    }

    public void setPreviewJson(String previewJson)
    {
        this.previewJson = previewJson;
    }

    public Date getFinishTime()
    {
        return finishTime;
    }

    public void setFinishTime(Date finishTime)
    {
        this.finishTime = finishTime;
    }
}
