package com.legaltraining.business.edu.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.legaltraining.business.edu.domain.EduTerm;
import com.legaltraining.business.edu.mapper.EduTermMapper;
import com.legaltraining.business.edu.mapper.PortalMapper;
import com.legaltraining.business.edu.service.IPortalService;
import com.legaltraining.common.core.domain.entity.SysUser;
import com.legaltraining.common.core.domain.model.LoginUser;
import com.legaltraining.common.utils.SecurityUtils;

/**
 * 统一门户 服务实现
 *
 * 入口卡片不写死给谁看，而是用菜单权限点判定：
 * 有 edu:term:list 才看到"平台管理"，有 edu:course:list 才看到"教师工作室"，
 * 这样 MG-02-03 的菜单授权与门户入口天然一致，不需要第二套配置。
 */
@Service
public class PortalServiceImpl implements IPortalService
{
    @Autowired
    private PortalMapper portalMapper;

    @Autowired
    private EduTermMapper eduTermMapper;

    @Override
    public Map<String, Object> index()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        Set<String> perms = new LinkedHashSet<>(loginUser.getPermissions() == null
                ? new java.util.HashSet<String>() : loginUser.getPermissions());
        boolean superAdmin = perms.contains("*:*:*");

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("userId", user.getUserId());
        account.put("userName", user.getUserName());
        account.put("nickName", user.getNickName());
        account.put("deptName", user.getDept() == null ? null : user.getDept().getDeptName());
        account.put("avatar", user.getAvatar());
        result.put("account", account);

        EduTerm term = eduTermMapper.selectCurrentTerm();
        Map<String, Object> termInfo = new LinkedHashMap<>();
        termInfo.put("termId", term == null ? null : term.getTermId());
        termInfo.put("termName", term == null ? "未设置当前学期" : term.getTermName());
        result.put("term", termInfo);

        List<Map<String, Object>> cards = new ArrayList<>();
        addCard(cards, perms, superAdmin, "admin", "平台管理", "/admin",
                "edu:term:list", "组织、账号、权限、监管与导出", "system");
        addCard(cards, perms, superAdmin, "teacher", "教师工作室", "/teacher",
                "edu:course:list", "建课、发任务、批阅、出成绩", "education");
        addCard(cards, perms, superAdmin, "exhibit", "展厅内容", "/exhibit",
                "exh:zone:list", "展区展项、发布审核、设备管理", "build");
        addCard(cards, perms, superAdmin, "todo", "我的待办", "/portal/todo",
                "portal:todo:list", "待批、待审、待交、待授权", "message");
        result.put("cards", cards);
        result.put("todo", todo().get("counts"));
        return result;
    }

    @Override
    public Map<String, Object> todo()
    {
        Long userId = SecurityUtils.getUserId();
        Set<String> perms = new LinkedHashSet<>(SecurityUtils.getLoginUser().getPermissions() == null
                ? new java.util.HashSet<String>() : SecurityUtils.getLoginUser().getPermissions());
        boolean superAdmin = perms.contains("*:*:*");

        int review = 0, audit = 0, submit = 0, authorize = 0;
        // 只查该账号确实有能力处理的类别，避免给无关数字
        if (superAdmin || hasAny(perms, "edu:review:list", "edu:task:list"))
        {
            review = portalMapper.countPendingReview(userId);
        }
        if (superAdmin || hasAny(perms, "exh:audit:list"))
        {
            audit = portalMapper.countPendingAudit(userId);
        }
        if (superAdmin || hasAny(perms, "student:task:list", "portal:todo:list"))
        {
            submit = portalMapper.countPendingSubmit(userId);
        }
        if (superAdmin || hasAny(perms, "student:rec:list", "portal:todo:list"))
        {
            authorize = portalMapper.countPendingAuthorize(userId);
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("review", review);
        counts.put("audit", audit);
        counts.put("submit", submit);
        counts.put("authorize", authorize);
        counts.put("total", review + audit + submit + authorize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("counts", counts);
        result.put("labels", labels());
        result.put("message", counts.get("total").equals(0) ? "暂无待办" : "共 " + counts.get("total") + " 项待办");
        return result;
    }

    private List<Map<String, Object>> labels()
    {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(label("review", "待批阅", "/teacher/review", "已提交未批的作业"));
        list.add(label("audit", "待审核", "/exhibit/audit", "推荐的成果等待审核"));
        list.add(label("submit", "待提交", "/portal/todo?tab=submit", "我还没交的实训任务"));
        list.add(label("authorize", "待授权", "/portal/todo?tab=authorize", "推荐到我的成果需确认署名"));
        return list;
    }

    private Map<String, Object> label(String key, String name, String path, String desc)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("path", path);
        m.put("desc", desc);
        return m;
    }

    private void addCard(List<Map<String, Object>> cards, Set<String> perms, boolean superAdmin,
                         String code, String name, String path, String perm, String desc, String icon)
    {
        if (!superAdmin && !perms.contains(perm))
        {
            return;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("code", code);
        card.put("name", name);
        card.put("path", path);
        card.put("desc", desc);
        card.put("icon", icon);
        cards.add(card);
    }

    private boolean hasAny(Set<String> perms, String... keys)
    {
        for (String k : keys)
        {
            if (perms.contains(k))
            {
                return true;
            }
            // 兼容通配：edu:*:* 之类
            int idx = k.lastIndexOf(':');
            if (idx > 0 && perms.contains(k.substring(0, idx) + ":*")) {
                return true;
            }
        }
        return false;
    }
}
