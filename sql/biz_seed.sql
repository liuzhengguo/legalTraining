-- ----------------------------------------------------------------------------
-- 法律实训数字平台 · 初始化数据
-- 依赖：先执行 sql/legalTraining.sql（RuoYi 框架库），再执行本文件。
-- 内容：1) 业务字典  2) 内置角色  3) 等级制区间  4) 通知模板
--       5) 三个视图的菜单骨架（按钮级权限点由代码生成器按命名规范补齐）
-- 幂等：全部使用 INSERT ... ON DUPLICATE KEY UPDATE 或先删后插，可重复执行。
-- ----------------------------------------------------------------------------
SET NAMES utf8mb4;

-- ============================================================================
-- 1. 业务字典
-- ============================================================================
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark) VALUES
('任务状态',     'biz_task_status',     '0', 'admin', sysdate(), '状态机 6.1'),
('提交状态',     'biz_submit_status',   '0', 'admin', sysdate(), '状态机 6.2'),
('推荐状态',     'biz_rec_status',      '0', 'admin', sysdate(), '状态机 6.3'),
('展项状态',     'biz_item_status',     '0', 'admin', sysdate(), '状态机 6.4'),
('课程状态',     'biz_course_status',   '0', 'admin', sysdate(), NULL),
('资源状态',     'biz_resource_status', '0', 'admin', sysdate(), NULL),
('成绩状态',     'biz_score_status',    '0', 'admin', sysdate(), NULL),
('设备状态',     'biz_device_status',   '0', 'admin', sysdate(), NULL),
('截止策略',     'biz_deadline_policy', '0', 'admin', sysdate(), 'TC-03-02 四种策略'),
('完成方式',     'biz_finish_mode',     '0', 'admin', sysdate(), 'TC-03-04'),
('评价方式',     'biz_score_mode',      '0', 'admin', sysdate(), 'TC-03-03'),
('展区开放级别', 'biz_open_level',      '0', 'admin', sysdate(), 'EF-06-01 三级'),
('展区版式',     'biz_zone_layout',     '0', 'admin', sysdate(), 'EF-01-02 网页版三种版式'),
('展项版式',     'biz_item_type',       '0', 'admin', sysdate(), 'EF-02-01 四种版式'),
('素材类型',     'biz_media_type',      '0', 'admin', sysdate(), 'EM-02-03'),
('可见范围',     'biz_visible_scope',   '0', 'admin', sysdate(), 'TC-02-04'),
('用户类别',     'biz_user_category',   '0', 'admin', sysdate(), NULL),
('分享渠道',     'biz_channel',         '0', 'admin', sysdate(), NULL),
('通知事件',     'biz_notice_event',    '0', 'admin', sysdate(), '11.2 NT-01~13'),
('下架原因',     'biz_offline_reason',  '0', 'admin', sysdate(), 'MG-05-02')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name);

