package com.legaltraining.business.edu.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 班级 edu_class
 *
 * 对应需求 MG-01-02（院系、专业、年级、学期、任课教师）
 * 班级编码在"同一学期"内唯一（uk_class_term_code），跨学期可复用。
 * student_count 为冗余字段，由成员表增删后统一重算，不做逐次 +1/-1。
 */
public class EduClass extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 班级ID */
    @Excel(name = "班级ID", cellType = Excel.ColumnType.NUMERIC)
    private Long classId;

    /** 所属学期 */
    @NotNull(message = "所属学期不能为空")
    private Long termId;

    /** 学期名称（关联查询） */
    @Excel(name = "所属学期")
    private String termName;

    /** 班级编码 */
    @Excel(name = "班级编码")
    private String classCode;

    /** 班级名称 */
    @Excel(name = "班级名称")
    private String className;

    /** 院系ID */
    @NotNull(message = "所属院系不能为空")
    private Long deptId;

    /** 院系名称（关联查询） */
    @Excel(name = "所属院系")
    private String deptName;

    /** 专业ID */
    private Long majorId;

    /** 专业名称（关联查询） */
    @Excel(name = "专业")
    private String majorName;

    /** 年级（入学年） */
    @Excel(name = "年级")
    private Integer gradeYear;

    /** 班主任/任课教师 user_id */
    private Long headTeacher;

    /** 班主任姓名（关联查询） */
    @Excel(name = "班主任")
    private String headTeacherName;

    /** 在册人数（冗余，成员变更后重算） */
    @Excel(name = "在册人数")
    private Integer studentCount;

    /** 状态 0启用 1归档 */
    @Excel(name = "状态", readConverterExp = "0=启用,1=归档")
    private Integer status;

    /** 删除标志 */
    private String delFlag;

    public Long getClassId()
    {
        return classId;
    }

    public void setClassId(Long classId)
    {
        this.classId = classId;
    }

    public Long getTermId()
    {
        return termId;
    }

    public void setTermId(Long termId)
    {
        this.termId = termId;
    }

    public String getTermName()
    {
        return termName;
    }

    public void setTermName(String termName)
    {
        this.termName = termName;
    }

    @NotBlank(message = "班级编码不能为空")
    @Size(min = 0, max = 32, message = "班级编码长度不能超过32个字符")
    public String getClassCode()
    {
        return classCode;
    }

    public void setClassCode(String classCode)
    {
        this.classCode = classCode;
    }

    @NotBlank(message = "班级名称不能为空")
    @Size(min = 0, max = 64, message = "班级名称长度不能超过64个字符")
    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
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

    public Long getHeadTeacher()
    {
        return headTeacher;
    }

    public void setHeadTeacher(Long headTeacher)
    {
        this.headTeacher = headTeacher;
    }

    public String getHeadTeacherName()
    {
        return headTeacherName;
    }

    public void setHeadTeacherName(String headTeacherName)
    {
        this.headTeacherName = headTeacherName;
    }

    public Integer getStudentCount()
    {
        return studentCount;
    }

    public void setStudentCount(Integer studentCount)
    {
        this.studentCount = studentCount;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
