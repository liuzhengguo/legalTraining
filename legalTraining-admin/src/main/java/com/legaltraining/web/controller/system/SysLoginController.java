package com.legaltraining.web.controller.system;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.legaltraining.common.annotation.Anonymous;
import com.legaltraining.common.constant.Constants;
import com.legaltraining.common.core.domain.AjaxResult;
import com.legaltraining.common.core.domain.entity.SysMenu;
import com.legaltraining.common.core.domain.entity.SysUser;
import com.legaltraining.common.core.domain.model.LoginBody;
import com.legaltraining.common.core.domain.model.LoginUser;
import com.legaltraining.common.core.redis.RedisCache;
import com.legaltraining.common.core.text.Convert;
import com.legaltraining.common.enums.UserStatus;
import com.legaltraining.common.utils.DateUtils;
import com.legaltraining.common.utils.SecurityUtils;
import com.legaltraining.common.utils.StringUtils;
import com.legaltraining.common.utils.ip.IpUtils;
import com.legaltraining.common.utils.uuid.IdUtils;
import com.legaltraining.framework.web.service.SysLoginService;
import com.legaltraining.framework.web.service.SysPermissionService;
import com.legaltraining.framework.web.service.TokenService;
import com.legaltraining.framework.web.service.UserDetailsServiceImpl;
import com.legaltraining.system.service.ISysConfigService;
import com.legaltraining.system.service.ISysMenuService;
import com.legaltraining.system.service.ISysUserService;

/**
 * 登录验证
 * 
 */
@RestController
public class SysLoginController
{
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private ISysUserService userService;

    private static final String SSO_TICKET_PREFIX = "portal:sso:student:";
    private static final int SSO_TICKET_SECONDS = 60;

    /**
     * 登录方法
     * 
     * @param loginBody 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    @PostMapping("/edu/student/login")
    public AjaxResult studentLogin(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.put(Constants.TOKEN, loginService.studentLogin(loginBody.getUsername(), loginBody.getPassword()));
        return ajax;
    }

    /**
     * 门户跨端单点：仅学生本人可换取一次性票据，供学生端免密进入。
     */
    @GetMapping("/portal/sso/student-ticket")
    public AjaxResult studentSsoTicket(HttpServletRequest request)
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || !studentOnly(loginUser.getUser()))
        {
            return AjaxResult.error(403, "仅学生账号可从门户进入学生端");
        }
        String ticket = IdUtils.fastSimpleUUID();
        String payload = JSON.toJSONString(Map.of(
                "username", loginUser.getUsername(),
                "ip", IpUtils.getIpAddr(request),
                "ua", String.valueOf(request.getHeader("User-Agent"))));
        redisCache.setCacheObject(SSO_TICKET_PREFIX + ticket, payload, SSO_TICKET_SECONDS, TimeUnit.SECONDS);
        return AjaxResult.success().put("ticket", ticket).put("expiresIn", SSO_TICKET_SECONDS);
    }

    /**
     * 学生端用一次性票据换取自身登录令牌。票据与 IP、User-Agent 绑定且仅可使用一次。
     */
    @Anonymous
    @PostMapping("/edu/student/sso-login")
    public AjaxResult studentSsoLogin(@RequestBody(required = false) Map<String, String> body,
                                      HttpServletRequest request)
    {
        String ticket = body == null ? null : body.get("ticket");
        if (StringUtils.isEmpty(ticket) || !ticket.matches("[a-f0-9]{32}"))
        {
            return AjaxResult.error(401, "登录票据无效");
        }
        String key = SSO_TICKET_PREFIX + ticket;
        String payload = redisCache.getCacheObject(key);
        redisCache.deleteObject(key);
        if (StringUtils.isEmpty(payload))
        {
            return AjaxResult.error(401, "登录票据已过期，请从门户重新进入");
        }
        JSONObject data = JSON.parseObject(payload);
        String ip = IpUtils.getIpAddr(request);
        String ua = String.valueOf(request.getHeader("User-Agent"));
        if (!ip.equals(data.getString("ip")) || !ua.equals(data.getString("ua")))
        {
            return AjaxResult.error(401, "登录票据校验失败，请从门户重新进入");
        }
        SysUser user = userService.selectUserByUserName(data.getString("username"));
        if (!studentOnly(user)
                || UserStatus.DELETED.getCode().equals(user.getDelFlag())
                || UserStatus.DISABLE.getCode().equals(user.getStatus()))
        {
            return AjaxResult.error(403, "学生账号不可用");
        }
        LoginUser loginUser = (LoginUser) userDetailsService.createLoginUser(user);
        AjaxResult ajax = AjaxResult.success();
        ajax.put(Constants.TOKEN, tokenService.createToken(loginUser));
        return ajax;
    }

    private boolean studentOnly(SysUser user)
    {
        return user != null && !user.isAdmin() && user.getRoles() != null && !user.getRoles().isEmpty()
                && user.getRoles().stream().allMatch(role -> "student".equals(role.getRoleKey()));
    }

    /**
     * 获取用户信息
     * 
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        if (!loginUser.getPermissions().equals(permissions))
        {
            loginUser.setPermissions(permissions);
            tokenService.refreshToken(loginUser);
        }
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        ajax.put("isDefaultModifyPwd", initPasswordIsModify(user.getPwdUpdateDate()));
        ajax.put("isPasswordExpired", passwordIsExpiration(user.getPwdUpdateDate()));
        return ajax;
    }

    /**
     * 获取路由信息
     * 
     * @return 路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters()
    {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }
    
    // 检查初始密码是否提醒修改
    public boolean initPasswordIsModify(Date pwdUpdateDate)
    {
        Integer initPasswordModify = Convert.toInt(configService.selectConfigByKey("sys.account.initPasswordModify"));
        return initPasswordModify != null && initPasswordModify == 1 && pwdUpdateDate == null;
    }

    // 检查密码是否过期
    public boolean passwordIsExpiration(Date pwdUpdateDate)
    {
        Integer passwordValidateDays = Convert.toInt(configService.selectConfigByKey("sys.account.passwordValidateDays"));
        if (passwordValidateDays != null && passwordValidateDays > 0)
        {
            if (StringUtils.isNull(pwdUpdateDate))
            {
                // 如果从未修改过初始密码，直接提醒过期
                return true;
            }
            Date nowDate = DateUtils.getNowDate();
            return DateUtils.differentDaysByMillisecond(nowDate, pwdUpdateDate) > passwordValidateDays;
        }
        return false;
    }
}
