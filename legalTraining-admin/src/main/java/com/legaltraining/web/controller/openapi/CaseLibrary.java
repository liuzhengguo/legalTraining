package com.legaltraining.web.controller.openapi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.legaltraining.common.exception.ServiceException;
import com.legaltraining.system.service.ISysConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CaseLibrary
{
    private final ISysConfigService configs;
    private final JdbcTemplate db;

    public CaseLibrary(ISysConfigService configs, JdbcTemplate db)
    {
        this.configs = configs;
        this.db = db;
    }

    public List<Map<String, Object>> references(String type, long id)
    {
        return db.queryForList("""
                select case_id as id,title from ops_case_reference
                where target_type=? and target_id=? order by created_at,case_id
                """, type, id);
    }

    public List<?> availableReferences(List<?> entries)
    {
        return entries.isEmpty() || Boolean.TRUE.equals(cases(null, 1, 1).get("degrade")) ? List.of() : entries;
    }

    public Map<String, Object> referencedCase(List<?> entries, String caseId)
    {
        if (entries.stream().noneMatch(entry -> entry instanceof Map<?, ?> fields && caseId.equals(fields.get("id"))))
            throw new ServiceException("内容未关联此案例");
        return caseDetail(caseId);
    }

    @org.springframework.beans.factory.annotation.Value("${legal-training.case-library.token:}")
    private String caseLibraryToken;

    public Map<String, Object> cases(String keyword,
            int pageNum,
            int pageSize)
    {
        int page = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), 50);
        String query = "?pageNum=" + page + "&pageSize=" + size;
        if (keyword != null) query += "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNum", page);
        data.put("pageSize", size);
        JSONObject provider = findCases("", query);
        if (provider != null && provider.get("rows") instanceof com.alibaba.fastjson2.JSONArray rows
                && provider.get("total") instanceof Number total)
        {
            data.put("rows", rows.stream().filter(JSONObject.class::isInstance).limit(size)
                    .map(row -> caseFields((JSONObject) row, false)).filter(row -> row.containsKey("id") && row.containsKey("title")).toList());
            data.put("total", Math.max(0L, total.longValue()));
            data.put("degrade", false);
        }
        else
        {
            data.put("rows", List.of());
            data.put("total", 0);
            data.put("degrade", true);
        }
        return data;
    }

    public Map<String, Object> caseDetail(String id)
    {
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new ServiceException("案例编号无效");
        JSONObject provider = findCases("/" + id, "");
        Map<String, Object> detail = provider == null ? Map.of() : caseFields(provider, true);
        boolean valid = id.equals(detail.get("id")) && detail.containsKey("title");
        return valid ? Map.of("degrade", false, "case", detail) : Map.of("degrade", true);
    }

    private Map<String, Object> caseFields(JSONObject provider, boolean detail)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> fields = detail ? List.of("id", "title", "summary", "source", "content")
                : List.of("id", "title", "summary", "source");
        for (String field : fields)
        {
            Object value = provider.get(field);
            if (value instanceof String text) result.put(field, text);
            else if ("id".equals(field) && value instanceof Number number) result.put(field, number.toString());
        }
        return result;
    }

    private JSONObject findCases(String path, String query)
    {
        String base = configs.selectConfigByKey("biz.openapi.caseUrl");
        if (base == null || base.isBlank()) return null;
        java.net.HttpURLConnection connection = null;
        try
        {
            java.net.URI uri = java.net.URI.create(base);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || ("http".equalsIgnoreCase(uri.getScheme())
                        && List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()))))
                return null;
            connection = (java.net.HttpURLConnection) java.net.URI.create(base.replaceAll("/+$", "") + path + query)
                    .toURL().openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (caseLibraryToken != null && !caseLibraryToken.isBlank())
                connection.setRequestProperty("Authorization", "Bearer " + caseLibraryToken);
            if (connection.getResponseCode() != 200) return null;
            try (var input = connection.getInputStream())
            {
                byte[] body = input.readNBytes(1024 * 1024 + 1);
                if (body.length > 1024 * 1024) return null;
                return JSON.parseObject(new String(body, StandardCharsets.UTF_8));
            }
        }
        catch (java.io.IOException | RuntimeException ex)
        {
            return null;
        }
        finally
        {
            if (connection != null) connection.disconnect();
        }
    }

}
