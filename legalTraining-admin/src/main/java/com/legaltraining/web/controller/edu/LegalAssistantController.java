package com.legaltraining.web.controller.edu;

import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.legaltraining.common.core.controller.BaseController;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.page.TableDataInfo;
import com.legaltraining.common.exception.ServiceException;

@RestController
public class LegalAssistantController extends BaseController
{
    private static final String FIELDS = "record_id as recordId, a.session_id as sessionId, question, answer, a.status, error_message as errorMessage, "
            + "date_format(a.create_time, '%Y-%m-%d %H:%i:%s') as createTime";
    private static final String SYSTEM_PROMPT = """
            你叫小律法，是高校法律实训学习助手，使用简体中文解释法律概念、案例分析思路和文书练习。
            区分事实、假设和待核实信息。不要编造法律条文、案例、司法解释或来源。
            涉及正式法律事务时，明确说明回答仅用于学习参考，并提示核对现行规则或咨询专业人员。
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RestClient ark;
    private final String model;
    private final String apiKey;

    public LegalAssistantController(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            @Value("${ARK_API_KEY:}") String apiKey,
            @Value("${ARK_MODEL_ENDPOINT:ep-20261116224921-kpbg8}") String model,
            @Value("${ARK_BASE_URL:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl)
    {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.apiKey = apiKey;
        this.model = model;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(6));
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.ark = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @PreAuthorize("@ss.hasRole('student')")
    @GetMapping("/edu/assistant/sessions")
    public AjaxResult sessions()
    {
        return success(jdbc.queryForList("select session_id as sessionId,title,"
                + "date_format(update_time, '%Y-%m-%d %H:%i:%s') as updateTime from edu_legal_assistant_session "
                + "where user_id=? order by update_time desc, session_id desc", getUserId()));
    }

    @PreAuthorize("@ss.hasRole('student')")
    @GetMapping("/edu/assistant/history")
    public AjaxResult history(@RequestParam(required = false) Long sessionId)
    {
        if (sessionId != null)
        {
            requireSession(sessionId, getUserId());
            return success(jdbc.queryForList("select " + FIELDS + " from edu_legal_assistant_record a "
                    + "where user_id=? and session_id=? order by record_id", getUserId(), sessionId));
        }
        var records = jdbc.queryForList("select " + FIELDS + " from edu_legal_assistant_record a "
                + "where user_id=? order by record_id desc limit 30", getUserId());
        Collections.reverse(records);
        return success(records);
    }

    @PreAuthorize("@ss.hasRole('student')")
    @PostMapping("/edu/assistant/question")
    public AjaxResult ask(@RequestBody Question input)
    {
        String question = input.question() == null ? "" : input.question().strip();
        if (question.isEmpty() || question.length() > 2000)
        {
            throw new ServiceException("请输入 1 至 2000 字的问题");
        }

        Long userId = getUserId();
        PendingQuestion pending = transaction.execute(status -> {
            Long sessionId = input.sessionId();
            if (sessionId == null)
            {
                String title = question.substring(0, question.offsetByCodePoints(0, Math.min(32, question.codePointCount(0, question.length()))));
                sessionId = insert("insert into edu_legal_assistant_session(user_id,title) values(?,?)", userId, title);
            }
            else
            {
                requireSession(sessionId, userId);
                jdbc.update("update edu_legal_assistant_session set update_time=now() where session_id=? and user_id=?", sessionId, userId);
            }
            long recordId = insert("insert into edu_legal_assistant_record(user_id,session_id,question,model_endpoint) values(?,?,?,?)",
                    userId, sessionId, question, model);
            return new PendingQuestion(sessionId, recordId);
        });
        long recordId = pending.recordId();

        if (apiKey.isBlank())
        {
            return fail(recordId, "法律助手服务暂未就绪，请联系管理员");
        }

        String answer;
        try
        {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            var history = jdbc.queryForList("select question,answer from edu_legal_assistant_record "
                    + "where user_id=? and session_id=? and record_id<? and status='answered' order by record_id desc limit 4",
                    userId, pending.sessionId(), recordId);
            Collections.reverse(history);
            for (var entry : history)
            {
                messages.add(Map.of("role", "user", "content", (String) entry.get("question")));
                messages.add(Map.of("role", "assistant", "content", (String) entry.get("answer")));
            }
            messages.add(Map.of("role", "user", "content", question));
            Completion response = ark.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(Map.of("model", model, "messages", messages, "max_tokens", 1800))
                    .retrieve().body(Completion.class);
            answer = response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null ? null : response.choices().get(0).message().content();
        }
        catch (RestClientException exception)
        {
            return fail(recordId, "模型暂时无法响应，请稍后重试");
        }
        if (answer == null || answer.isBlank())
        {
            return fail(recordId, "模型未返回可显示的内容，请重试");
        }
        jdbc.update("update edu_legal_assistant_record set status='answered',answer=? where record_id=?", answer, recordId);
        return record(recordId);
    }

    @PreAuthorize("@ss.hasPermi('ops:legalAssistant:list')")
    @GetMapping("/ops/legal-assistant/records")
    public TableDataInfo records(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize)
    {
        int limit = Math.max(1, Math.min(pageSize, 100));
        long offset = (long) (Math.max(pageNum, 1) - 1) * limit;
        String search = keyword.strip();
        String term = "%" + search + "%";
        String from = " from edu_legal_assistant_record a left join sys_user u on u.user_id=a.user_id "
                + "where (a.question like ? or u.user_name like ? or u.nick_name like ?)";
        var rows = jdbc.queryForList("select " + FIELDS + ", a.model_endpoint as modelEndpoint, "
                + "u.user_name as userName, u.nick_name as nickName" + from
                + " order by a.record_id desc limit ? offset ?", term, term, term, limit, offset);
        TableDataInfo result = new TableDataInfo(rows, jdbc.queryForObject("select count(*)" + from, Long.class, term, term, term));
        result.setCode(200);
        return result;
    }

    private AjaxResult fail(long id, String message)
    {
        jdbc.update("update edu_legal_assistant_record set status='failed',error_message=? where record_id=?", message, id);
        return record(id);
    }

    private void requireSession(Long sessionId, Long userId)
    {
        if (jdbc.queryForObject("select count(*) from edu_legal_assistant_session where session_id=? and user_id=?",
                Long.class, sessionId, userId) == 0)
        {
            throw new ServiceException("会话不存在或不可访问");
        }
    }

    private long insert(String sql, Object... values)
    {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++)
            {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private AjaxResult record(long id)
    {
        return success(jdbc.queryForMap("select " + FIELDS + " from edu_legal_assistant_record a where record_id=?", id));
    }

    private record PendingQuestion(long sessionId, long recordId) {}
    public record Question(String question, Long sessionId) {}
    public record Completion(List<Choice> choices) {}
    public record Choice(Message message) {}
    public record Message(String content) {}
}

