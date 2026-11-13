-- ----------------------------------------------------------------------------
-- 法律实训数字平台 · 业务库结构（V3.0 需求对齐版）
-- 引擎：MySQL 8.0 / InnoDB / utf8mb4
-- 说明：
--   1. 身份、角色、菜单、日志、参数、字典直接复用 RuoYi 的 sys_* 表，本脚本只
--      在建视图 / 业务表层面做补充，不修改框架表结构。
--   2. 表前缀即领域边界：edu_ = 教学业务，exh_ = 展厅内容，ops_ = 接入与运营。
--      本脚本为【单库版】，供开发机一次建好；生产按 header 说明拆两个库：
--        legal_training（sys_* / edu_* / ops_*）  legal_exhibit（exh_*）
--      脚本无物理外键、无跨域 join，拆库不需要改字段。
--   3. 状态字段一律 tinyint + 注释，取值与《分工程功能需求规格说明书》第 6 章
--      状态机严格一一对应，字典编码见 sql/biz_seed.sql。
--   4. 时间统一 datetime，业务判定按"到秒"，时区 Asia/Shanghai。
-- ----------------------------------------------------------------------------
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、组织与账号（E1 平台管理 / MG-01）
-- ============================================================================

DROP TABLE IF EXISTS edu_term;
CREATE TABLE edu_term (
  term_id        bigint       NOT NULL AUTO_INCREMENT COMMENT '学期ID',
  school_year    varchar(16)  NOT NULL COMMENT '学年，如 2026-2027',
  term_no        tinyint      NOT NULL COMMENT '学期序号 1=第一学期 2=第二学期 3=短学期',
  term_name      varchar(64)  NOT NULL COMMENT '学期显示名，如 2026-2027学年第一学期',
  start_date     date         NOT NULL COMMENT '开始日期',
  end_date       date         NOT NULL COMMENT '结束日期',
  is_current     char(1)      NOT NULL DEFAULT '0' COMMENT '是否当前学期（0否 1是，全局唯一）',
  status         tinyint      NOT NULL DEFAULT 0 COMMENT '状态 0筹备 1进行中 2已归档',
  archive_time   datetime              DEFAULT NULL COMMENT '归档时间',
  archive_by     varchar(64)           DEFAULT NULL COMMENT '归档人',
  archive_report longtext              DEFAULT NULL COMMENT '归档前置检查报告 JSON',
  del_flag       char(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  create_by      varchar(64)           DEFAULT NULL,
  create_time    datetime              DEFAULT NULL,
  update_by      varchar(64)           DEFAULT NULL,
  update_time    datetime              DEFAULT NULL,
  remark         varchar(500)          DEFAULT NULL,
  PRIMARY KEY (term_id),
  UNIQUE KEY uk_term (school_year, term_no),
  KEY idx_term_current (is_current, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学年学期（MG-01-01）';

DROP TABLE IF EXISTS edu_major;
CREATE TABLE edu_major (
  major_id     bigint      NOT NULL AUTO_INCREMENT COMMENT '专业ID',
  dept_id      bigint      NOT NULL COMMENT '所属院系（sys_dept.dept_id）',
  major_code   varchar(32) NOT NULL COMMENT '专业编码',
  major_name   varchar(64) NOT NULL COMMENT '专业名称',
  degree_type  varchar(16)          DEFAULT NULL COMMENT '培养层次 本科/专科/研究生',
  status       char(1)     NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  create_by    varchar(64)          DEFAULT NULL,
  create_time  datetime             DEFAULT NULL,
  update_by    varchar(64)          DEFAULT NULL,
  update_time  datetime             DEFAULT NULL,
  PRIMARY KEY (major_id),
  UNIQUE KEY uk_major_code (major_code),
  KEY idx_major_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业（MG-01-02 字典支撑）';

DROP TABLE IF EXISTS edu_class;
CREATE TABLE edu_class (
  class_id      bigint      NOT NULL AUTO_INCREMENT COMMENT '班级ID',
  term_id       bigint      NOT NULL COMMENT '所属学期（edu_term.term_id）',
  class_code    varchar(32) NOT NULL COMMENT '班级编码，导入时按编码定位',
  class_name    varchar(64) NOT NULL COMMENT '班级名称',
  dept_id       bigint      NOT NULL COMMENT '院系ID',
  major_id      bigint               DEFAULT NULL COMMENT '专业ID',
  grade_year    smallint             DEFAULT NULL COMMENT '年级（入学年，如 2024）',
  head_teacher  bigint               DEFAULT NULL COMMENT '任课/班主任（sys_user.user_id）',
  student_count int         NOT NULL DEFAULT 0 COMMENT '在册人数（冗余，成员变更时重算）',
  status        tinyint     NOT NULL DEFAULT 0 COMMENT '状态 0启用 1归档',
  del_flag      char(1)     NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  create_by     varchar(64)          DEFAULT NULL,
  create_time   datetime             DEFAULT NULL,
  update_by     varchar(64)          DEFAULT NULL,
  update_time   datetime             DEFAULT NULL,
  remark        varchar(500)         DEFAULT NULL,
  PRIMARY KEY (class_id),
  UNIQUE KEY uk_class_term_code (term_id, class_code),
  KEY idx_class_teacher (head_teacher),
  KEY idx_class_dept (dept_id, major_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级（MG-01-02）';

DROP TABLE IF EXISTS edu_user_profile;
CREATE TABLE edu_user_profile (
  user_id      bigint      NOT NULL COMMENT 'sys_user.user_id',
  user_category varchar(16) NOT NULL COMMENT '用户类别 student/teacher/editor/auditor/admin',
  student_no   varchar(32)          DEFAULT NULL COMMENT '学号（学生必填，学年内唯一）',
  teacher_no   varchar(32)          DEFAULT NULL COMMENT '工号',
  id_type      varchar(16)          DEFAULT NULL COMMENT '证件类型',
  major_id     bigint               DEFAULT NULL COMMENT '专业ID',
  grade_year   smallint             DEFAULT NULL COMMENT '年级',
  source       varchar(16) NOT NULL DEFAULT 'manual' COMMENT '来源 manual/import/unified',
  create_time  datetime             DEFAULT NULL,
  update_time  datetime             DEFAULT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_student_no (student_no),
  UNIQUE KEY uk_teacher_no (teacher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号扩展信息（MG-01-05 单条新增/账号启停）';

DROP TABLE IF EXISTS edu_class_student;
CREATE TABLE edu_class_student (
  id          bigint   NOT NULL AUTO_INCREMENT,
  class_id    bigint   NOT NULL COMMENT '班级ID',
  user_id     bigint   NOT NULL COMMENT '学生 user_id',
  term_id     bigint   NOT NULL COMMENT '学期（冗余，便于按学期查归属）',
  join_time   datetime          DEFAULT NULL COMMENT '加入时间',
  leave_time  datetime          DEFAULT NULL COMMENT '离开时间（转班/退课）',
  is_current  char(1)  NOT NULL DEFAULT '1' COMMENT '是否当前归属（0否 1是）',
  create_by   varchar(64)       DEFAULT NULL,
  create_time datetime          DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_cs_class (class_id, is_current),
  KEY idx_cs_user (user_id, is_current),
  KEY idx_cs_term (term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级成员（MG-01-06 转班保留发生时归属）';

DROP TABLE IF EXISTS edu_group;
CREATE TABLE edu_group (
  group_id    bigint      NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  class_id    bigint      NOT NULL COMMENT '所属班级',
  task_id     bigint               DEFAULT NULL COMMENT '所属任务（自由组队时按任务建组；班级固定分组时为空）',
  group_name  varchar(64) NOT NULL COMMENT '组名',
  leader_id   bigint               DEFAULT NULL COMMENT '组长 user_id',
  member_min  int         NOT NULL DEFAULT 1 COMMENT '人数下限',
  member_max  int         NOT NULL DEFAULT 10 COMMENT '人数上限',
  group_type  tinyint     NOT NULL DEFAULT 0 COMMENT '0教师指定 1自由组队',
  status      tinyint     NOT NULL DEFAULT 0 COMMENT '0组建中 1已确认 2已解散',
  create_by   varchar(64)          DEFAULT NULL,
  create_time datetime             DEFAULT NULL,
  update_time datetime             DEFAULT NULL,
  PRIMARY KEY (group_id),
  KEY idx_group_class (class_id),
  KEY idx_group_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组（MG-01-03 / ST-02-04）';

DROP TABLE IF EXISTS edu_group_member;
CREATE TABLE edu_group_member (
  id          bigint  NOT NULL AUTO_INCREMENT,
  group_id    bigint  NOT NULL,
  user_id     bigint  NOT NULL COMMENT '成员 user_id',
  confirm_status tinyint NOT NULL DEFAULT 1 COMMENT '确认状态 0待确认 1已确认 2已拒绝',
  contribution  varchar(500) DEFAULT NULL COMMENT '成员贡献说明（ST-02-04）',
  join_time   datetime         DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gm (group_id, user_id),
  KEY idx_gm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组成员';

DROP TABLE IF EXISTS edu_transfer_log;
CREATE TABLE edu_transfer_log (
  id          bigint      NOT NULL AUTO_INCREMENT,
  user_id     bigint      NOT NULL COMMENT '学生 user_id',
  transfer_type varchar(16) NOT NULL COMMENT 'class=转班 group=转组',
  from_id     bigint               DEFAULT NULL COMMENT '原班级/分组ID',
  to_id       bigint               DEFAULT NULL COMMENT '新班级/分组ID',
  from_name   varchar(128)         DEFAULT NULL,
  to_name     varchar(128)         DEFAULT NULL,
  term_id     bigint               DEFAULT NULL,
  operator_id bigint               DEFAULT NULL COMMENT '操作人',
  operate_time datetime            DEFAULT NULL,
  remark      varchar(500)         DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_tf_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转班/转组记录（MG-01-06 留痕）';

DROP TABLE IF EXISTS edu_import_batch;
CREATE TABLE edu_import_batch (
  batch_id    bigint      NOT NULL AUTO_INCREMENT,
  biz_type    varchar(16) NOT NULL COMMENT '导入类型 student/teacher/score',
  term_id     bigint               DEFAULT NULL,
  class_id    bigint               DEFAULT NULL,
  file_name   varchar(255)         DEFAULT NULL,
  file_path   varchar(500)         DEFAULT NULL,
  total_rows  int         NOT NULL DEFAULT 0,
  success_rows int        NOT NULL DEFAULT 0,
  update_rows int         NOT NULL DEFAULT 0 COMMENT '已存在账号按“更新班级”处理的行数',
  fail_rows   int         NOT NULL DEFAULT 0,
  status      tinyint     NOT NULL DEFAULT 0 COMMENT '0预检中 1预检完成 2已确认导入 3已回滚',
  preview_json longtext            DEFAULT NULL COMMENT '预检报告 JSON（行号-原因）',
  create_by   varchar(64)          DEFAULT NULL,
  create_time datetime             DEFAULT NULL,
  finish_time datetime             DEFAULT NULL,
  PRIMARY KEY (batch_id),
  KEY idx_ib_type (biz_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入批次（MG-01-04 预检→确认导入）';

DROP TABLE IF EXISTS edu_import_row;
CREATE TABLE edu_import_row (
  id          bigint       NOT NULL AUTO_INCREMENT,
  batch_id    bigint       NOT NULL,
  row_no      int          NOT NULL COMMENT 'Excel 行号',
  raw_data    varchar(2000)         DEFAULT NULL COMMENT '原始行数据 JSON',
  result      tinyint      NOT NULL DEFAULT 0 COMMENT '0成功 1失败 2更新',
  fail_reason varchar(500)          DEFAULT NULL COMMENT '失败原因',
  PRIMARY KEY (id),
  KEY idx_ir_batch (batch_id, result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入逐行结果（失败清单可下载）';

-- ============================================================================
-- 二、课程与资源（E2 教师工作室 / TC-01、TC-02）
-- ============================================================================

DROP TABLE IF EXISTS edu_course;
CREATE TABLE edu_course (
  course_id    bigint       NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  course_code  varchar(32)  NOT NULL COMMENT '课程编码（学期内唯一）',
  course_name  varchar(128) NOT NULL COMMENT '课程名称',
  term_id      bigint       NOT NULL COMMENT '学期ID',
  teacher_id   bigint       NOT NULL COMMENT '授课教师 user_id',
  class_id     bigint                DEFAULT NULL COMMENT '归属班级（主班，多班见 edu_course_class）',
  cover        varchar(500)          DEFAULT NULL COMMENT '封面',
  intro        varchar(2000)         DEFAULT NULL COMMENT '课程简介',
  status       tinyint      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已归档',
  copy_from    bigint                DEFAULT NULL COMMENT '复制来源课程ID（TC-01-02）',
  publish_time datetime              DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  create_by    varchar(64)           DEFAULT NULL,
  create_time  datetime              DEFAULT NULL,
  update_by    varchar(64)           DEFAULT NULL,
  update_time  datetime              DEFAULT NULL,
  PRIMARY KEY (course_id),
  UNIQUE KEY uk_course (term_id, course_code),
  KEY idx_course_teacher (teacher_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程（TC-01-01 / TC-01-02）';

DROP TABLE IF EXISTS edu_course_class;
CREATE TABLE edu_course_class (
  id        bigint NOT NULL AUTO_INCREMENT,
  course_id bigint NOT NULL,
  class_id  bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cc (course_id, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-班级（一门课可带多个班）';

DROP TABLE IF EXISTS edu_course_exhibit;
CREATE TABLE edu_course_exhibit (
  id          bigint  NOT NULL AUTO_INCREMENT,
  course_id   bigint  NOT NULL,
  target_type varchar(16) NOT NULL COMMENT 'zone=展区 item=展项',
  target_id   bigint  NOT NULL,
  sort_no     int     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_ce_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程关联展区/展项（TC-01-03 延伸学习入口）';

DROP TABLE IF EXISTS edu_resource_category;
CREATE TABLE edu_resource_category (
  category_id bigint      NOT NULL AUTO_INCREMENT,
  parent_id   bigint      NOT NULL DEFAULT 0,
  category_name varchar(64) NOT NULL,
  order_num   int         NOT NULL DEFAULT 0,
  owner_id    bigint               DEFAULT NULL COMMENT '归属教师，空=全校公共',
  status      char(1)     NOT NULL DEFAULT '0',
  PRIMARY KEY (category_id),
  KEY idx_rc_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源分类（TC-02-02）';

DROP TABLE IF EXISTS edu_resource;
CREATE TABLE edu_resource (
  resource_id  bigint       NOT NULL AUTO_INCREMENT COMMENT '资源ID',
  title        varchar(200) NOT NULL COMMENT '资源标题',
  category_id  bigint                DEFAULT NULL,
  res_type     varchar(16)  NOT NULL COMMENT 'doc/video/audio/image/link',
  file_id      varchar(64)           DEFAULT NULL COMMENT '文件存储标识（ops 文件服务）',
  file_name    varchar(255)          DEFAULT NULL,
  file_size    bigint                DEFAULT NULL,
  file_ext     varchar(16)           DEFAULT NULL,
  external_url varchar(500)          DEFAULT NULL COMMENT '外链资源地址',
  duration     int                   DEFAULT NULL COMMENT '音视频时长（秒）',
  cover        varchar(500)           DEFAULT NULL,
  visible_scope tinyint     NOT NULL DEFAULT 1 COMMENT '可见范围 0仅本人 1本课程 2本班 3全校',
  allow_download char(1)    NOT NULL DEFAULT '1' COMMENT '是否允许下载（TC-02-04）',
  ref_count    int          NOT NULL DEFAULT 0 COMMENT '被任务/展项引用次数，>0 不可删除',
  status       tinyint      NOT NULL DEFAULT 0 COMMENT '0已上架 1已下架',
  owner_id     bigint       NOT NULL COMMENT '上传教师',
  del_flag     char(1)      NOT NULL DEFAULT '0',
  create_time  datetime              DEFAULT NULL,
  update_time  datetime              DEFAULT NULL,
  PRIMARY KEY (resource_id),
  KEY idx_res_owner (owner_id, status),
  KEY idx_res_cate (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学资源（TC-02-01 / TC-02-03 / TC-02-04）';

DROP TABLE IF EXISTS edu_course_resource;
CREATE TABLE edu_course_resource (
  id          bigint NOT NULL AUTO_INCREMENT,
  course_id   bigint NOT NULL,
  resource_id bigint NOT NULL,
  sort_no     int    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cr (course_id, resource_id),
  KEY idx_cr_res (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-资源';

-- ============================================================================
-- 三、任务、提交、批阅、成绩（E2 / E3，状态机见文档第 6 章）
-- ============================================================================

DROP TABLE IF EXISTS edu_task;
CREATE TABLE edu_task (
  task_id        bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  course_id      bigint       NOT NULL,
  title          varchar(200) NOT NULL COMMENT '任务标题',
  requirement    longtext              DEFAULT NULL COMMENT '富文本要求（TC-03-01）',
  template_file  varchar(500)          DEFAULT NULL COMMENT '附件模板',
  start_time     datetime     NOT NULL COMMENT '开始时间',
  deadline       datetime     NOT NULL COMMENT '截止时间',
  deadline_policy tinyint     NOT NULL DEFAULT 0 COMMENT '截止策略 0严格 1宽限 2审批补交 3不限',
  grace_days     int          NOT NULL DEFAULT 3 COMMENT '宽限天数（策略=1 时生效）',
  finish_mode    tinyint      NOT NULL DEFAULT 0 COMMENT '完成方式 0个人 1教师分组 2自由组队',
  group_min      int                   DEFAULT NULL,
  group_max      int                   DEFAULT NULL,
  score_mode     tinyint      NOT NULL DEFAULT 0 COMMENT '评价方式 0百分制 1等级制 2多维度加权',
  total_score    decimal(6,2) NOT NULL DEFAULT 100.00 COMMENT '满分',
  weight_check   char(1)      NOT NULL DEFAULT '1' COMMENT '评分项权重是否合计100（1是 0否）',
  status         tinyint      NOT NULL DEFAULT 0 COMMENT '见状态机 0草稿 1进行中 2已截止 3已取消 4已归档',
  publish_time   datetime              DEFAULT NULL COMMENT '发布时间',
  cancel_reason  varchar(500)          DEFAULT NULL COMMENT '撤回原因',
  archive_flag   char(1)      NOT NULL DEFAULT '0' COMMENT '是否归档时未批（1是）',
  del_flag       char(1)      NOT NULL DEFAULT '0',
  create_by      varchar(64)           DEFAULT NULL,
  create_time    datetime              DEFAULT NULL,
  update_by      varchar(64)           DEFAULT NULL,
  update_time    datetime              DEFAULT NULL,
  PRIMARY KEY (task_id),
  KEY idx_task_course (course_id, status),
  KEY idx_task_deadline (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实训任务（TC-03-01~06，状态机 6.1）';

DROP TABLE IF EXISTS edu_task_score_item;
CREATE TABLE edu_task_score_item (
  item_id    bigint       NOT NULL AUTO_INCREMENT,
  task_id    bigint       NOT NULL,
  item_name  varchar(100) NOT NULL COMMENT '评分项名称',
  weight     decimal(6,2) NOT NULL COMMENT '权重（%）',
  max_score  decimal(6,2) NOT NULL COMMENT '该项满分',
  sort_no    int          NOT NULL DEFAULT 0,
  remark     varchar(500)          DEFAULT NULL,
  PRIMARY KEY (item_id),
  KEY idx_tsi_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分项（权重合计须=100）';

DROP TABLE IF EXISTS edu_grade_level;
CREATE TABLE edu_grade_level (
  level_id   bigint      NOT NULL AUTO_INCREMENT,
  level_code varchar(16) NOT NULL COMMENT '等级编码 A/B/C/D 或 优/良/中/及格/不及格',
  level_name varchar(32) NOT NULL,
  min_score  decimal(6,2) NOT NULL,
  max_score  decimal(6,2) NOT NULL,
  sort_no    int         NOT NULL DEFAULT 0,
  is_default char(1)     NOT NULL DEFAULT '0' COMMENT '是否为校默认口径',
  PRIMARY KEY (level_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级制分数区间（校级统一，教师不可自定）';

DROP TABLE IF EXISTS edu_task_resource;
CREATE TABLE edu_task_resource (
  id          bigint NOT NULL AUTO_INCREMENT,
  task_id     bigint NOT NULL,
  resource_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tr (task_id, resource_id),
  KEY idx_tr_res (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务关联资源';

DROP TABLE IF EXISTS edu_task_exhibit;
CREATE TABLE edu_task_exhibit (
  id          bigint NOT NULL AUTO_INCREMENT,
  task_id     bigint NOT NULL,
  target_type varchar(16) NOT NULL COMMENT 'zone/item',
  target_id   bigint NOT NULL,
  PRIMARY KEY (id),
  KEY idx_te_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务关联展项（ST-04-01 一键跳转）';

DROP TABLE IF EXISTS edu_task_participant;
CREATE TABLE edu_task_participant (
  id          bigint NOT NULL AUTO_INCREMENT,
  task_id     bigint NOT NULL,
  user_id     bigint NOT NULL COMMENT '应参与学生',
  group_id    bigint         DEFAULT NULL COMMENT '分组（分组任务时）',
  submit_status tinyint NOT NULL DEFAULT 0 COMMENT '提交状态镜像，见 6.2',
  overdue_flag char(1) NOT NULL DEFAULT '0' COMMENT '是否逾期',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tp (task_id, user_id),
  KEY idx_tp_user (user_id),
  KEY idx_tp_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务参与人（发布时按班级快照生成待办）';

DROP TABLE IF EXISTS edu_submission;
CREATE TABLE edu_submission (
  submission_id bigint      NOT NULL AUTO_INCREMENT COMMENT '提交ID',
  task_id       bigint      NOT NULL,
  subject_type  tinyint     NOT NULL DEFAULT 0 COMMENT '主体 0个人 1分组',
  user_id       bigint               DEFAULT NULL COMMENT '个人提交时的学生',
  group_id      bigint               DEFAULT NULL COMMENT '分组提交时的组',
  content       text                 DEFAULT NULL COMMENT '文字说明（≤2000 字）',
  remark        varchar(500)         DEFAULT NULL,
  version       int         NOT NULL DEFAULT 1 COMMENT '当前版本号',
  status        tinyint     NOT NULL DEFAULT 0 COMMENT '6.2 状态机 0未提交 1草稿 2已提交 3已批阅 4已退回 5逾期未交 6任务取消',
  submit_time   datetime             DEFAULT NULL COMMENT '最近提交时间',
  draft_time    datetime             DEFAULT NULL COMMENT '草稿自动保存时间',
  is_overdue    char(1)     NOT NULL DEFAULT '0' COMMENT '是否逾期提交',
  resubmit_deadline datetime          DEFAULT NULL COMMENT '退回后的重交截止',
  withdraw_time datetime             DEFAULT NULL COMMENT '撤回时间',
  late_apply_status tinyint NOT NULL DEFAULT 0 COMMENT '补交申请 0未申请 1待审批 2已通过 3已驳回（ST-02-05）',
  late_reason   varchar(500)         DEFAULT NULL COMMENT '补交申请理由',
  late_approve_by bigint             DEFAULT NULL COMMENT '补交审批人',
  late_approve_time datetime         DEFAULT NULL COMMENT '补交审批时间',
  create_time   datetime             DEFAULT NULL,
  update_time   datetime             DEFAULT NULL,
  PRIMARY KEY (submission_id),
  UNIQUE KEY uk_sub_task_user (task_id, user_id),
  KEY idx_sub_group (task_id, group_id),
  KEY idx_sub_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业提交（ST-02-01~05，状态机 6.2）';

DROP TABLE IF EXISTS edu_submission_version;
CREATE TABLE edu_submission_version (
  id            bigint   NOT NULL AUTO_INCREMENT,
  submission_id bigint   NOT NULL,
  version       int      NOT NULL,
  content       text              DEFAULT NULL,
  file_snapshot text              DEFAULT NULL COMMENT '附件快照 JSON',
  submit_time   datetime          DEFAULT NULL,
  operator_id   bigint            DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sv (submission_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交历史版本（撤回/退回/重交均留版）';

DROP TABLE IF EXISTS edu_submission_file;
CREATE TABLE edu_submission_file (
  id            bigint       NOT NULL AUTO_INCREMENT,
  submission_id bigint       NOT NULL,
  version       int          NOT NULL DEFAULT 1,
  file_id       varchar(64)  NOT NULL COMMENT '文件服务标识',
  file_name     varchar(255) NOT NULL,
  file_size     bigint                DEFAULT NULL,
  file_ext      varchar(16)           DEFAULT NULL,
  mime_type     varchar(100)          DEFAULT NULL,
  sort_no       int          NOT NULL DEFAULT 0,
  upload_time   datetime              DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_sf_sub (submission_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交附件（上限 10 个/次）';

DROP TABLE IF EXISTS edu_review;
CREATE TABLE edu_review (
  review_id     bigint      NOT NULL AUTO_INCREMENT,
  submission_id bigint      NOT NULL,
  submission_version int   NOT NULL COMMENT '批阅针对的提交版本',
  teacher_id    bigint      NOT NULL COMMENT '批阅教师',
  total_score   decimal(6,2)         DEFAULT NULL COMMENT '合计得分',
  review_comment varchar(2000)       DEFAULT NULL COMMENT '评语',
  status        tinyint     NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
  review_time   datetime             DEFAULT NULL,
  publish_time  datetime             DEFAULT NULL,
  return_reason varchar(500)         DEFAULT NULL COMMENT '退回原因（≥20 字）',
  return_deadline datetime           DEFAULT NULL COMMENT '重交截止',
  is_return     char(1)     NOT NULL DEFAULT '0' COMMENT '本次操作是否退回（TC-04-04）',
  create_time   datetime             DEFAULT NULL,
  update_time   datetime             DEFAULT NULL,
  PRIMARY KEY (review_id),
  KEY idx_rv_sub (submission_id),
  KEY idx_rv_teacher (teacher_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批阅（TC-04-01~06）';

DROP TABLE IF EXISTS edu_review_score;
CREATE TABLE edu_review_score (
  id         bigint       NOT NULL AUTO_INCREMENT,
  review_id  bigint       NOT NULL,
  item_id    bigint       NOT NULL COMMENT '评分项',
  score      decimal(6,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rs (review_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逐项得分';

DROP TABLE IF EXISTS edu_score;
CREATE TABLE edu_score (
  score_id     bigint      NOT NULL AUTO_INCREMENT,
  task_id      bigint      NOT NULL,
  course_id    bigint      NOT NULL,
  user_id      bigint      NOT NULL COMMENT '学生',
  group_id     bigint               DEFAULT NULL COMMENT '分组任务时的组（默认组得分=成员得分）',
  task_score   decimal(6,2)         DEFAULT NULL COMMENT '任务得分',
  group_adjust decimal(6,2) NOT NULL DEFAULT 0 COMMENT '分组加减分',
  final_score  decimal(6,2)         DEFAULT NULL COMMENT '计入课程总评的分',
  level_code   varchar(16)          DEFAULT NULL COMMENT '等级制时的等级',
  status       tinyint     NOT NULL DEFAULT 0 COMMENT '0待发布 1已发布 2已更正',
  publish_time datetime             DEFAULT NULL,
  create_time  datetime             DEFAULT NULL,
  update_time  datetime             DEFAULT NULL,
  PRIMARY KEY (score_id),
  UNIQUE KEY uk_score (task_id, user_id),
  KEY idx_score_course (course_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务成绩（TC-04-05 / TC-05-04）';

DROP TABLE IF EXISTS edu_score_adjust;
CREATE TABLE edu_score_adjust (
  id          bigint       NOT NULL AUTO_INCREMENT,
  score_id    bigint       NOT NULL,
  old_score   decimal(6,2)          DEFAULT NULL,
  new_score   decimal(6,2)          DEFAULT NULL,
  reason      varchar(500) NOT NULL COMMENT '更正原因（必填）',
  operator_id bigint       NOT NULL,
  operate_time datetime    NOT NULL,
  notify_flag char(1)      NOT NULL DEFAULT '1' COMMENT '是否已通知学生',
  PRIMARY KEY (id),
  KEY idx_sa_score (score_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩更正记录（TC-04-06 留痕+通知）';

DROP TABLE IF EXISTS edu_course_score;
CREATE TABLE edu_course_score (
  id           bigint       NOT NULL AUTO_INCREMENT,
  course_id    bigint       NOT NULL,
  user_id      bigint       NOT NULL,
  total_score  decimal(6,2)          DEFAULT NULL COMMENT '课程总评',
  level_code   varchar(16)           DEFAULT NULL,
  status       tinyint      NOT NULL DEFAULT 0 COMMENT '0待发布 1已发布',
  calc_time    datetime              DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cs (course_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程总评（TC-05-04）';

DROP TABLE IF EXISTS edu_export_log;
CREATE TABLE edu_export_log (
  id          bigint      NOT NULL AUTO_INCREMENT,
  export_type varchar(32) NOT NULL COMMENT 'score/result_package/process_ledger',
  filter_json text                 DEFAULT NULL COMMENT '筛选条件',
  field_list  text                 DEFAULT NULL COMMENT '字段范围',
  row_count   int         NOT NULL DEFAULT 0,
  file_path   varchar(500)         DEFAULT NULL,
  operator_id bigint               DEFAULT NULL,
  operate_time datetime             DEFAULT NULL,
  status      tinyint     NOT NULL DEFAULT 0 COMMENT '0生成中 1完成 2失败',
  PRIMARY KEY (id),
  KEY idx_el_type (export_type, operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出留痕（MG-04-01/02 可反查）';

-- ============================================================================
-- 四、成果推荐与审核（TC-05 / EM-04，状态机 6.3）
-- ============================================================================

DROP TABLE IF EXISTS edu_recommendation;
CREATE TABLE edu_recommendation (
  rec_id        bigint      NOT NULL AUTO_INCREMENT COMMENT '推荐记录ID（对外幂等键）',
  submission_id bigint      NOT NULL,
  teacher_id    bigint      NOT NULL COMMENT '推荐教师',
  reason        varchar(1000)        DEFAULT NULL COMMENT '推荐理由',
  target_zone   bigint               DEFAULT NULL COMMENT '目标展区',
  level_code    varchar(16)          DEFAULT NULL COMMENT '推荐等级',
  sign_mode     varchar(32)          DEFAULT NULL COMMENT '署名方式：实名/学号后四位/匿名',
  scope_desc    varchar(500)         DEFAULT NULL COMMENT '展示范围',
  status        tinyint     NOT NULL DEFAULT 0 COMMENT '6.3 0待授权 1待审核 2已通过 3已驳回 4已撤回 5已成展',
  exhibit_id    bigint               DEFAULT NULL COMMENT '成展后回写的展项ID',
  review_opinion varchar(1000)       DEFAULT NULL COMMENT '审核意见',
  auditor_id    bigint               DEFAULT NULL,
  audit_time    datetime             DEFAULT NULL,
  push_status   tinyint     NOT NULL DEFAULT 0 COMMENT '推送展厅侧 0未推送 1已推送 2未送达',
  push_retry    int         NOT NULL DEFAULT 0 COMMENT '推送重试次数（最多5次指数退避）',
  withdraw_by   bigint               DEFAULT NULL,
  withdraw_time datetime             DEFAULT NULL,
  create_time   datetime             DEFAULT NULL,
  update_time   datetime             DEFAULT NULL,
  PRIMARY KEY (rec_id),
  UNIQUE KEY uk_rec_sub (submission_id),
  KEY idx_rec_status (status, create_time),
  KEY idx_rec_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成果推荐（TC-05-01，状态机 6.3）';

DROP TABLE IF EXISTS edu_authorization;
CREATE TABLE edu_authorization (
  auth_id       bigint      NOT NULL AUTO_INCREMENT,
  rec_id        bigint      NOT NULL,
  user_id       bigint      NOT NULL COMMENT '学生（权利人）',
  auth_type     tinyint     NOT NULL DEFAULT 0 COMMENT '0线上确认 1线下授权书',
  sign_mode     varchar(32)          DEFAULT NULL COMMENT '署名方式',
  scope_desc    varchar(500)         DEFAULT NULL COMMENT '授权展示范围',
  file_id       varchar(64)          DEFAULT NULL COMMENT '授权书扫描件',
  confirm_time  datetime             DEFAULT NULL,
  ip_addr       varchar(64)          DEFAULT NULL COMMENT '线上确认留痕',
  status        tinyint     NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2已撤销',
  create_time   datetime             DEFAULT NULL,
  PRIMARY KEY (auth_id),
  KEY idx_auth_rec (rec_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='展示授权凭证（TC-05-02 / ST-04-02，强制卡点）';

DROP TABLE IF EXISTS exh_review;
CREATE TABLE exh_review (
  review_id   bigint      NOT NULL AUTO_INCREMENT,
  rec_id      bigint      NOT NULL COMMENT '来源推荐记录',
  auditor_id  bigint      NOT NULL COMMENT '审核员',
  check_items varchar(1000)        DEFAULT NULL COMMENT '五项要素核对结果 JSON（准确性/合规/版权/脱敏/可用性）',
  result      tinyint     NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  opinion     varchar(1000)        DEFAULT NULL COMMENT '审核意见',
  audit_time  datetime             DEFAULT NULL,
  self_check  char(1)     NOT NULL DEFAULT '0' COMMENT '推荐人与审核人是否为同一人（1是，禁止通过）',
  PRIMARY KEY (review_id),
  KEY idx_exr_rec (rec_id),
  KEY idx_exr_auditor (auditor_id, result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成果审核（EM-04-01/02）';

-- ============================================================================
-- 五、展厅内容（E4，状态机 6.4）
-- ============================================================================

DROP TABLE IF EXISTS exh_zone;
CREATE TABLE exh_zone (
  zone_id     bigint       NOT NULL AUTO_INCREMENT COMMENT '展区ID',
  zone_code   varchar(32)  NOT NULL COMMENT '展区编号',
  zone_name   varchar(100) NOT NULL COMMENT '展区名称',
  cover       varchar(500)          DEFAULT NULL COMMENT '列表卡片封面',
  intro       varchar(2000)         DEFAULT NULL COMMENT '简介',
  route_no    int          NOT NULL DEFAULT 0 COMMENT '参观动线序号（EM-01-02）',
  open_level  tinyint      NOT NULL DEFAULT 0 COMMENT '开放级别 0未开放 1内部开放 2对外开放（EF-06-01）',
  seo_index   char(1)      NOT NULL DEFAULT '0' COMMENT '是否允许搜索引擎收录',
  status      tinyint      NOT NULL DEFAULT 0 COMMENT '0关闭 1开放',
  layout      varchar(16)  NOT NULL DEFAULT 'grid' COMMENT '展区版式 grid图墙/flow长页/timeline时间轴（EF-01-02）',
  bg_image    varchar(500)          DEFAULT NULL COMMENT '展区头图（网页版展示，非全景）',
  theme_color varchar(16)           DEFAULT NULL COMMENT '展区主色，前端取色渲染',
  floor_no    int                   DEFAULT NULL COMMENT '楼层（上下展区切换用）',
  del_flag    char(1)      NOT NULL DEFAULT '0',
  create_by   varchar(64)           DEFAULT NULL,
  create_time datetime              DEFAULT NULL,
  update_time datetime              DEFAULT NULL,
  PRIMARY KEY (zone_id),
  UNIQUE KEY uk_zone_code (zone_code),
  KEY idx_zone_route (route_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='展区（EM-01-01~04）';

DROP TABLE IF EXISTS exh_zone_spot;
CREATE TABLE exh_zone_spot (
  spot_id     bigint      NOT NULL AUTO_INCREMENT,
  zone_id     bigint      NOT NULL,
  spot_name   varchar(100)         DEFAULT NULL,
  pos_x       decimal(10,4) NOT NULL COMMENT '相对展区头图的归一化坐标 0~1',
  pos_y       decimal(10,4) NOT NULL,
  icon        varchar(255)         DEFAULT NULL,
  target_type varchar(16) NOT NULL COMMENT 'zone=跳展区 item=开展项',
  target_id   bigint      NOT NULL,
  sort_no     int         NOT NULL DEFAULT 0,
  PRIMARY KEY (spot_id),
  KEY idx_spot_zone (zone_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景热点（EM-01-03 / EF-03-03）';

DROP TABLE IF EXISTS exh_item;
CREATE TABLE exh_item (
  item_id      bigint       NOT NULL AUTO_INCREMENT COMMENT '展项ID',
  item_code    varchar(32)           DEFAULT NULL,
  zone_id      bigint       NOT NULL COMMENT '所属展区',
  title        varchar(200) NOT NULL COMMENT '标题',
  item_type    tinyint      NOT NULL DEFAULT 0 COMMENT '版式 0图文 1视频 2音频 3混合（EF-02-01）',
  cover        varchar(500)          DEFAULT NULL,
  summary      varchar(255)          DEFAULT NULL COMMENT '摘要（20—120 字）',
  content      longtext              DEFAULT NULL COMMENT '结构化富文本正文',
  source_rec   bigint                DEFAULT NULL COMMENT '来源成果推荐ID（EM-04-03）',
  author_name  varchar(100)          DEFAULT NULL COMMENT '署名（脱敏后）',
  view_level   tinyint      NOT NULL DEFAULT 0 COMMENT '访问级别 0未发布 1内部开放 2对外开放',
  status       tinyint      NOT NULL DEFAULT 0 COMMENT '6.4 0草稿 1待发布 2已发布 3已下架 4发布失败',
  version      int          NOT NULL DEFAULT 0 COMMENT '当前版本号',
  publish_time datetime              DEFAULT NULL,
  offline_time datetime              DEFAULT NULL,
  offline_reason varchar(500)        DEFAULT NULL,
  editor_id    bigint                DEFAULT NULL COMMENT '最后编辑人',
  lock_user    bigint                DEFAULT NULL COMMENT '编辑锁持有人（EM-02-04）',
  lock_time    datetime              DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  create_by    varchar(64)           DEFAULT NULL,
  create_time  datetime              DEFAULT NULL,
  update_by    varchar(64)           DEFAULT NULL,
  update_time  datetime              DEFAULT NULL,
  PRIMARY KEY (item_id),
  KEY idx_item_zone (zone_id, status),
  KEY idx_item_status (status, publish_time),
  KEY idx_item_source (source_rec)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='展项（EM-02-01~05 / EM-03）';

DROP TABLE IF EXISTS exh_item_media;
CREATE TABLE exh_item_media (
  media_id   bigint      NOT NULL AUTO_INCREMENT,
  item_id    bigint      NOT NULL,
  media_type varchar(16) NOT NULL COMMENT 'image/video/audio/link',
  url        varchar(500) NOT NULL,
  cover      varchar(500)         DEFAULT NULL,
  duration   int                  DEFAULT NULL COMMENT '音视频时长（秒）',
  file_size  bigint               DEFAULT NULL,
  transcode_status tinyint NOT NULL DEFAULT 0 COMMENT '转码 0无需 1排队 2完成 3失败',
  sort_no    int         NOT NULL DEFAULT 0,
  status     char(1)     NOT NULL DEFAULT '0' COMMENT '0正常 1已删除',
  PRIMARY KEY (media_id),
  KEY idx_media_item (item_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='展项素材（EM-02-03，类型与规格见需求 6 条）';

DROP TABLE IF EXISTS exh_item_version;
CREATE TABLE exh_item_version (
  version_id bigint   NOT NULL AUTO_INCREMENT,
  item_id    bigint   NOT NULL,
  version    int      NOT NULL,
  snapshot   longtext NOT NULL COMMENT '快照 JSON：正文/素材引用与排序/展区/标题摘要/发布人与时间',
  edit_type  tinyint  NOT NULL DEFAULT 0 COMMENT '0普通保存 1发布 2回退 3下架',
  source_version int            DEFAULT NULL COMMENT '回退时的来源版本',
  operator_id bigint            DEFAULT NULL,
  operate_time datetime         DEFAULT NULL,
  PRIMARY KEY (version_id),
  UNIQUE KEY uk_iv (item_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='展项版本快照（EM-03-05 回退生成新版本）';

DROP TABLE IF EXISTS exh_publish_job;
CREATE TABLE exh_publish_job (
  job_id      bigint   NOT NULL AUTO_INCREMENT,
  item_id     bigint   NOT NULL,
  plan_time   datetime NOT NULL COMMENT '计划发布时间 UTC',
  schedule_id bigint            DEFAULT NULL COMMENT 'Quartz 任务ID',
  view_level  tinyint  NOT NULL DEFAULT 2,
  item_version int     NOT NULL DEFAULT 0,
  operator_id bigint            DEFAULT NULL,
  status      tinyint  NOT NULL DEFAULT 0 COMMENT '0待执行 1成功 2失败 3已取消',
  retry_count int      NOT NULL DEFAULT 0 COMMENT '失败重试次数（3 次后告警）',
  fail_reason varchar(500)      DEFAULT NULL,
  execute_time datetime         DEFAULT NULL,
  create_time datetime          DEFAULT NULL,
  PRIMARY KEY (job_id),
  UNIQUE KEY uk_pj_item (item_id),
  KEY idx_pj_status (status, plan_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时发布任务（EM-03-03）';

DROP TABLE IF EXISTS exh_offline_request;
CREATE TABLE exh_offline_request (
  req_id      bigint      NOT NULL AUTO_INCREMENT,
  target_type varchar(16) NOT NULL COMMENT 'item/zone',
  target_id   bigint      NOT NULL,
  apply_by    bigint      NOT NULL,
  reason_type tinyint     NOT NULL COMMENT '0内容错误 1版权投诉 2合规要求 3其他',
  reason      varchar(1000)        DEFAULT NULL,
  scope_desc  varchar(500)         DEFAULT NULL COMMENT '影响范围说明',
  status      tinyint     NOT NULL DEFAULT 0 COMMENT '0待审批 1已同意 2已驳回 3先下后批',
  auditor_id  bigint               DEFAULT NULL,
  audit_time  datetime             DEFAULT NULL,
  audit_opinion varchar(500)       DEFAULT NULL,
  apply_time  datetime             DEFAULT NULL,
  PRIMARY KEY (req_id),
  KEY idx_or_status (status, apply_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急下架申请与审批（MG-05-02 / EM-03-04）';

-- ============================================================================
-- 六、分享、访问统计、终端设备（E5/E6/E7）
-- ============================================================================

DROP TABLE IF EXISTS exh_share_link;
CREATE TABLE exh_share_link (
  link_id     bigint      NOT NULL AUTO_INCREMENT,
  target_type varchar(16) NOT NULL COMMENT 'zone/item',
  target_id   bigint      NOT NULL,
  channel     varchar(16) NOT NULL COMMENT 'qrcode/link/terminal/portal',
  short_code  varchar(16) NOT NULL COMMENT '短链码（长期有效，不带时效）',
  qr_path     varchar(500)         DEFAULT NULL COMMENT '二维码图片地址',
  view_count  int         NOT NULL DEFAULT 0,
  create_by   bigint               DEFAULT NULL,
  create_time datetime             DEFAULT NULL,
  PRIMARY KEY (link_id),
  UNIQUE KEY uk_short_code (short_code),
  KEY idx_sl_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享链接与二维码（EM-05-01 / EF-05-01）';

DROP TABLE IF EXISTS exh_access_event;
CREATE TABLE exh_access_event (
  id          bigint      NOT NULL AUTO_INCREMENT,
  zone_id     bigint               DEFAULT NULL,
  item_id     bigint               DEFAULT NULL,
  channel     varchar(16)          DEFAULT NULL COMMENT 'qrcode/link/terminal/portal/direct',
  terminal    varchar(16)          DEFAULT NULL COMMENT 'pc/tablet/mobile/kiosk',
  mode        varchar(16)          DEFAULT NULL COMMENT 'immersive/lite/kiosk',
  duration    int                  DEFAULT NULL COMMENT '停留时长（秒）',
  anon_id     varchar(64)          DEFAULT NULL COMMENT '匿名会话标识，不采身份',
  access_time datetime    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ae_item (item_id, access_time),
  KEY idx_ae_zone (zone_id, access_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问事件（EF-05-02，仅聚合统计）';

DROP TABLE IF EXISTS exh_access_daily;
CREATE TABLE exh_access_daily (
  id          bigint  NOT NULL AUTO_INCREMENT,
  stat_date   date    NOT NULL,
  target_type varchar(16) NOT NULL COMMENT 'zone/item/all',
  target_id   bigint  NOT NULL DEFAULT 0,
  channel     varchar(16) NOT NULL DEFAULT 'all',
  pv          int     NOT NULL DEFAULT 0,
  uv          int     NOT NULL DEFAULT 0,
  avg_duration int    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ad (stat_date, target_type, target_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问统计日聚合（EM-05-02）';

DROP TABLE IF EXISTS exh_device;
CREATE TABLE exh_device (
  device_id    bigint      NOT NULL AUTO_INCREMENT,
  device_code  varchar(64) NOT NULL COMMENT '设备码（一个设备码只能激活一台设备）',
  site_no      varchar(64)          DEFAULT NULL COMMENT '现场编号',
  bind_zone    bigint               DEFAULT NULL COMMENT '绑定展区',
  bind_playlist bigint              DEFAULT NULL COMMENT '绑定播放列表',
  mode         varchar(16)  NOT NULL DEFAULT 'kiosk' COMMENT 'immersive/lite/kiosk',
  config_json  varchar(1000)        DEFAULT NULL COMMENT '超时/音量/亮度等远程配置',
  cache_version varchar(32)         DEFAULT NULL COMMENT '本地缓存内容版本',
  cache_size   bigint               DEFAULT NULL COMMENT '缓存占用（字节）',
  token        varchar(128)         DEFAULT NULL COMMENT '长期令牌（可吊销）',
  auth_expire  datetime             DEFAULT NULL COMMENT '授权有效期',
  status       tinyint      NOT NULL DEFAULT 0 COMMENT '0待激活 1在线 2离线 3停用',
  last_heartbeat datetime           DEFAULT NULL,
  offline_hours int         NOT NULL DEFAULT 0,
  activate_time datetime            DEFAULT NULL,
  del_flag     char(1)      NOT NULL DEFAULT '0',
  create_time  datetime             DEFAULT NULL,
  update_time  datetime             DEFAULT NULL,
  PRIMARY KEY (device_id),
  UNIQUE KEY uk_device_code (device_code),
  KEY idx_dev_status (status, last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='终端设备（TM-01-01~04 / TM-04-02）';

DROP TABLE IF EXISTS exh_playlist;
CREATE TABLE exh_playlist (
  playlist_id bigint      NOT NULL AUTO_INCREMENT,
  name        varchar(100) NOT NULL,
  zone_id     bigint               DEFAULT NULL,
  loop_flag   char(1)     NOT NULL DEFAULT '1',
  status      char(1)     NOT NULL DEFAULT '0',
  create_time datetime             DEFAULT NULL,
  PRIMARY KEY (playlist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放列表（TM-01-02 / TM-02-02）';

DROP TABLE IF EXISTS exh_playlist_item;
CREATE TABLE exh_playlist_item (
  id          bigint NOT NULL AUTO_INCREMENT,
  playlist_id bigint NOT NULL,
  item_id     bigint NOT NULL,
  stay_seconds int   NOT NULL DEFAULT 30 COMMENT '停留时长',
  sort_no     int   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pi_list (playlist_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放列表条目（自动轮播）';

DROP TABLE IF EXISTS exh_device_event;
CREATE TABLE exh_device_event (
  id          bigint      NOT NULL AUTO_INCREMENT,
  device_id   bigint      NOT NULL,
  event_type  varchar(32) NOT NULL COMMENT 'offline/crash/cache_broken/restart',
  detail      varchar(1000)        DEFAULT NULL,
  report_time datetime    NOT NULL,
  handle_flag char(1)     NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  KEY idx_de_dev (device_id, report_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='终端异常上报（TM-04-02）';

-- ============================================================================
-- 七、开放接口与运营（D，OP-02 / OP-03）
-- ============================================================================

DROP TABLE IF EXISTS ops_app;
CREATE TABLE ops_app (
  app_id       bigint      NOT NULL AUTO_INCREMENT,
  app_code     varchar(64) NOT NULL COMMENT 'AppID',
  app_secret   varchar(128) NOT NULL COMMENT 'AppSecret（加密存储）',
  app_name     varchar(100) NOT NULL,
  owner_unit   varchar(100)         DEFAULT NULL COMMENT '所属单位',
  contact      varchar(100)         DEFAULT NULL,
  status       tinyint     NOT NULL DEFAULT 0 COMMENT '0待启用 1启用 2停用',
  expired_at   datetime             DEFAULT NULL COMMENT '密钥有效期',
  rate_limit   int         NOT NULL DEFAULT 600 COMMENT '每分钟总配额',
  create_by    varchar(64)          DEFAULT NULL,
  create_time  datetime             DEFAULT NULL,
  update_time  datetime             DEFAULT NULL,
  PRIMARY KEY (app_id),
  UNIQUE KEY uk_app_code (app_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用注册（OP-03-01）';

DROP TABLE IF EXISTS ops_app_api;
CREATE TABLE ops_app_api (
  id         bigint      NOT NULL AUTO_INCREMENT,
  app_id     bigint      NOT NULL,
  api_code   varchar(32) NOT NULL COMMENT 'OP-02-01~09',
  quota_per_min int      NOT NULL DEFAULT 60 COMMENT '该接口每分钟配额',
  enable     char(1)     NOT NULL DEFAULT '1',
  PRIMARY KEY (id),
  UNIQUE KEY uk_oa (app_id, api_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可访问接口清单（OP-03-02）';

DROP TABLE IF EXISTS ops_app_ip;
CREATE TABLE ops_app_ip (
  id      bigint      NOT NULL AUTO_INCREMENT,
  app_id  bigint      NOT NULL,
  ip_addr varchar(64) NOT NULL COMMENT '支持单IP或 CIDR',
  remark  varchar(200)         DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_oi_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 白名单（OP-03-02）';

DROP TABLE IF EXISTS ops_call_log;
CREATE TABLE ops_call_log (
  id          bigint      NOT NULL AUTO_INCREMENT,
  app_id      bigint               DEFAULT NULL,
  api_code    varchar(32)          DEFAULT NULL,
  request_uri varchar(255)         DEFAULT NULL,
  method      varchar(8)           DEFAULT NULL,
  client_ip   varchar(64)          DEFAULT NULL,
  http_status int                  DEFAULT NULL,
  cost_ms     int                  DEFAULT NULL,
  req_id      varchar(64)          DEFAULT NULL COMMENT '幂等/追踪ID',
  error_msg   varchar(1000)        DEFAULT NULL,
  call_time   datetime    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cl_app (app_id, call_time),
  KEY idx_cl_api (api_code, call_time),
  KEY idx_cl_req (req_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放接口调用日志（OP-03-04）';

DROP TABLE IF EXISTS ops_api_version;
CREATE TABLE ops_api_version (
  id          bigint      NOT NULL AUTO_INCREMENT,
  api_code    varchar(32) NOT NULL,
  version     varchar(16) NOT NULL,
  status      tinyint     NOT NULL DEFAULT 0 COMMENT '0可用 1已弃用 2已下线',
  offline_time datetime            DEFAULT NULL COMMENT '计划下线时间',
  notice      varchar(1000)        DEFAULT NULL COMMENT '下线通知内容',
  create_time datetime             DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_av (api_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口版本管理（OP-03-05）';

DROP TABLE IF EXISTS ops_notification;
CREATE TABLE ops_notification (
  id          bigint      NOT NULL AUTO_INCREMENT,
  event_code  varchar(16) NOT NULL COMMENT 'NT-01 ~ NT-13',
  receiver_id bigint      NOT NULL,
  title       varchar(200) NOT NULL,
  content     varchar(2000)        DEFAULT NULL,
  biz_type    varchar(32)          DEFAULT NULL COMMENT 'task/submission/review/rec/exhibit/device',
  biz_id      bigint               DEFAULT NULL,
  channel     varchar(16) NOT NULL DEFAULT 'inner' COMMENT 'inner=站内信（V3.0 不接短信/微信，字段保留以备后续扩展）',
  is_read     char(1)     NOT NULL DEFAULT '0',
  read_time   datetime             DEFAULT NULL,
  send_time   datetime    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_nt_recv (receiver_id, is_read, send_time),
  KEY idx_nt_event (event_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知（11.2 通知清单落地）';

DROP TABLE IF EXISTS ops_notice_template;
CREATE TABLE ops_notice_template (
  template_id bigint      NOT NULL AUTO_INCREMENT,
  event_code  varchar(16) NOT NULL,
  event_name  varchar(64) NOT NULL,
  title_tpl   varchar(200) NOT NULL,
  content_tpl varchar(1000) NOT NULL,
  channels    varchar(64) NOT NULL DEFAULT 'inner' COMMENT '发送渠道，当前固定 inner（仅站内信）',
  closable    char(1)     NOT NULL DEFAULT '1' COMMENT '是否允许关闭（1可 0不可）',
  status      char(1)     NOT NULL DEFAULT '0',
  PRIMARY KEY (template_id),
  UNIQUE KEY uk_nt_event (event_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知模板（MG-06-04）';

DROP TABLE IF EXISTS ops_backup_record;
CREATE TABLE ops_backup_record (
  id          bigint      NOT NULL AUTO_INCREMENT,
  backup_type varchar(16) NOT NULL COMMENT 'full/incr',
  scope       varchar(200)         DEFAULT NULL COMMENT '备份范围',
  file_path   varchar(500)         DEFAULT NULL,
  file_size   bigint               DEFAULT NULL,
  status      tinyint     NOT NULL DEFAULT 0 COMMENT '0进行中 1成功 2失败',
  start_time  datetime             DEFAULT NULL,
  end_time    datetime             DEFAULT NULL,
  operator_id bigint               DEFAULT NULL,
  fail_reason varchar(500)         DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_br_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份与恢复记录（MG-06-03）';

DROP TABLE IF EXISTS ops_archive_unfreeze;
CREATE TABLE ops_archive_unfreeze (
  id          bigint      NOT NULL AUTO_INCREMENT,
  term_id     bigint      NOT NULL,
  scope_type  varchar(16) NOT NULL COMMENT 'course/task',
  scope_id    bigint      NOT NULL,
  apply_by    bigint      NOT NULL,
  approve_by  bigint               DEFAULT NULL,
  expire_time datetime    NOT NULL COMMENT '解冻时限，到期自动回收',
  status      tinyint     NOT NULL DEFAULT 0 COMMENT '0申请中 1已解冻 2已回收',
  create_time datetime             DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_au_term (term_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学期归档解冻（P10-N03）';

SET FOREIGN_KEY_CHECKS = 1;
-- 统计：业务表 57 张（edu_ 33 / exh_ 15 / ops_ 9），新增表请同步 docs/02-数据库设计.md 清单