DELETE FROM sys_dict_data WHERE dict_type LIKE 'biz_%';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time) VALUES
-- 任务状态 6.1
(1, '草稿',   '0', 'biz_task_status', '', 'info',    'Y', '0', 'admin', sysdate()),
(2, '进行中', '1', 'biz_task_status', '', 'primary', 'N', '0', 'admin', sysdate()),
(3, '已截止', '2', 'biz_task_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(4, '已取消', '3', 'biz_task_status', '', 'danger',  'N', '0', 'admin', sysdate()),
(5, '已归档', '4', 'biz_task_status', '', 'info',    'N', '0', 'admin', sysdate()),
-- 提交状态 6.2
(1, '未提交',   '0', 'biz_submit_status', '', 'info',    'Y', '0', 'admin', sysdate()),
(2, '草稿',     '1', 'biz_submit_status', '', 'info',    'N', '0', 'admin', sysdate()),
(3, '已提交',   '2', 'biz_submit_status', '', 'primary', 'N', '0', 'admin', sysdate()),
(4, '已批阅',   '3', 'biz_submit_status', '', 'success', 'N', '0', 'admin', sysdate()),
(5, '已退回',   '4', 'biz_submit_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(6, '逾期未交', '5', 'biz_submit_status', '', 'danger',  'N', '0', 'admin', sysdate()),
(7, '任务取消', '6', 'biz_submit_status', '', 'info',    'N', '0', 'admin', sysdate()),
-- 推荐状态 6.3
(1, '待授权', '0', 'biz_rec_status', '', 'warning', 'Y', '0', 'admin', sysdate()),
(2, '待审核', '1', 'biz_rec_status', '', 'primary', 'N', '0', 'admin', sysdate()),
(3, '已通过', '2', 'biz_rec_status', '', 'success', 'N', '0', 'admin', sysdate()),
(4, '已驳回', '3', 'biz_rec_status', '', 'danger',  'N', '0', 'admin', sysdate()),
(5, '已撤回', '4', 'biz_rec_status', '', 'info',    'N', '0', 'admin', sysdate()),
(6, '已成展', '5', 'biz_rec_status', '', 'success', 'N', '0', 'admin', sysdate()),
-- 展项状态 6.4
(1, '草稿',     '0', 'biz_item_status', '', 'info',    'Y', '0', 'admin', sysdate()),
(2, '待发布',   '1', 'biz_item_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(3, '已发布',   '2', 'biz_item_status', '', 'success', 'N', '0', 'admin', sysdate()),
(4, '已下架',   '3', 'biz_item_status', '', 'info',    'N', '0', 'admin', sysdate()),
(5, '发布失败', '4', 'biz_item_status', '', 'danger',  'N', '0', 'admin', sysdate()),
-- 课程状态
(1, '草稿',   '0', 'biz_course_status', '', 'info',    'Y', '0', 'admin', sysdate()),
(2, '已发布', '1', 'biz_course_status', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '已归档', '2', 'biz_course_status', '', 'info',    'N', '0', 'admin', sysdate()),
-- 资源状态
(1, '已上架', '0', 'biz_resource_status', '', 'success', 'Y', '0', 'admin', sysdate()),
(2, '已下架', '1', 'biz_resource_status', '', 'info',    'N', '0', 'admin', sysdate()),
-- 成绩状态
(1, '待发布', '0', 'biz_score_status', '', 'warning', 'Y', '0', 'admin', sysdate()),
(2, '已发布', '1', 'biz_score_status', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '已更正', '2', 'biz_score_status', '', 'primary', 'N', '0', 'admin', sysdate()),
-- 设备状态
(1, '待激活', '0', 'biz_device_status', '', 'info',    'Y', '0', 'admin', sysdate()),
(2, '在线',   '1', 'biz_device_status', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '离线',   '2', 'biz_device_status', '', 'warning', 'N', '0', 'admin', sysdate()),
(4, '停用',   '3', 'biz_device_status', '', 'danger',  'N', '0', 'admin', sysdate()),
-- 截止策略
(1, '严格（截止即锁定）',   '0', 'biz_deadline_policy', '', 'danger',  'Y', '0', 'admin', sysdate()),
(2, '宽限（N 天内可交）',   '1', 'biz_deadline_policy', '', 'warning', 'N', '0', 'admin', sysdate()),
(3, '审批补交',             '2', 'biz_deadline_policy', '', 'primary', 'N', '0', 'admin', sysdate()),
(4, '不限（仅提示逾期）',   '3', 'biz_deadline_policy', '', 'info',    'N', '0', 'admin', sysdate()),
-- 完成方式
(1, '个人',     '0', 'biz_finish_mode', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '教师分组', '1', 'biz_finish_mode', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '自由组队', '2', 'biz_finish_mode', '', 'warning', 'N', '0', 'admin', sysdate()),
-- 评价方式
(1, '百分制',       '0', 'biz_score_mode', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '等级制',       '1', 'biz_score_mode', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '多维度加权',   '2', 'biz_score_mode', '', 'warning', 'N', '0', 'admin', sysdate()),
-- 展区开放级别
(1, '未开放',   '0', 'biz_open_level', '', 'info',    'Y', '0', 'admin', sysdate()),
(2, '内部开放', '1', 'biz_open_level', '', 'warning', 'N', '0', 'admin', sysdate()),
(3, '对外开放', '2', 'biz_open_level', '', 'success', 'N', '0', 'admin', sysdate()),
-- 展区版式（网页版，无三维）
(1, '图墙',   'grid',     'biz_zone_layout', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '长页',   'flow',     'biz_zone_layout', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '时间轴', 'timeline', 'biz_zone_layout', '', 'warning', 'N', '0', 'admin', sysdate()),
-- 展项版式
(1, '图文', '0', 'biz_item_type', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '视频', '1', 'biz_item_type', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '音频', '2', 'biz_item_type', '', 'warning', 'N', '0', 'admin', sysdate()),
(4, '混合', '3', 'biz_item_type', '', 'info',    'N', '0', 'admin', sysdate()),
-- 素材类型
(1, '图片', 'image', 'biz_media_type', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '视频', 'video', 'biz_media_type', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '音频', 'audio', 'biz_media_type', '', 'warning', 'N', '0', 'admin', sysdate()),
(4, '外链', 'link',  'biz_media_type', '', 'info',    'N', '0', 'admin', sysdate()),
-- 可见范围
(1, '仅本人', '0', 'biz_visible_scope', '', 'info',    'Y', '0', 'admin', sysdate()),
(2, '本课程', '1', 'biz_visible_scope', '', 'primary', 'N', '0', 'admin', sysdate()),
(3, '本班',   '2', 'biz_visible_scope', '', 'success', 'N', '0', 'admin', sysdate()),
(4, '全校',   '3', 'biz_visible_scope', '', 'warning', 'N', '0', 'admin', sysdate()),
-- 用户类别
(1, '学生',     'student', 'biz_user_category', '', 'primary', 'N', '0', 'admin', sysdate()),
(2, '教师',     'teacher', 'biz_user_category', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '内容编辑', 'editor',  'biz_user_category', '', 'warning', 'N', '0', 'admin', sysdate()),
(4, '审核员',   'auditor', 'biz_user_category', '', 'info',    'N', '0', 'admin', sysdate()),
(5, '管理员',   'admin',   'biz_user_category', '', 'danger',  'N', '0', 'admin', sysdate()),
-- 分享渠道
(1, '二维码', 'qrcode',   'biz_channel', '', 'primary', 'Y', '0', 'admin', sysdate()),
(2, '链接',   'link',     'biz_channel', '', 'success', 'N', '0', 'admin', sysdate()),
(3, '终端',   'terminal', 'biz_channel', '', 'warning', 'N', '0', 'admin', sysdate()),
(4, '门户',   'portal',   'biz_channel', '', 'info',    'N', '0', 'admin', sysdate()),
-- 下架原因
(1, '内容错误', '0', 'biz_offline_reason', '', 'warning', 'Y', '0', 'admin', sysdate()),
(2, '版权投诉', '1', 'biz_offline_reason', '', 'danger',  'N', '0', 'admin', sysdate()),
(3, '合规要求', '2', 'biz_offline_reason', '', 'danger',  'N', '0', 'admin', sysdate()),
(4, '其他',     '3', 'biz_offline_reason', '', 'info',    'N', '0', 'admin', sysdate());

-- ============================================================================
-- 2. 内置角色（MG-02-01：三类内置 + 内容编辑 + 审核员）
--    data_scope：1 全校 / 3 本部门 / 5 仅本人
-- ============================================================================
INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark) VALUES
(102, '教务管理员', 'registrar',   2, '1', 1, 1, '0', '0', 'admin', sysdate(), 'E1 组织与监管，不含系统运维删除类'),
(103, '教师',       'teacher',     3, '3', 1, 1, '0', '0', 'admin', sysdate(), 'E2 本班/本课程范围'),
(104, '学生',       'student',     4, '5', 1, 1, '0', '0', 'admin', sysdate(), 'E3 仅本人'),
(105, '内容编辑',   'editor',      5, '5', 1, 1, '0', '0', 'admin', sysdate(), 'E4 内容生产'),
(106, '审核员',     'auditor',     6, '1', 1, 1, '0', '0', 'admin', sysdate(), 'E4 仅审核，不可编辑；与推荐人互斥')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), remark = VALUES(remark);

-- 说明：超级管理员沿用 RuoYi 内置 role_id=1（role_key=admin），无需另建；
--       框架对 user_id=1 直接返回全量菜单，因此不给该角色配 sys_role_menu。
--       一人多角色由 sys_user_role 天然支持，权限并集由 RuoYi 的 selectMenuPermsByUserId
-- 天然实现；"推荐人与审核人不得同一人" 属业务校验，在推荐/审核服务中判断，不在此处配。

-- ============================================================================
-- 3. 等级制区间（校级统一口径，教师不可自定）
-- ============================================================================
DELETE FROM edu_grade_level;
INSERT INTO edu_grade_level (level_code, level_name, min_score, max_score, sort_no, is_default) VALUES
('A', '优秀',   90.00, 100.00, 1, '1'),
('B', '良好',   80.00,  89.99, 2, '1'),
('C', '中等',   70.00,  79.99, 3, '1'),
('D', '及格',   60.00,  69.99, 4, '1'),
('E', '不及格',  0.00,  59.99, 5, '1');

-- ============================================================================
-- 4. 通知模板（11.2 清单，closable 与需求表"是否可关闭"一致）
-- ============================================================================
INSERT INTO ops_notice_template (event_code, event_name, title_tpl, content_tpl, channels, closable, status) VALUES
('NT-01', '任务发布',         '新任务：{taskName}',            '{courseName} 发布了实训任务「{taskName}」，截止 {deadline}。', 'inner', '1', '0'),
('NT-02', '截止前24小时提醒', '任务即将截止：{taskName}',      '「{taskName}」将于 {deadline} 截止，请尽快提交。',              'inner', '1', '0'),
('NT-03', '提交成功回执',     '提交成功：{taskName}',          '你的提交已收到，版本 v{version}，提交时间 {submitTime}。',      'inner', '0', '0'),
('NT-04', '新提交待批',       '{count} 份新提交待批',          '「{taskName}」有 {count} 份提交待批阅。',                        'inner', '1', '0'),
('NT-05', '退回修改',         '作业被退回：{taskName}',        '退回原因：{reason}；请于 {resubmitDeadline} 前重新提交。',      'inner', '0', '0'),
('NT-06', '成绩发布',         '成绩已发布：{taskName}',        '你的「{taskName}」得分 {score}。',                              'inner', '0', '0'),
('NT-07', '成绩更正',         '成绩已更正：{taskName}',        '你的「{taskName}」成绩由 {oldScore} 更正为 {newScore}，原因：{reason}。', 'inner', '0', '0'),
('NT-08', '成果展示授权请求', '成果展示授权：{achievementName}', '教师推荐你的成果「{achievementName}」在 {zoneName} 展示，请确认署名方式与范围。', 'inner', '0', '0'),
('NT-09', '推荐进入待审',     '新成果待审：{achievementName}',  '来自 {teacherName} 的推荐，请按五项要素审核。',                  'inner', '1', '0'),
('NT-10', '审核结果回写',     '成果审核{result}：{achievementName}', '审核意见：{opinion}',                                     'inner', '0', '0'),
('NT-11', '定时发布失败',     '定时发布失败：{itemTitle}',     '失败原因：{failReason}，已重试 {retry} 次。',                    'inner', '0', '0'),
('NT-12', '设备离线超24小时', '设备离线：{deviceCode}',        '设备 {deviceCode}（{siteNo}）已离线 {hours} 小时。',             'inner', '1', '0'),
('NT-13', '待审积压提醒',     '成果审核积压',                  '待审区已积压 {count} 条，最长等待 {days} 个工作日。',            'inner', '1', '0')
ON DUPLICATE KEY UPDATE title_tpl = VALUES(title_tpl), content_tpl = VALUES(content_tpl);

-- ============================================================================
-- 5. 菜单骨架（E1 / E2 / E4 三个角色菜单组 + 门户）
--    路由前缀与需求文档 2.2 一致：/admin、/teacher、/exhibit，门户为首页
--    按钮级权限点命名规范：{模块}:{对象}:{动作}
--      list / query / add / edit / remove / export / import
--      业务动作：publish / offline / audit / recommend / authorize / review
--                recall / archive / unfreeze / revert / preview / score
-- ============================================================================
DELETE FROM sys_role_menu WHERE role_id IN (102,103,104,105,106);
DELETE FROM sys_menu WHERE menu_id BETWEEN 2000 AND 2199;

-- 门户（E8，随 A 工程发布）
INSERT INTO sys_menu VALUES (2000, '工作台', 0, 0, '/portal', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'dashboard', 'admin', sysdate(), '', NULL, 'E8 统一门户');
INSERT INTO sys_menu VALUES (2001, '门户首页', 2000, 1, 'index', 'portal/index', NULL, '', 1, 0, 'C', '0', '0', 'portal:index:view', 'home', 'admin', sysdate(), '', NULL, 'OP-01-01/02');
INSERT INTO sys_menu VALUES (2002, '我的待办', 2000, 2, 'todo', 'portal/todo', NULL, '', 1, 0, 'C', '0', '0', 'portal:todo:list', 'message', 'admin', sysdate(), '', NULL, 'OP-01-03');

-- E1 平台管理 /admin
INSERT INTO sys_menu VALUES (2010, '平台管理', 0, 1, '/admin', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'system', 'admin', sysdate(), '', NULL, 'E1 入口');
INSERT INTO sys_menu VALUES (2011, '组织管理', 2010, 1, 'org', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'tree', 'admin', sysdate(), '', NULL, NULL);
INSERT INTO sys_menu VALUES (2012, '学年学期', 2011, 1, 'term', 'edu/term/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:term:list', 'date', 'admin', sysdate(), '', NULL, 'MG-01-01');
INSERT INTO sys_menu VALUES (2027, '专业管理', 2011, 2, 'major', 'edu/major/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:major:list', 'tree-table', 'admin', sysdate(), '', NULL, 'MG-01-02 支撑数据');
INSERT INTO sys_menu VALUES (2013, '班级管理', 2011, 3, 'class', 'edu/class/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:class:list', 'peoples', 'admin', sysdate(), '', NULL, 'MG-01-02');
INSERT INTO sys_menu VALUES (2014, '分组管理', 2011, 4, 'group', 'edu/group/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:group:list', 'component', 'admin', sysdate(), '', NULL, 'MG-01-03');
INSERT INTO sys_menu VALUES (2015, '学生账号', 2011, 5, 'student', 'edu/student/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:student:list', 'user', 'admin', sysdate(), '', NULL, 'MG-01-04/05/06');
INSERT INTO sys_menu VALUES (2016, '权限管理', 2010, 2, 'auth', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'lock', 'admin', sysdate(), '', NULL, NULL);
INSERT INTO sys_menu VALUES (2017, '账号授权', 2016, 1, 'grant', 'edu/grant/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:grant:list', 'people', 'admin', sysdate(), '', NULL, 'MG-02-02');
INSERT INTO sys_menu VALUES (2018, '接口授权', 2016, 2, 'app', 'ops/app/index', NULL, '', 1, 0, 'C', '0', '0', 'ops:app:list', 'link', 'admin', sysdate(), '', NULL, 'MG-02-04 / OP-03');
INSERT INTO sys_menu VALUES (2019, '教学监管', 2010, 3, 'monitor', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', sysdate(), '', NULL, NULL);
INSERT INTO sys_menu VALUES (2020, '课程任务总览', 2019, 1, 'course', 'edu/monitor/course', NULL, '', 1, 0, 'C', '0', '0', 'edu:monitor:course', 'chart', 'admin', sysdate(), '', NULL, 'MG-03-01');
INSERT INTO sys_menu VALUES (2021, '批阅进度', 2019, 2, 'review', 'edu/monitor/review', NULL, '', 1, 0, 'C', '0', '0', 'edu:monitor:review', 'edit', 'admin', sysdate(), '', NULL, 'MG-03-02');
INSERT INTO sys_menu VALUES (2022, '数据导出', 2010, 4, 'export', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'download', 'admin', sysdate(), '', NULL, NULL);
INSERT INTO sys_menu VALUES (2023, '成绩汇总', 2022, 1, 'score', 'edu/score/summary', NULL, '', 1, 0, 'C', '0', '0', 'edu:score:summary', 'excel', 'admin', sysdate(), '', NULL, 'MG-04-01');
INSERT INTO sys_menu VALUES (2024, '展厅监管', 2010, 5, 'exh', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'eye-open', 'admin', sysdate(), '', NULL, NULL);
INSERT INTO sys_menu VALUES (2025, '展区开放配置', 2024, 1, 'zone-level', 'exh/zone/level', NULL, '', 1, 0, 'C', '0', '0', 'exh:zone:level', 'tree-table', 'admin', sysdate(), '', NULL, 'MG-05-01');
INSERT INTO sys_menu VALUES (2026, '紧急下架审批', 2024, 2, 'offline', 'exh/offline/audit', NULL, '', 1, 0, 'C', '0', '0', 'exh:offline:audit', 'checkbox', 'admin', sysdate(), '', NULL, 'MG-05-02');

-- 按钮级权限点示例：学年学期（其余模块按同一命名规范 list/query/add/edit/remove/export/switch/archive 补齐）
INSERT INTO sys_menu VALUES (2060, '学期查询', 2012, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:term:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2061, '学期新增', 2012, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:term:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2062, '学期修改', 2012, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:term:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2063, '学期删除', 2012, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:term:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2064, '学期导出', 2012, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:term:export', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2065, '切换学期', 2012, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:term:switch', '#', 'admin', sysdate(), '', NULL, 'MG-01-01');
INSERT INTO sys_menu VALUES (2066, '学期归档', 2012, 7, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:term:archive','#', 'admin', sysdate(), '', NULL, 'P10');
-- 专业
INSERT INTO sys_menu VALUES (2067, '专业查询', 2027, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2068, '专业新增', 2027, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2069, '专业修改', 2027, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2070, '专业删除', 2027, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2071, '专业导出', 2027, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:major:export', '#', 'admin', sysdate(), '', NULL, '');
-- 班级
INSERT INTO sys_menu VALUES (2072, '班级查询', 2013, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2073, '班级新增', 2013, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2074, '班级修改', 2013, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2075, '班级删除', 2013, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2076, '班级导出', 2013, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:class:export', '#', 'admin', sysdate(), '', NULL, '');
-- 分组
INSERT INTO sys_menu VALUES (2077, '分组查询', 2014, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2078, '分组新增', 2014, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2079, '分组修改', 2014, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2080, '分组删除', 2014, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:group:remove', '#', 'admin', sysdate(), '', NULL, '');
-- 学生账号
INSERT INTO sys_menu VALUES (2081, '学生查询', 2015, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:query',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2082, '学生新增', 2015, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:add',      '#', 'admin', sysdate(), '', NULL, 'MG-01-05');
INSERT INTO sys_menu VALUES (2083, '学生修改', 2015, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:edit',     '#', 'admin', sysdate(), '', NULL, 'MG-01-05 启停');
INSERT INTO sys_menu VALUES (2084, '学生导出', 2015, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:export',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2085, '批量导入', 2015, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:import',   '#', 'admin', sysdate(), '', NULL, 'MG-01-04');
INSERT INTO sys_menu VALUES (2086, '重置密码', 2015, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:resetPwd', '#', 'admin', sysdate(), '', NULL, 'MG-01-05');
INSERT INTO sys_menu VALUES (2087, '转班',     2015, 7, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:student:transfer', '#', 'admin', sysdate(), '', NULL, 'MG-01-06');
-- 账号授权
INSERT INTO sys_menu VALUES (2088, '查询授权', 2017, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:grant:query', '#', 'admin', sysdate(), '', NULL, 'MG-02-02');
INSERT INTO sys_menu VALUES (2089, '修改授权', 2017, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:grant:edit',  '#', 'admin', sysdate(), '', NULL, 'MG-02-02');
-- 应用接入（2018 接口授权）
INSERT INTO sys_menu VALUES (2090, '应用查询', 2018, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'ops:app:query',       '#', 'admin', sysdate(), '', NULL, 'OP-03-01');
INSERT INTO sys_menu VALUES (2091, '应用注册', 2018, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'ops:app:add',         '#', 'admin', sysdate(), '', NULL, 'OP-03-01');
INSERT INTO sys_menu VALUES (2092, '应用修改', 2018, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'ops:app:edit',        '#', 'admin', sysdate(), '', NULL, 'OP-03-02');
INSERT INTO sys_menu VALUES (2093, '应用删除', 2018, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'ops:app:remove',      '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2094, '重置密钥', 2018, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'ops:app:resetSecret', '#', 'admin', sysdate(), '', NULL, 'OP-03-01');
INSERT INTO sys_menu VALUES (2095, '调用日志', 2018, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'ops:app:log',         '#', 'admin', sysdate(), '', NULL, 'OP-03-04');

-- E2 教师工作室按钮级权限（TC-01~05 / ST-02）
INSERT INTO sys_menu VALUES (2100, '课程查询', 2031, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:course:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2101, '课程新增', 2031, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:course:add',    '#', 'admin', sysdate(), '', NULL, 'TC-01-01');
INSERT INTO sys_menu VALUES (2102, '课程修改', 2031, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:course:edit',   '#', 'admin', sysdate(), '', NULL, 'TC-01-01');
INSERT INTO sys_menu VALUES (2103, '课程删除', 2031, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:course:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2104, '资源查询', 2032, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2105, '资源新增', 2032, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:add',    '#', 'admin', sysdate(), '', NULL, 'TC-02-01');
INSERT INTO sys_menu VALUES (2106, '资源修改', 2032, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:edit',   '#', 'admin', sysdate(), '', NULL, 'TC-02-03/04');
INSERT INTO sys_menu VALUES (2107, '资源删除', 2032, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:resource:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2108, '任务查询', 2033, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:task:query',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2109, '任务新增', 2033, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:task:add',     '#', 'admin', sysdate(), '', NULL, 'TC-03-01');
INSERT INTO sys_menu VALUES (2110, '任务修改', 2033, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:task:edit',    '#', 'admin', sysdate(), '', NULL, 'TC-03-01');
INSERT INTO sys_menu VALUES (2111, '任务发布', 2033, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:task:publish', '#', 'admin', sysdate(), '', NULL, 'TC-03-02');
INSERT INTO sys_menu VALUES (2112, '任务撤回', 2033, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:task:recall',  '#', 'admin', sysdate(), '', NULL, 'TC-03-04/05');
INSERT INTO sys_menu VALUES (2113, '任务删除', 2033, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:task:remove',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2114, '批阅查询', 2034, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:review:query',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2115, '提交批阅', 2034, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:review:edit',    '#', 'admin', sysdate(), '', NULL, 'TC-04-01~03');
INSERT INTO sys_menu VALUES (2116, '成绩发布', 2034, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:review:publish', '#', 'admin', sysdate(), '', NULL, 'TC-04-05');
INSERT INTO sys_menu VALUES (2117, '退回重交', 2034, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:review:return',  '#', 'admin', sysdate(), '', NULL, 'TC-04-04');
INSERT INTO sys_menu VALUES (2118, '批量批阅', 2034, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:review:batch',   '#', 'admin', sysdate(), '', NULL, 'TC-04-06');
INSERT INTO sys_menu VALUES (2119, '成绩查询', 2035, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:score:query',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2120, '成绩发布', 2035, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:score:publish', '#', 'admin', sysdate(), '', NULL, 'TC-05-04');
INSERT INTO sys_menu VALUES (2121, '成绩更正', 2035, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:score:adjust',  '#', 'admin', sysdate(), '', NULL, 'TC-04-06');
INSERT INTO sys_menu VALUES (2122, '成绩导出', 2035, 4, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:score:export',  '#', 'admin', sysdate(), '', NULL, 'MG-04-01');
INSERT INTO sys_menu VALUES (2123, '成果推荐', 2035, 5, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:score:recommend','#', 'admin', sysdate(), '', NULL, 'TC-05-01');
-- 学生侧提交权限（ST-02，挂在批阅菜单下便于教师查看/代改）
INSERT INTO sys_menu VALUES (2124, '我的提交', 2034, 6, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:submission:my',     '#', 'admin', sysdate(), '', NULL, 'ST-02-01');
INSERT INTO sys_menu VALUES (2125, '草稿保存', 2034, 7, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:submission:edit',   '#', 'admin', sysdate(), '', NULL, 'ST-02-02');
INSERT INTO sys_menu VALUES (2126, '提交作业', 2034, 8, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:submission:submit', '#', 'admin', sysdate(), '', NULL, 'ST-02-03');
INSERT INTO sys_menu VALUES (2127, '补交申请', 2034, 9, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:submission:apply',  '#', 'admin', sysdate(), '', NULL, 'ST-02-05');
INSERT INTO sys_menu VALUES (2128, '批阅录入', 2034, 10, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:review:add',        '#', 'admin', sysdate(), '', NULL, 'TC-04-03');
INSERT INTO sys_menu VALUES (2129, '补交审批', 2034, 11, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:review:audit',      '#', 'admin', sysdate(), '', NULL, 'ST-02-05');
-- 导出留痕按钮（MG-04）
INSERT INTO sys_menu VALUES (2130, '成果包导出', 2022, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:export:package', '#', 'admin', sysdate(), '', NULL, 'MG-04-02');
INSERT INTO sys_menu VALUES (2131, '台账导出',   2022, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:export:ledger',  '#', 'admin', sysdate(), '', NULL, 'MG-04-02');
INSERT INTO sys_menu VALUES (2132, '导出记录',   2022, 3, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'edu:export:query',   '#', 'admin', sysdate(), '', NULL, 'MG-04-01');

-- E2 教师工作室 /teacher
INSERT INTO sys_menu VALUES (2030, '教师工作室', 0, 2, '/teacher', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', NULL, 'E2 入口');
INSERT INTO sys_menu VALUES (2031, '课程管理', 2030, 1, 'course', 'edu/course/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:course:list', 'documentation', 'admin', sysdate(), '', NULL, 'TC-01');
INSERT INTO sys_menu VALUES (2032, '资源管理', 2030, 2, 'resource', 'edu/resource/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:resource:list', 'file', 'admin', sysdate(), '', NULL, 'TC-02');
INSERT INTO sys_menu VALUES (2033, '任务发布', 2030, 3, 'task', 'edu/task/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:task:list', 'form', 'admin', sysdate(), '', NULL, 'TC-03');
INSERT INTO sys_menu VALUES (2034, '批阅', 2030, 4, 'review', 'edu/review/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:review:list', 'edit', 'admin', sysdate(), '', NULL, 'TC-04');
INSERT INTO sys_menu VALUES (2035, '成绩与推荐', 2030, 5, 'score', 'edu/score/index', NULL, '', 1, 0, 'C', '0', '0', 'edu:score:list', 'star', 'admin', sysdate(), '', NULL, 'TC-05');

-- E4 展厅内容管理 /exhibit
INSERT INTO sys_menu VALUES (2040, '展厅内容', 0, 3, '/exhibit', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'build', 'admin', sysdate(), '', NULL, 'E4 入口');
INSERT INTO sys_menu VALUES (2041, '展区管理', 2040, 1, 'zone', 'exh/zone/index', NULL, '', 1, 0, 'C', '0', '0', 'exh:zone:list', 'tree', 'admin', sysdate(), '', NULL, 'EM-01');
INSERT INTO sys_menu VALUES (2042, '展项管理', 2040, 2, 'item', 'exh/item/index', NULL, '', 1, 0, 'C', '0', '0', 'exh:item:list', 'component', 'admin', sysdate(), '', NULL, 'EM-02');
INSERT INTO sys_menu VALUES (2043, '发布与版本', 2040, 3, 'publish', 'exh/publish/index', NULL, '', 1, 0, 'C', '0', '0', 'exh:publish:list', 'time', 'admin', sysdate(), '', NULL, 'EM-03');
INSERT INTO sys_menu VALUES (2044, '成果审核', 2040, 4, 'audit', 'exh/audit/index', NULL, '', 1, 0, 'C', '0', '0', 'exh:audit:list', 'checkbox', 'admin', sysdate(), '', NULL, 'EM-04');
INSERT INTO sys_menu VALUES (2045, '分享与二维码', 2040, 5, 'share', 'exh/share/index', NULL, '', 1, 0, 'C', '0', '0', 'exh:share:list', 'qrcode', 'admin', sysdate(), '', NULL, 'EM-05');
INSERT INTO sys_menu VALUES (2046, '终端设备', 2040, 6, 'device', 'exh/device/index', NULL, '', 1, 0, 'C', '0', '0', 'exh:device:list', 'phone', 'admin', sysdate(), '', NULL, 'TM-01');
INSERT INTO sys_menu VALUES (2133, '访问统计', 2045, 1, '', NULL, NULL, '', 1, 0, 'F', '0', '0', 'exh:stat:query', '#', 'admin', sysdate(), '', NULL, 'EM-05-02');

-- 角色-菜单：按视图准入矩阵（2.5）配置；超级管理员走框架全量菜单，不在此列
-- 102 教务管理员：E1 全部菜单 + 组织管理相关按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id BETWEEN 2010 AND 2027;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 102, menu_id FROM sys_menu WHERE menu_id BETWEEN 2060 AND 2132;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id IN (2000,2001,2002,2030,2031,2032,2033,2034,2035);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 103, menu_id FROM sys_menu WHERE menu_id BETWEEN 2100 AND 2129;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 104, menu_id FROM sys_menu WHERE menu_id IN (2000,2001,2002);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 104, menu_id FROM sys_menu WHERE menu_id IN (2119, 2124, 2125, 2126, 2127);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 105, menu_id FROM sys_menu WHERE menu_id IN (2000,2001,2002,2040,2041,2042,2043,2045,2046,2133);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 106, menu_id FROM sys_menu WHERE menu_id IN (2000,2001,2002,2040,2044);

-- ============================================================================
-- 6. 业务参数
-- ============================================================================
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark) VALUES
('当前学期ID',           'biz.current.termId',        '',      'N', 'admin', sysdate(), '由学期切换自动写入'),
('任务截止预警小时',     'biz.task.warnHours',        '24',    'Y', 'admin', sysdate(), 'NT-02'),
('退回原因最小字数',     'biz.review.returnMinLen',   '20',    'Y', 'admin', sysdate(), 'TC-04-04'),
('提交附件数量上限',     'biz.submit.maxFiles',       '10',    'Y', 'admin', sysdate(), 'ST-02-01'),
('单附件大小上限MB',     'biz.submit.maxFileMB',      '200',   'Y', 'admin', sysdate(), '第13章待确认'),
('文字说明字数上限',     'biz.submit.maxContentLen',  '2000',  'Y', 'admin', sysdate(), 'ST-02-01'),
('导入单批行数建议',     'biz.import.batchRows',      '5000',  'Y', 'admin', sysdate(), 'MG-01-04'),
('定时发布最小提前分钟', 'biz.publish.minLeadMin',    '5',     'Y', 'admin', sysdate(), 'EM-03-01'),
('发布失败重试次数',     'biz.publish.retryTimes',    '3',     'Y', 'admin', sysdate(), 'EM-03-03'),
('设备离线告警小时',     'biz.device.offlineHours',   '24',    'Y', 'admin', sysdate(), 'NT-12'),
('待审积压告警工作日',   'biz.audit.backlogDays',     '5',     'Y', 'admin', sysdate(), 'NT-13'),
('紧急下架升级小时',     'biz.offline.escalateHours','2',     'Y', 'admin', sysdate(), 'MG-05-02'),
('开放接口默认配额/分',  'biz.openapi.quotaPerMin',   '600',   'Y', 'admin', sysdate(), 'OP-03-03'),
('跨班分组开关',         'biz.group.crossClass',      '0',     'Y', 'admin', sysdate(), 'MG-01-03：0仅本班 1允许跨班'),
('展厅短链前缀',         'biz.exhibit.shareBase',    'https://exh.legaltraining.cn/s/', 'N', 'admin', sysdate(), 'EM-05-01 分享短链域名前缀'),
('展厅内容版本号',       'biz.exhibit.contentVersion','',      'N', 'admin', sysdate(), 'OP-02-03 留空则按最后更新时间推导'),
('外部案例库地址',       'biz.openapi.caseUrl',       '',      'N', 'admin', sysdate(), 'OP-02-08 留空即降级为空结果'),
('AI 咨询地址',          'biz.ai.consultUrl',         '',      'N', 'admin', sysdate(), 'OP-02-09 留空即降级提示未配置')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);
