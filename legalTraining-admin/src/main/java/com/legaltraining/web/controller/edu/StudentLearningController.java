package com.legaltraining.web.controller.edu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.legaltraining.business.edu.service.IEduSubmissionService;
import com.legaltraining.business.edu.service.IEduTaskService;
import com.legaltraining.business.edu.domain.EduSubmission;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.config.LegalTrainingConfig;
import com.legaltraining.common.exception.ServiceException;

@RestController
@RequestMapping("/edu/learning")
@PreAuthorize("@ss.hasPermi('edu:submission:my')")
public class StudentLearningController extends BaseController
{
    private static final String COURSE_ACCESS = "c.del_flag = '0' and c.status in (1, 2) and ("
            + "exists (select 1 from edu_course_class cc join edu_class_student cs "
            + "on cs.class_id = cc.class_id and cs.term_id = c.term_id "
            + "where cc.course_id = c.course_id and cs.user_id = ? "
            + "and (cs.is_current = '1' or c.status = 2)))";

    private static final String COURSES = "select c.course_id as courseId, c.course_name as courseName, "
            + "c.intro, c.status from edu_course c where (" + COURSE_ACCESS
            + "or exists (select 1 from edu_task t join edu_task_participant p on p.task_id = t.task_id "
            + "where t.course_id = c.course_id and t.del_flag = '0' and t.publish_time is not null and p.user_id = ? "
            + "and c.del_flag = '0' and c.status in (1, 2)))";

    private static final String TASKS = "select t.task_id as taskId, t.title, t.deadline, t.status, "
            + "p.submit_status as submitStatus from edu_task t join edu_task_participant p on p.task_id = t.task_id "
            + "where p.user_id = ? and t.del_flag = '0' and t.status in (1, 2, 3, 4) and t.publish_time is not null";

