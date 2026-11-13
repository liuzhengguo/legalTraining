package com.legaltraining.business.edu.domain;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.legaltraining.common.annotation.Excel;
import com.legaltraining.common.core.domain.BaseEntity;

/**
 * 教学资源 edu_resource（TC-02-01~04）
 *
 * 文件本体存在 ops 文件服务，这里只登记元数据（file_id 为文件服务标识）。
 * ref_count > 0 表示已被任务或展项引用，禁止删除，只能下架。
 * allow_download='0' 时学生端只能在线预览，不返回可下载地址（TC-02-04）。
 */
public class EduResource extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Excel(name = "资源ID", cellType = Excel.ColumnType.NUMERIC)
    private Long resourceId;

    @Excel(name = "资源标题")
    private String title;

    private Long categoryId;

    /** 分类名称（关联查询） */
    @Excel(name = "分类")
    private String categoryName;

    /** doc/video/audio/image/link */
    @Pattern(regexp = "^(doc|video|audio|image|link)$", message = "资源类型只能是 doc/video/audio/image/link")
    private String resType;

    /** 文件服务标识 */
    private String fileId;

    @Excel(name = "文件名")
    private String fileName;

    private Long fileSize;

    private String fileExt;

    /** 外链地址（resType=link 时必填） */
    private String externalUrl;

    /** 音视频时长（秒） */
    private Integer duration;

    private String cover;

    /** 可见范围 0仅本人 1本课程 2本班 3全校 */
    @Excel(name = "可见范围", readConverterExp = "0=仅本人,1=本课程,2=本班,3=全校")
    private Integer visibleScope;

    /** 是否允许下载（TC-02-04） */
    @Excel(name = "允许下载", readConverterExp = "1=允许,0=禁止")
    private String allowDownload;

    /** 被引用次数，>0 不可删除 */
    private Integer refCount;

    /** 0已上架 1已下架 */
    @Excel(name = "状态", readConverterExp = "0=已上架,1=已下架")
    private Integer status;

    /** 上传教师 */
    private Long ownerId;

    /** 上传教师姓名（关联查询） */
    @Excel(name = "上传教师")
    private String ownerName;

    /** 提交时一并建立课程关联 */
    private Long courseId;

    /** 课程ID列表（查询：按课程筛选） */
    private List<Long> courseIds;

    public Long getResourceId()
    {
        return resourceId;
    }

    public void setResourceId(Long resourceId)
    {
        this.resourceId = resourceId;
    }

    @NotBlank(message = "资源标题不能为空")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    @NotBlank(message = "资源类型不能为空")
    public String getResType()
    {
        return resType;
    }

    public void setResType(String resType)
    {
        this.resType = resType;
    }

    public String getFileId()
    {
        return fileId;
    }

    public void setFileId(String fileId)
    {
        this.fileId = fileId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getFileExt()
    {
        return fileExt;
    }

    public void setFileExt(String fileExt)
    {
        this.fileExt = fileExt;
    }

    public String getExternalUrl()
    {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl)
    {
        this.externalUrl = externalUrl;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public Integer getVisibleScope()
    {
        return visibleScope;
    }

    public void setVisibleScope(Integer visibleScope)
    {
        this.visibleScope = visibleScope;
    }

    public String getAllowDownload()
    {
        return allowDownload;
    }

    public void setAllowDownload(String allowDownload)
    {
        this.allowDownload = allowDownload;
    }

    public Integer getRefCount()
    {
        return refCount;
    }

    public void setRefCount(Integer refCount)
    {
        this.refCount = refCount;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public Long getOwnerId()
    {
        return ownerId;
    }

    public void setOwnerId(Long ownerId)
    {
        this.ownerId = ownerId;
    }

    public String getOwnerName()
    {
        return ownerName;
    }

    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public List<Long> getCourseIds()
    {
        return courseIds;
    }

    public void setCourseIds(List<Long> courseIds)
    {
        this.courseIds = courseIds;
    }
}
