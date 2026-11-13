package com.legaltraining.web.controller.edu;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.legaltraining.business.edu.scope.BusinessScopeChecker;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.config.LegalTrainingConfig;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.ip.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
public class AchievementController extends BaseController
{
    private static final Set<String> CHECKS = Set.of("accuracy", "compliance", "copyright", "privacy", "usability");
    private static final Set<String> SIGN_MODES = Set.of("实名", "部分匿名", "完全匿名");
    private static final Set<String> SCOPES = Set.of("仅展厅内", "含对外分享");
    private static final String LIST = "select r.rec_id as recId,r.submission_id as submissionId,r.reason,r.status,"
            + "r.sign_mode as signMode,r.scope_desc as scopeDesc,r.review_opinion as opinion,r.exhibit_id as exhibitId,"
            + "t.title,z.zone_name as zoneName,r.level_code as levelCode,i.status as exhibitStatus,"
            + "exists(select 1 from edu_authorization a where a.rec_id=r.rec_id and a.auth_type=1 and a.file_id is not null) as hasAuthFile "
            + "from edu_recommendation r join edu_submission s on s.submission_id=r.submission_id "
            + "join edu_task t on t.task_id=s.task_id left join exh_zone z on z.zone_id=r.target_zone "
            + "left join exh_item i on i.item_id=r.exhibit_id ";
    private final JdbcTemplate jdbc;
    private final BusinessScopeChecker scope;

