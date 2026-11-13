package com.legaltraining.business.edu.domain;

import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 学生账号查询视图（跨 sys_user / edu_user_profile / edu_class_student / edu_class）
 *
 * 对应需求 MG-01-05/06：账号启停、单条新增、按班级查看
 * 只用于查询与导出，新增走 SysUser + EduUserProfile 两表写入。
 */
public class EduStudent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long userId;

    /** 登录名（学生即学号） */
    @Excel(name = "登录名")
    private String userName;

    /** 姓名 */
    @Excel(name = "姓名")
    private String nickName;

    /** 学号 */
    @Excel(name = "学号")
    private String studentNo;

    /** 用户类别 student/teacher/editor/auditor/admin */
    private String userCategory;

    private Long termId;

    private Long classId;

    @Excel(name = "班级")
    private String className;

    private Long majorId;

    @Excel(name = "专业")
    private String majorName;

    @Excel(name = "年级")
    private Integer gradeYear;

    @Excel(name = "手机号")
    private String phonenumber;

    /** 账号状态 0正常 1停用 */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 来源 manual/import/unified */
    @Excel(name = "来源")
    private String source;

    /** 是否当前归属（查询条件：0 发生时归属 1 当前归属，默认 1） */
    private String currentOnly;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getStudentNo()
    {
        return studentNo;
    }

    public void setStudentNo(String studentNo)
    {
        this.studentNo = studentNo;
    }

    public String getUserCategory()
    {
        return userCategory;
    }

    public void setUserCategory(String userCategory)
    {
        this.userCategory = userCategory;
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

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public Long getMajorId()
    {
        return majorId;
    }

    public void setMajorId(Long majorId)
    {
        this.majorId = majorId;
    }

    public String getMajorName()
    {
        return majorName;
    }

    public void setMajorName(String majorName)
    {
        this.majorName = majorName;
    }

    public Integer getGradeYear()
    {
        return gradeYear;
    }

    public void setGradeYear(Integer gradeYear)
    {
        this.gradeYear = gradeYear;
    }

    public String getPhonenumber()
    {
        return phonenumber;
    }

    public void setPhonenumber(String phonenumber)
    {
        this.phonenumber = phonenumber;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getCurrentOnly()
    {
        return currentOnly;
    }

    public void setCurrentOnly(String currentOnly)
    {
        this.currentOnly = currentOnly;
    }
}
