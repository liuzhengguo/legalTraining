-- ============================================================================
-- v3 增量补丁：分享链接 / 访问统计 / 终端设备（biz_schema.sql 第六节）
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可重复执行，不删除现有数据。
-- 适用：已完成早期 biz_schema.sql 初始化、后补第六节表结构的库。
-- ============================================================================

-- 分享链接与二维码（EM-05-01 / EF-05-01）——学习二维码页面依赖
CREATE TABLE IF NOT EXISTS exh_share_link (
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

-- 访问事件（EF-05-02，仅聚合统计）
CREATE TABLE IF NOT EXISTS exh_access_event (
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

-- 访问统计日聚合（EM-05-02）
CREATE TABLE IF NOT EXISTS exh_access_daily (
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

-- 终端设备（TM-01-01~04 / TM-04-02）
CREATE TABLE IF NOT EXISTS exh_device (
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

-- 播放列表（TM-01-02 / TM-02-02）
CREATE TABLE IF NOT EXISTS exh_playlist (
  playlist_id bigint      NOT NULL AUTO_INCREMENT,
  name        varchar(100) NOT NULL,
  zone_id     bigint               DEFAULT NULL,
  loop_flag   char(1)     NOT NULL DEFAULT '1',
  status      char(1)     NOT NULL DEFAULT '0',
  create_time datetime             DEFAULT NULL,
  PRIMARY KEY (playlist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放列表（TM-01-02 / TM-02-02）';

-- 播放列表条目（自动轮播）
CREATE TABLE IF NOT EXISTS exh_playlist_item (
  id          bigint NOT NULL AUTO_INCREMENT,
  playlist_id bigint NOT NULL,
  item_id     bigint NOT NULL,
  stay_seconds int   NOT NULL DEFAULT 30 COMMENT '停留时长',
  sort_no     int   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pi_list (playlist_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='播放列表条目（自动轮播）';

-- 设备远程指令（TM-03）
CREATE TABLE IF NOT EXISTS exh_device_command (
  command_id bigint NOT NULL AUTO_INCREMENT,
  device_id bigint NOT NULL,
  action varchar(16) NOT NULL,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2成功 3失败',
  claim_token varchar(36),
  detail varchar(240),
  screenshot mediumblob,
  operator_id bigint NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  claimed_at datetime,
  completed_at datetime,
  PRIMARY KEY(command_id),
  KEY idx_device_command(device_id,status,command_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备远程指令（TM-03）';

-- 终端异常上报（TM-04-02）
CREATE TABLE IF NOT EXISTS exh_device_event (
  id          bigint      NOT NULL AUTO_INCREMENT,
  device_id   bigint      NOT NULL,
  event_type  varchar(32) NOT NULL COMMENT 'offline/crash/cache_broken/restart',
  detail      varchar(1000)        DEFAULT NULL,
  report_time datetime    NOT NULL,
  handle_flag char(1)     NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  KEY idx_de_dev (device_id, report_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='终端异常上报（TM-04-02）';
