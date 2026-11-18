package com.legaltraining.web.controller.common;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.legaltraining.common.config.LegalTrainingConfig;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.utils.StringUtils;
import com.legaltraining.common.utils.file.FileUploadUtils;
import com.legaltraining.common.utils.file.FileUtils;
import com.legaltraining.framework.config.ServerConfig;

/**
 * 通用请求处理
 * 
 */
@RestController
@RequestMapping("/common")
public class CommonController
{
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private com.legaltraining.web.controller.ops.MediaRecoveryJournal mediaJournal;

    private static final String FILE_DELIMITER = ",";

    /**
     * 通用下载请求
     * 
     * @param fileName 文件名称
     * @param delete 是否删除
     */
    @GetMapping("/download")
    public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request)
    {
        try
        {
            if (!FileUtils.checkAllowDownload(fileName))
            {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String filePath = LegalTrainingConfig.getDownloadPath() + fileName;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
            if (Boolean.TRUE.equals(delete))
            {
                mediaJournal.deleteProfileFile(java.nio.file.Path.of(filePath));
            }
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用上传请求（单个）
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception
    {
        try
        {
            // 上传文件路径
            String filePath = LegalTrainingConfig.getUploadPath();
            // 上传并返回新文件名称
            String fileName = FileUploadUtils.upload(filePath, file);
            mediaJournal.captureUpload(fileName);
            String url = serverConfig.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通用上传请求（多个）
     */
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files) throws Exception
    {
        try
        {
            // 上传文件路径
            String filePath = LegalTrainingConfig.getUploadPath();
            List<String> urls = new ArrayList<String>();
            List<String> fileNames = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files)
            {
                // 上传并返回新文件名称
                String fileName = FileUploadUtils.upload(filePath, file);
                mediaJournal.captureUpload(fileName);
                String url = serverConfig.getUrl() + fileName;
                urls.add(url);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMITER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMITER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMITER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMITER));
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 受控教学文件上传（F01）
     *
     * 教学资源、学生提交附件、任务模板等受控文件必须走此端点，落到 upload/secure 子目录，
     * 返回的 fileName 形如 /profile/upload/secure/YYYY/MM/DD/xxx。该前缀被 Spring Security
     * 静态拒绝，只能经带业务鉴权的接口（serveFile）读取，避免知道路径即可绕过鉴权直取。
     */
    @PostMapping("/upload/secure")
    public AjaxResult uploadSecureFile(MultipartFile file) throws Exception
    {
        try
        {
            String fileName = FileUploadUtils.upload(LegalTrainingConfig.getSecureUploadPath(), file);
            mediaJournal.captureUpload(fileName);
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", serverConfig.getUrl() + fileName);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 受控教学文件批量上传（F01），语义与单文件上传一致。
     */
    @PostMapping("/uploads/secure")
    public AjaxResult uploadSecureFiles(List<MultipartFile> files) throws Exception
    {
        try
        {
            List<String> urls = new ArrayList<String>();
            List<String> fileNames = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files)
            {
                String fileName = FileUploadUtils.upload(LegalTrainingConfig.getSecureUploadPath(), file);
                mediaJournal.captureUpload(fileName);
                urls.add(serverConfig.getUrl() + fileName);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMITER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMITER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMITER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMITER));
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/download/resource")
    public void resourceDownload(String resource, HttpServletRequest request, HttpServletResponse response)
            throws Exception
    {
        try
        {
            if (!FileUtils.checkAllowDownload(resource))
            {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            // F01：受控文件禁止经通用下载接口直读，必须走带业务鉴权的接口
            String relative = FileUtils.stripPrefix(resource);
            String normalized = relative == null ? "" : relative.replace('\\', '/');
            if ("/upload/secure/".equals(normalized) || normalized.startsWith("/upload/secure/"))
            {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "受控文件不允许通过通用下载接口访问");
                return;
            }
            // 本地资源路径
            String localPath = LegalTrainingConfig.getProfile();
            // 数据库资源地址
            String downloadPath = localPath + relative;
            // 下载名称
            String downloadName = StringUtils.substringAfterLast(downloadPath, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }
}