    private static final Map<String, String> PREVIEW_TYPES = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "webp", "image/webp", "mp4", "video/mp4", "mp3", "audio/mpeg", "txt", "text/plain");

    private final JdbcTemplate jdbc;
    private final IEduTaskService tasks;
    private final IEduSubmissionService submissions;

    public StudentLearningController(JdbcTemplate jdbc, IEduTaskService tasks, IEduSubmissionService submissions)
    {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.submissions = submissions;
    }

    @GetMapping("/courses")
    public AjaxResult courses()
    {
        Long userId = getUserId();
        return success(jdbc.queryForList(COURSES + " order by c.term_id desc, c.course_id desc", userId, userId));
    }

    @GetMapping("/notifications")
    public AjaxResult notifications()
    {
        return success(jdbc.queryForList("select id,event_code as eventCode,title,content,biz_type as bizType,"
                + "biz_id as bizId,is_read as isRead,send_time as sendTime from ops_notification "
                + "where receiver_id=? order by send_time desc,id desc limit 100", getUserId()));
    }

    @PutMapping("/notifications/{id}/read")
    public AjaxResult markRead(@PathVariable long id)
    {
        jdbc.update("update ops_notification set is_read='1',read_time=now() "
                + "where id=? and receiver_id=? and is_read='0'", id, getUserId());
        return success();
    }

    @GetMapping("/courses/{courseId}")
    public AjaxResult course(@PathVariable Long courseId)
    {
        Long userId = getUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(COURSES + " and c.course_id = ?", userId, userId, courseId);
        if (rows.isEmpty())
        {
            throw new ServiceException("课程不存在或当前账号无权访问");
        }
        Map<String, Object> data = new LinkedHashMap<>(rows.get(0));
        data.put("tasks", jdbc.queryForList(TASKS + " and t.course_id = ? order by t.deadline, t.task_id", userId, courseId));
        List<Map<String, Object>> resources = jdbc.queryForList(
                "select r.resource_id as resourceId, r.title, r.res_type as resType, "
                + "r.file_name as fileName, r.allow_download as allowDownload, r.file_ext as fileExt, "
                + "case when r.res_type = 'link' then r.external_url else null end as externalUrl "
                + "from edu_course_resource cr join edu_resource r on r.resource_id = cr.resource_id "
                + "join edu_course c on c.course_id = cr.course_id "
                + "where " + COURSE_ACCESS + " and cr.course_id = ? and r.status = 0 and r.del_flag = '0' "
                + "and r.visible_scope in (1, 2, 3) order by r.resource_id",
                userId, courseId);
        for (Map<String, Object> resource : resources)
        {
            resource.put("previewable", previewType((String) resource.remove("fileExt")) != null);
        }
        data.put("resources", resources);
        data.put("exhibits", jdbc.queryForList(
                "select ce.target_type as targetType,ce.target_id as targetId,"
                + "case when ce.target_type='zone' then z.zone_name else i.title end as title "
                + "from edu_course_exhibit ce left join exh_item i on ce.target_type='item' "
                + "and i.item_id=ce.target_id and i.status=2 and i.view_level=2 and i.del_flag='0' "
                + "left join exh_item_version v on v.item_id=i.item_id and v.version=(select max(p.version) "
                + "from exh_item_version p where p.item_id=i.item_id and p.edit_type in (1,2)) "
                + "join exh_zone z on z.zone_id=case when ce.target_type='zone' then ce.target_id "
                + "else cast(json_unquote(json_extract(v.snapshot,'$.zoneId')) as unsigned) end "
                + "where ce.course_id=? and z.status=1 and z.open_level=2 and z.del_flag='0' "
                + "order by ce.sort_no,ce.id", courseId));
        return success(data);
    }

    @GetMapping("/tasks/{taskId}")
    public AjaxResult task(@PathVariable Long taskId)
    {
        if (jdbc.queryForList(TASKS + " and t.task_id = ?", getUserId(), taskId).isEmpty())
        {
            throw new ServiceException("任务不存在或当前账号无权访问");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", tasks.selectEduTaskById(taskId));
        EduSubmission submission = submissions.my(taskId);
        data.put("submission", submission);
        if (submission.getSubmissionId() != null)
        {
            List<Map<String, Object>> feedback = jdbc.queryForList(
                    "select r.review_comment as reviewComment, r.publish_time as publishTime "
                    + "from edu_review r where r.submission_id = ? and r.submission_version = ? "
                    + "and r.status = 1 and r.is_return = '0' order by r.review_id desc limit 1",
                    submission.getSubmissionId(), submission.getVersion());
            if (!feedback.isEmpty())
            {
                data.put("feedback", feedback.get(0));
            }
        }
        return success(data);
    }

    @GetMapping("/courses/{courseId}/resources/{resourceId}")
    public ResponseEntity<FileSystemResource> resource(@PathVariable Long courseId, @PathVariable Long resourceId,
            @RequestParam(defaultValue = "false") boolean download) throws IOException
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select r.file_id, r.file_name, r.file_ext, r.allow_download "
                + "from edu_course_resource cr join edu_resource r on r.resource_id = cr.resource_id "
                + "join edu_course c on c.course_id = cr.course_id "
                + "where " + COURSE_ACCESS + " and cr.course_id = ? and r.resource_id = ? "
                + "and r.status = 0 and r.del_flag = '0' and r.visible_scope in (1, 2, 3)",
                getUserId(), courseId, resourceId);
        if (rows.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> resource = rows.get(0);
        String fileId = (String) resource.get("file_id");
        if (download && !"1".equals(resource.get("allow_download")))
        {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (fileId == null || !fileId.matches("/profile/upload/[A-Za-z0-9_./-]+"))
        {
            return ResponseEntity.notFound().build();
        }
        String mediaType = previewType((String) resource.get("file_ext"));
        if (!download && mediaType == null)
        {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }
        Path root = Path.of(LegalTrainingConfig.getUploadPath()).toAbsolutePath().normalize();
        Path file = root.resolve(fileId.substring("/profile/upload/".length())).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || !file.toRealPath().startsWith(root.toRealPath()))
        {
            return ResponseEntity.notFound().build();
        }
        String name = (String) resource.get("file_name");
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(name == null ? file.getFileName().toString() : name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(download ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mediaType))
                .body(new FileSystemResource(file));
    }

    private String previewType(String ext)
    {
        return ext == null ? null : PREVIEW_TYPES.get(ext.toLowerCase(java.util.Locale.ROOT));
    }
}
