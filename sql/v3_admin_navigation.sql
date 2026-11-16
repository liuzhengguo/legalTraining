-- 为已有 V3.0 库补齐管理端页面入口及按钮权限。保留已配置的菜单记录。
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO sys_menu VALUES (2000, '工作台', 0, 0, '/portal', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'dashboard', 'admin', sysdate(), '', NULL, 'E8 统一门户') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2001, '门户首页', 2000, 1, 'index', 'portal/index', NULL, 'BusinessPortal', 1, 0, 'C', '0', '0', 'portal:index:view', 'home', 'admin', sysdate(), '', NULL, 'OP-01-01/02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2002, '我的待办', 2000, 2, 'todo', 'portal/todo', NULL, '', 1, 0, 'C', '0', '0', 'portal:todo:list', 'message', 'admin', sysdate(), '', NULL, 'OP-01-03') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2013, '班级管理', 2011, 3, 'class', 'edu/class/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:class:list', 'peoples', 'admin', sysdate(), '', NULL, 'MG-01-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2014, '分组管理', 2011, 4, 'group', 'edu/group/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:group:list', 'component', 'admin', sysdate(), '', NULL, 'MG-01-03') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2015, '学生账号', 2011, 5, 'student', 'edu/student/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:student:list', 'user', 'admin', sysdate(), '', NULL, 'MG-01-04/05/06') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2017, '账号授权', 2016, 1, 'grant', 'edu/grant/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:grant:list', 'people', 'admin', sysdate(), '', NULL, 'MG-02-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2019, '教学监管', 2010, 3, 'monitor', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', sysdate(), '', NULL, NULL) ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2020, '课程任务总览', 2019, 1, 'course', 'edu/monitor/course', NULL, 'CourseMonitor', 1, 0, 'C', '0', '0', 'edu:monitor:course', 'chart', 'admin', sysdate(), '', NULL, 'MG-03-01') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2021, '批阅进度', 2019, 2, 'review', 'edu/monitor/review', NULL, 'ReviewMonitor', 1, 0, 'C', '0', '0', 'edu:monitor:review', 'edit', 'admin', sysdate(), '', NULL, 'MG-03-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2022, '数据导出', 2010, 4, 'export', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'download', 'admin', sysdate(), '', NULL, NULL) ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2023, '成绩汇总', 2022, 1, 'score', 'edu/score/summary', NULL, 'EduScoreSummary', 1, 0, 'C', '0', '0', 'edu:score:summary', 'excel', 'admin', sysdate(), '', NULL, 'MG-04-01') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2024, '展厅监管', 2010, 5, 'exh', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'eye-open', 'admin', sysdate(), '', NULL, NULL) ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2025, '展区开放配置', 2024, 1, 'zone-level', 'exh/zone/level', NULL, '', 1, 0, 'C', '0', '0', 'exh:zone:level', 'tree-table', 'admin', sysdate(), '', NULL, 'MG-05-01') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2026, '紧急下架审批', 2024, 2, 'offline', 'exh/offline/audit', NULL, '', 1, 0, 'C', '0', '0', 'exh:offline:audit', 'checkbox', 'admin', sysdate(), '', NULL, 'MG-05-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2027, '专业管理', 2011, 2, 'major', 'edu/major/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:major:list', 'tree-table', 'admin', sysdate(), '', NULL, 'MG-01-02 支撑数据') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2032, '资源管理', 2030, 2, 'resource', 'edu/resource/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:resource:list', 'file', 'admin', sysdate(), '', NULL, 'TC-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2067, '专业查询', 2027, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:query',  '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2068, '专业新增', 2027, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:add',    '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2069, '专业修改', 2027, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:edit',   '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2070, '专业删除', 2027, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:remove', '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2071, '专业导出', 2027, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:export', '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2072, '班级查询', 2013, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:query',  '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2073, '班级新增', 2013, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:add',    '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2074, '班级修改', 2013, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:edit',   '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2075, '班级删除', 2013, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:remove', '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2076, '班级导出', 2013, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:export', '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2077, '分组查询', 2014, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:query',  '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2078, '分组新增', 2014, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:add',    '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2079, '分组修改', 2014, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:edit',   '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2080, '分组删除', 2014, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:remove', '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2081, '学生查询', 2015, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:query',    '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2082, '学生新增', 2015, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:add',      '#', 'admin', sysdate(), '', NULL, 'MG-01-05') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2083, '学生修改', 2015, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:edit',     '#', 'admin', sysdate(), '', NULL, 'MG-01-05 启停') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2084, '学生导出', 2015, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:export',   '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2085, '批量导入', 2015, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:import',   '#', 'admin', sysdate(), '', NULL, 'MG-01-04') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2086, '重置密码', 2015, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:resetPwd', '#', 'admin', sysdate(), '', NULL, 'MG-01-05') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2087, '转班',     2015, 7, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:transfer', '#', 'admin', sysdate(), '', NULL, 'MG-01-06') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2088, '查询授权', 2017, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:grant:query', '#', 'admin', sysdate(), '', NULL, 'MG-02-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2089, '修改授权', 2017, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:grant:edit',  '#', 'admin', sysdate(), '', NULL, 'MG-02-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2104, '资源查询', 2032, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:query',  '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2105, '资源新增', 2032, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:add',    '#', 'admin', sysdate(), '', NULL, 'TC-02-01') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2106, '资源修改', 2032, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:edit',   '#', 'admin', sysdate(), '', NULL, 'TC-02-03/04') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2107, '资源删除', 2032, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:remove', '#', 'admin', sysdate(), '', NULL, '') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2130, '成果包导出', 2022, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:export:package', '#', 'admin', sysdate(), '', NULL, 'MG-04-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2131, '台账导出',   2022, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:export:ledger',  '#', 'admin', sysdate(), '', NULL, 'MG-04-02') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_menu VALUES (2132, '导出记录',   2022, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:export:query',   '#', 'admin', sysdate(), '', NULL, 'MG-04-01') ON DUPLICATE KEY UPDATE menu_id=sys_menu.menu_id;
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m
ON (r.role_key='registrar' AND (m.menu_id BETWEEN 2010 AND 2027 OR m.menu_id BETWEEN 2060 AND 2132))
OR (r.role_key='teacher' AND m.menu_id IN (2032,2104,2105,2106,2107))
OR (r.role_key IN ('registrar','teacher','student','editor','auditor') AND m.menu_id IN (2000,2001,2002))
WHERE r.status='0' AND r.del_flag='0'
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
UPDATE sys_menu SET route_name='BusinessPortal' WHERE menu_id=2001 AND COALESCE(route_name,'')='';
UPDATE sys_menu SET route_name='CourseMonitor' WHERE menu_id=2020 AND COALESCE(route_name,'')='';
UPDATE sys_menu SET route_name='ReviewMonitor' WHERE menu_id=2021 AND COALESCE(route_name,'')='';
UPDATE sys_menu SET route_name='EduScoreSummary' WHERE menu_id=2023 AND COALESCE(route_name,'')='';
COMMIT;
