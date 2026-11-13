package com.legaltraining.business.edu.service;

import java.util.Map;

/**
 * 统一门户（OP-01-01/02/03）
 */
public interface IPortalService
{
    /**
     * 门户首页：账号信息、当前学期、按权限过滤的入口卡片
     */
    public Map<String, Object> index();

    /**
     * 我的待办：待批 / 待审 / 待交 / 待授权 四类计数
     */
    public Map<String, Object> todo();
}