    public AchievementController(JdbcTemplate jdbc, BusinessScopeChecker scope)
    {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    public record Recommendation(@NotNull Long submissionId, @NotNull Long zoneId,
            @NotBlank @Size(max = 1000) String reason, @NotBlank @Size(max = 16) String levelCode) {}
    public record Authorization(boolean accept, String signMode, String scope) {}
    public record Decision(boolean approve, Set<String> checks, @Size(max = 1000) String opinion) {}

    @GetMapping("/edu/rec/options")
    @PreAuthorize("@ss.hasPermi('edu:score:recommend')")
    public AjaxResult options()
    {
        return success(jdbc.queryForList("select zone_id as zoneId,zone_name as zoneName from exh_zone "
                + "where del_flag='0' order by route_no,zone_id"));
    }

    @GetMapping("/edu/rec/list")
    @PreAuthorize("@ss.hasPermi('edu:score:recommend')")
    public AjaxResult recommendations()
    {
        if (scope.hasAnyRole("registrar")) return success(jdbc.queryForList(LIST + "order by r.rec_id desc"));
        return success(jdbc.queryForList(LIST + "where r.teacher_id=? order by r.rec_id desc", getUserId()));
    }

    @PostMapping("/edu/rec")
    @PreAuthorize("@ss.hasPermi('edu:score:recommend')")
    @Transactional
    public AjaxResult recommend(@Valid @RequestBody Recommendation form)
    {
        Map<String, Object> submission = row("select s.user_id,s.status,t.course_id,t.title from edu_submission s "
                + "join edu_task t on t.task_id=s.task_id where s.submission_id=? for update", form.submissionId());
        scope.requireCourseAccess(number(submission, "course_id"));
        if (number(submission, "status") != 3) throw new ServiceException("仅可推荐已批阅作业");
        if (submission.get("user_id") == null) throw new ServiceException("该成果尚未确定授权人");
        Map<String, Object> zone = row("select zone_name from exh_zone where zone_id=? and del_flag='0'", form.zoneId());
        if (!jdbc.queryForList("select rec_id from edu_recommendation where submission_id=?", form.submissionId()).isEmpty())
        {
            throw new ServiceException("该作业已有推荐记录");
        }
        jdbc.update("insert into edu_recommendation(submission_id,teacher_id,reason,target_zone,level_code,create_time) "
                + "values(?,?,?,?,?,now())", form.submissionId(), getUserId(), form.reason(), form.zoneId(), form.levelCode());
        long id = jdbc.queryForObject("select rec_id from edu_recommendation where submission_id=?", Long.class, form.submissionId());
        jdbc.update("insert into edu_authorization(rec_id,user_id,create_time) values(?,?,now())", id, submission.get("user_id"));
        notify("NT-08", number(submission, "user_id"), id, "成果展示授权请求",
                submission.get("title") + " · " + zone.get("zone_name"));
        return success();
    }

    @GetMapping("/edu/rec/my")
    @PreAuthorize("@ss.hasPermi('edu:submission:my')")
    public AjaxResult my()
    {
        return success(jdbc.queryForList(LIST + "where s.user_id=? order by r.rec_id desc", getUserId()));
    }

    @PostMapping("/edu/rec/{id}/revoke")
    @PreAuthorize("@ss.hasPermi('edu:submission:my')")
    @Transactional
    public AjaxResult revoke(@PathVariable long id)
    {
        Map<String, Object> rec = row("select r.teacher_id,r.exhibit_id,a.auth_id from edu_recommendation r "
                + "join edu_authorization a on a.rec_id=r.rec_id where r.rec_id=? and a.user_id=? and a.status=1 for update",
                id, getUserId());
        if (rec.get("exhibit_id") != null)
        {
            jdbc.update("update exh_item set status=3,offline_time=now(),offline_reason='学生撤回展示授权' where item_id=?",
                    rec.get("exhibit_id"));
            jdbc.update("update exh_publish_job set status=3,fail_reason='学生撤回展示授权' where item_id=? and status=0",
                    rec.get("exhibit_id"));
        }
        jdbc.update("update edu_authorization set status=2 where auth_id=?", rec.get("auth_id"));
        jdbc.update("update edu_recommendation set status=4,withdraw_by=?,withdraw_time=now() where rec_id=?", getUserId(), id);
        notify("NT-10", number(rec, "teacher_id"), id, "学生已撤回成果展示授权", null);
        return success();
    }

    @PostMapping("/edu/rec/{id}/authorize")
    @PreAuthorize("@ss.hasPermi('edu:submission:my')")
    @Transactional
    public AjaxResult authorize(@PathVariable long id, @RequestBody Authorization decision, HttpServletRequest request)
    {
        Map<String, Object> rec = row("select r.status,r.teacher_id,a.auth_id from edu_recommendation r "
                + "join edu_authorization a on a.rec_id=r.rec_id where r.rec_id=? and a.user_id=? for update", id, getUserId());
        if (number(rec, "status") != 0) throw new ServiceException("当前推荐已处理");
        if (decision.accept() && (decision.signMode() == null || decision.scope() == null
                || !SIGN_MODES.contains(decision.signMode()) || !SCOPES.contains(decision.scope())))
        {
            throw new ServiceException("请选择有效的署名方式及授权范围");
        }
        jdbc.update("update edu_authorization set status=?,sign_mode=?,scope_desc=?,confirm_time=now(),ip_addr=? where auth_id=?",
                decision.accept() ? 1 : 2, decision.signMode(), decision.scope(), IpUtils.getIpAddr(request), rec.get("auth_id"));
        jdbc.update("update edu_recommendation set status=?,sign_mode=?,scope_desc=?,push_status=0,update_time=now() where rec_id=?",
                decision.accept() ? 1 : 4, decision.signMode(), decision.scope(), id);
        if (decision.accept())
        {
            notifyAuditors(id);
        }
        else
        {
            notify("NT-10", number(rec, "teacher_id"), id, "学生拒绝成果展示授权", null);
        }
        return success();
    }

    @GetMapping("/exh/audit/list")
    @PreAuthorize("@ss.hasAnyPermi('exh:audit:list,exh:item:list')")
    public AjaxResult queue()
    {
        if (!SecurityUtils.hasPermi("exh:audit:list"))
        {
            return success(jdbc.queryForList(LIST + "where r.status in (2,5) order by r.rec_id desc"));
        }
        return success(jdbc.queryForList(LIST + "where (r.status in (1,2,3,5) "
                + "or exists(select 1 from exh_review h where h.rec_id=r.rec_id)) order by r.rec_id desc"));
    }

    @GetMapping("/exh/audit/{id}")
    @PreAuthorize("@ss.hasPermi('exh:audit:list')")
    public AjaxResult auditDetail(@PathVariable long id)
    {
        Map<String, Object> detail = row("select s.content,r.status from edu_recommendation r join edu_submission s "
                + "on s.submission_id=r.submission_id where r.rec_id=? and (r.status in (1,2,3,5) "
                + "or exists(select 1 from exh_review h where h.rec_id=r.rec_id))", id);
        List<Map<String, Object>> history = jdbc.queryForList(
                "select h.review_id as reviewId,h.check_items as checks,h.result,h.opinion,"
                + "date_format(h.audit_time,'%Y-%m-%d %H:%i:%s') as auditTime,u.nick_name as auditor "
                + "from exh_review h left join sys_user u on u.user_id=h.auditor_id "
                + "where h.rec_id=? order by h.review_id desc", id);
        for (Map<String, Object> entry : history)
        {
            String checks = (String) entry.get("checks");
            entry.put("checks", checks == null ? List.of() : JSON.parseArray(checks, String.class));
        }
        detail.put("history", history);
        return success(detail);
    }

    @PostMapping("/exh/audit/{id}")
    @PreAuthorize("@ss.hasPermi('exh:audit:list')")
    @Transactional
    public AjaxResult audit(@PathVariable long id, @Valid @RequestBody Decision decision)
    {
        Map<String, Object> rec = row("select r.status,r.teacher_id,s.user_id from edu_recommendation r "
                + "join edu_submission s on s.submission_id=r.submission_id where r.rec_id=? for update", id);
        if (number(rec, "status") != 1) throw new ServiceException("当前成果不在待审状态");
        if (number(rec, "teacher_id") == getUserId()) throw new ServiceException("推荐人不得审核自己的推荐");
        if (decision.approve() && !CHECKS.equals(decision.checks())) throw new ServiceException("须完成五项审核");
        if (!decision.approve() && (decision.opinion() == null || decision.opinion().isBlank()))
        {
            throw new ServiceException("请填写驳回原因");
        }
        jdbc.update("insert into exh_review(rec_id,auditor_id,check_items,result,opinion,audit_time) values(?,?,?,?,?,now())",
                id, getUserId(), JSON.toJSONString(decision.checks()), decision.approve() ? 1 : 2, decision.opinion());
        jdbc.update("update edu_recommendation set status=?,review_opinion=?,auditor_id=?,audit_time=now() where rec_id=?",
                decision.approve() ? 2 : 3, decision.opinion(), getUserId(), id);
        String title = decision.approve() ? "成果审核通过" : "成果审核驳回";
        notify("NT-10", number(rec, "teacher_id"), id, title, decision.opinion());
        if (number(rec, "teacher_id") != number(rec, "user_id"))
        {
            notify("NT-10", number(rec, "user_id"), id, title, decision.opinion());
        }
        return success();
    }

    @PostMapping("/exh/audit/{id}/exhibit")
    @PreAuthorize("@ss.hasPermi('exh:item:list')")
    @Transactional
    public AjaxResult exhibit(@PathVariable long id)
    {
        Map<String, Object> rec = row("select r.status,r.target_zone,r.sign_mode,s.content,t.title,p.student_no,u.nick_name "
                + "from edu_recommendation r join edu_submission s on s.submission_id=r.submission_id "
                + "join edu_task t on t.task_id=s.task_id join sys_user u on u.user_id=s.user_id "
                + "left join edu_user_profile p on p.user_id=s.user_id where r.rec_id=? for update", id);
        if (number(rec, "status") != 2) throw new ServiceException("只有审核通过的成果可转为展项");
        String studentNo = (String) rec.get("student_no");
        String author = switch ((String) rec.get("sign_mode"))
        {
            case "实名" -> (String) rec.get("nick_name");
            case "部分匿名" -> studentNo == null ? "匿名" : "学号尾号 " + studentNo.substring(Math.max(0, studentNo.length()-4));
            default -> "匿名";
        };
        jdbc.update("insert into exh_item(zone_id,title,content,source_rec,author_name,editor_id,create_time) values(?,?,?,?,?,?,now())",
                rec.get("target_zone"), rec.get("title"), rec.get("content"), id, author, getUserId());
        Long itemId = jdbc.queryForObject("select item_id from exh_item where source_rec=?", Long.class, id);
        jdbc.update("update edu_recommendation set status=5,exhibit_id=?,update_time=now() where rec_id=?", itemId, id);
        return success(Map.of("itemId", itemId));
    }

    @PostMapping("/edu/rec/{id}/withdraw")
    @PreAuthorize("@ss.hasPermi('edu:score:recommend')")
    @Transactional
    public AjaxResult withdraw(@PathVariable long id)
    {
        Map<String, Object> rec = row("select teacher_id,status,exhibit_id from edu_recommendation where rec_id=? for update", id);
        long status = number(rec, "status");
        if (status == 5) throw new ServiceException("已成展的推荐不可撤回，请走展项下架流程");
        if (status == 4) throw new ServiceException("该推荐已撤回");
        if (number(rec, "teacher_id") != getUserId() && !scope.hasAnyRole("registrar"))
        {
            throw new ServiceException("仅推荐教师或教务可撤回");
        }
        jdbc.update("update edu_authorization set status=2 where rec_id=? and status=0", id);
        jdbc.update("update edu_recommendation set status=4,withdraw_by=?,withdraw_time=now(),update_time=now() where rec_id=?",
                getUserId(), id);
        return success();
    }

    @PostMapping("/edu/rec/{id}/auth-request")
    @PreAuthorize("@ss.hasPermi('edu:score:recommend')")
    public AjaxResult authRequest(@PathVariable long id)
    {
        Map<String, Object> rec = row("select r.status,r.teacher_id,s.user_id,t.title from edu_recommendation r "
                + "join edu_submission s on s.submission_id=r.submission_id "
                + "join edu_task t on t.task_id=s.task_id where r.rec_id=?", id);
        if (number(rec, "status") != 0) throw new ServiceException("仅待授权状态可再次发起授权请求");
        if (number(rec, "teacher_id") != getUserId() && !scope.hasAnyRole("registrar"))
        {
            throw new ServiceException("仅推荐教师或教务可发起授权请求");
        }
        notify("NT-08", number(rec, "user_id"), id, "成果展示授权请求", rec.get("title") + " · 请在学生端确认署名方式与展示范围");
        return success(Map.of("msg", "已发送站内授权请求"));
    }

    @PostMapping("/edu/rec/{id}/auth-file")
    @PreAuthorize("@ss.hasPermi('edu:score:recommend')")
    @Transactional(rollbackFor = IOException.class)
    public AjaxResult authFile(@PathVariable long id, @RequestParam MultipartFile file,
            @RequestParam String signMode, @RequestParam String scopeMode) throws IOException
    {
        if (!SIGN_MODES.contains(signMode) || !SCOPES.contains(scopeMode))
        {
            throw new ServiceException("请选择有效的署名方式及授权范围");
        }
        Map<String, Object> rec = row("select r.status,r.teacher_id,s.user_id from edu_recommendation r "
                + "join edu_submission s on s.submission_id=r.submission_id where r.rec_id=? for update", id);
        if (number(rec, "status") != 0) throw new ServiceException("仅待授权状态可登记线下授权书");
        if (number(rec, "teacher_id") != getUserId() && !scope.hasAnyRole("registrar"))
        {
            throw new ServiceException("仅推荐教师或教务可登记线下授权书");
        }
        String fileId = storeAuthorization(file);
        int updated = jdbc.update("update edu_authorization set auth_type=1,status=1,sign_mode=?,scope_desc=?,file_id=?,confirm_time=now() "
                + "where rec_id=? and status=0", signMode, scopeMode, fileId, id);
        if (updated != 1) throw new ServiceException("该成果没有待确认的授权记录");
        jdbc.update("update edu_recommendation set status=1,sign_mode=?,scope_desc=?,push_status=1,update_time=now() where rec_id=?",
                signMode, scopeMode, id);
        notify("NT-08", number(rec, "user_id"), id, "成果授权已线下登记", "纸质授权书已登记，成果进入审核流程");
        notifyAuditors(id);
        return success();
    }

    private String storeAuthorization(MultipartFile file) throws IOException
    {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
        {
            throw new ServiceException("请上传不超过 10 MB 的 PDF 授权书");
        }
        String fileId = UUID.randomUUID() + ".pdf";
        Path directory = authorizationDirectory();
        Files.createDirectories(directory);
        Path target = directory.resolve(fileId);
        try (InputStream input = file.getInputStream())
        {
            byte[] signature = input.readNBytes(5);
            if (!java.util.Arrays.equals(signature, new byte[] {'%', 'P', 'D', 'F', '-'}))
            {
                throw new ServiceException("授权书文件须为 PDF");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCompletion(int status)
                {
                    if (status != STATUS_COMMITTED)
                    {
                        try { Files.deleteIfExists(target); }
                        catch (IOException error) { logger.warn("无法清理未登记的授权书 {}", fileId, error); }
                    }
                }
            });
            try (var output = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW))
            {
                output.write(signature);
                input.transferTo(output);
            }
        }
        return fileId;
    }

    @GetMapping("/edu/rec/{id}/auth-file")
    @PreAuthorize("@ss.hasAnyPermi('edu:score:recommend,edu:submission:my,exh:audit:list')")
    public ResponseEntity<FileSystemResource> downloadAuthorization(@PathVariable long id)
    {
        Map<String, Object> authorization = row("select r.teacher_id,a.user_id,a.file_id from edu_recommendation r "
                + "join edu_authorization a on a.rec_id=r.rec_id where r.rec_id=? and a.auth_type=1", id);
        long userId = getUserId();
        if (number(authorization, "teacher_id") != userId && number(authorization, "user_id") != userId
                && !scope.hasAnyRole("registrar", "auditor"))
        {
            return ResponseEntity.notFound().build();
        }
        String fileId = (String) authorization.get("file_id");
        if (fileId == null || !fileId.matches("[0-9a-f-]{36}\\.pdf")) return ResponseEntity.notFound().build();
        Path path = authorizationDirectory().resolve(fileId);
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"authorization.pdf\"")
                .body(new FileSystemResource(path));
    }

    private Path authorizationDirectory()
    {
        Path profile = Path.of(LegalTrainingConfig.getProfile()).toAbsolutePath().normalize();
        return profile.resolveSibling(profile.getFileName() + "-authorizations");
    }

    @GetMapping("/edu/rec/{id}/status")
    @PreAuthorize("@ss.hasAnyPermi('edu:score:recommend,edu:submission:my')")
    public AjaxResult status(@PathVariable long id)
    {
        Map<String, Object> rec = row("select r.rec_id,r.status,r.reason,r.sign_mode as signMode,r.scope_desc as scopeDesc,"
                + "r.exhibit_id as exhibitId,r.review_opinion as opinion,r.auditor_id as auditorId,r.audit_time as auditTime,"
                + "r.push_status as pushStatus,r.withdraw_time as withdrawTime,t.title,s.user_id as studentId,r.teacher_id "
                + "from edu_recommendation r join edu_submission s on s.submission_id=r.submission_id "
                + "join edu_task t on t.task_id=s.task_id where r.rec_id=?", id);
        long uid = getUserId();
        boolean teacher = number(rec, "teacher_id") == uid;
        boolean student = number(rec, "studentId") == uid;
        if (!teacher && !student && !scope.hasAnyRole("registrar", "auditor"))
        {
            throw new ServiceException("记录不存在或当前账号无权访问");
        }
        Map<String, Object> auth = jdbc.queryForList(
                "select auth_type,status,sign_mode,scope_desc,file_id,confirm_time from edu_authorization where rec_id=? "
                + "order by auth_id desc limit 1", id).stream().findFirst().orElse(null);
        List<Map<String, Object>> reviews = jdbc.queryForList(
                "select auditor_id,check_items,result,opinion,audit_time from exh_review where rec_id=? order by review_id", id);
        rec.put("authorization", auth);
        rec.put("reviews", reviews);
        return success(rec);
    }

    private Map<String, Object> row(String query, Object... parameters)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(query, parameters);
        if (rows.isEmpty()) throw new ServiceException("记录不存在或当前账号无权访问");
        return rows.get(0);
    }

    private long number(Map<String, Object> row, String key)
    {
        return ((Number) row.get(key)).longValue();
    }

    private void notify(String event, long user, long id, String title, String content)
    {
        jdbc.update("insert into ops_notification(event_code,receiver_id,title,content,biz_type,biz_id,send_time) "
                + "values(?,?,?,?,'rec',?,now())", event, user, title, content, id);
    }

    private void notifyAuditors(long id)
    {
        jdbc.update("insert into ops_notification(event_code,receiver_id,title,biz_type,biz_id,send_time) "
                + "select distinct 'NT-09',ur.user_id,'新成果待审','rec',?,now() from sys_user_role ur "
                + "join sys_role r on r.role_id=ur.role_id join sys_user u on u.user_id=ur.user_id "
                + "where r.role_key='auditor' and r.status='0' and r.del_flag='0' and u.status='0' and u.del_flag='0'", id);
    }
}
