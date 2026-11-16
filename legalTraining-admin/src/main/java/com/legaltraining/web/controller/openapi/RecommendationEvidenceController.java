package com.legaltraining.web.controller.openapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.legaltraining.business.ops.domain.OpsApp;
import com.legaltraining.common.config.LegalTrainingConfig;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.utils.file.FileUtils;
import com.legaltraining.web.interceptor.OpenApiInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/openapi/v1/rec/{recId}/evidence")
public class RecommendationEvidenceController
{
    private final JdbcTemplate db;
    private final String receiverApp;

    public RecommendationEvidenceController(JdbcTemplate db,
            @Value("${legal-training.recommendation-push.callback-app-id:}") String receiverApp)
    {
        this.db = db;
        this.receiverApp = receiverApp;
    }

    @GetMapping
    public ResponseEntity<AjaxResult> list(@PathVariable long recId, @RequestAttribute(OpenApiInterceptor.ATTR_APP) OpsApp app)
    {
        Map<String, Object> authorization = authorized(recId, app);
        if (authorization == null) return ResponseEntity.notFound().build();
        List<Map<String, Object>> files = db.queryForList("""
                select cast(id as char) as id,file_name as name,file_size as size from edu_submission_file
                where submission_id=? and version=? order by sort_no,id
                """, authorization.get("submission_id"), authorization.get("version"));
        if (authorization.get("file_id") != null) files.add(Map.of("id", "authorization", "name", "授权书.pdf"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(files));
    }

    @GetMapping("/{token:authorization|[0-9]+}")
    public ResponseEntity<FileSystemResource> file(@PathVariable long recId, @PathVariable String token,
            @RequestAttribute(OpenApiInterceptor.ATTR_APP) OpsApp app) throws IOException
    {
        Map<String, Object> authorization = authorized(recId, app);
        if (authorization == null) return ResponseEntity.notFound().build();
        Path file;
        String name;
        if ("authorization".equals(token))
        {
            if (!(authorization.get("file_id") instanceof String fileId) || !fileId.matches("[0-9a-f-]{36}\\.pdf"))
                return ResponseEntity.notFound().build();
            Path profile = Path.of(LegalTrainingConfig.getProfile()).toAbsolutePath().normalize();
            Path root = profile.resolveSibling(profile.getFileName() + "-authorizations");
            file = root.resolve(fileId);
            if (!Files.isRegularFile(file) || !file.toRealPath().startsWith(root.toRealPath()))
                return ResponseEntity.notFound().build();
            name = "授权书.pdf";
        }
        else
        {
            var files = db.queryForList("""
                    select file_id,file_name from edu_submission_file where id=? and submission_id=? and version=?
                    """, token, authorization.get("submission_id"), authorization.get("version"));
            if (files.isEmpty()) return ResponseEntity.notFound().build();
            file = FileUtils.resolveStoredUpload((String) files.get(0).get("file_id"));
            if (file == null) return ResponseEntity.notFound().build();
            name = (String) files.get(0).get("file_name");
        }
        String safeName = name == null ? "attachment" : name.replaceAll("[\\p{Cntrl}\\\\/]", "_");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(safeName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    private Map<String, Object> authorized(long recId, OpsApp app)
    {
        if (receiverApp.isBlank() || !receiverApp.equals(app.getAppCode())) return null;
        return db.queryForList("""
                select snapshot.submission_id,snapshot.submission_version as version,a.file_id
                from edu_recommendation r join ops_rec_evidence_snapshot snapshot on snapshot.rec_id=r.rec_id
                join edu_authorization a on a.rec_id=r.rec_id and a.status=1
                join ops_rec_push_job j on j.rec_id=r.rec_id
                where r.rec_id=? and r.status in (1,2) and (j.status=1 or j.lease_id is not null)
                order by a.auth_id desc limit 1
                """, recId).stream().findFirst().orElse(null);
    }
}
