package com.legaltraining.business.edu.domain;

import com.legaltraining.common.annotation.Excel;

/**
 * 导入逐行结果 edu_import_row
 *
 * result：0 成功 / 1 失败 / 2 更新
 * fail_reason 直接面向导入人，写"第 3 列班级编码不存在"这种可自助修正的话。
 */
public class EduImportRow
{
    private Long id;

    private Long batchId;

    /** Excel 行号（含表头，从 2 起） */
    @Excel(name = "Excel行号")
    private Integer rowNo;

    /** 原始行数据 JSON */
    @Excel(name = "原始数据", width = 60)
    private String rawData;

    private Integer result;

    @Excel(name = "失败原因", width = 40)
    private String failReason;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getBatchId()
    {
        return batchId;
    }

    public void setBatchId(Long batchId)
    {
        this.batchId = batchId;
    }

    public Integer getRowNo()
    {
        return rowNo;
    }

    public void setRowNo(Integer rowNo)
    {
        this.rowNo = rowNo;
    }

    public String getRawData()
    {
        return rawData;
    }

    public void setRawData(String rawData)
    {
        this.rawData = rawData;
    }

    public Integer getResult()
    {
        return result;
    }

    public void setResult(Integer result)
    {
        this.result = result;
    }

    public String getFailReason()
    {
        return failReason;
    }

    public void setFailReason(String failReason)
    {
        this.failReason = failReason;
    }
}
