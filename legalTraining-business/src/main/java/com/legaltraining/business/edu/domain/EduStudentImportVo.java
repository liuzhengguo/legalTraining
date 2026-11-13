package com.legaltraining.business.edu.domain;

import com.legaltraining.common.annotation.Excel;

/**
 * 学生导入模板行（MG-01-04）
 *
 * 模板只有四列：学号、姓名、班级编码、手机号。
 * 专业、年级从班级反填，不从 Excel 读，避免同一学生在两个来源里被写成不同专业。
 */
public class EduStudentImportVo
{
    @Excel(name = "学号", width = 20)
    private String studentNo;

    @Excel(name = "姓名", width = 20)
    private String realName;

    @Excel(name = "班级编码", width = 20)
    private String classCode;

    @Excel(name = "手机号", width = 20)
    private String phone;

    public String getStudentNo()
    {
        return studentNo;
    }

    public void setStudentNo(String studentNo)
    {
        this.studentNo = studentNo;
    }

    public String getRealName()
    {
        return realName;
    }

    public void setRealName(String realName)
    {
        this.realName = realName;
    }

    public String getClassCode()
    {
        return classCode;
    }

    public void setClassCode(String classCode)
    {
        this.classCode = classCode;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }
}
