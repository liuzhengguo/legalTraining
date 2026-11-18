-- V3.0 分享事件表增量迁移（F12 / EL-04-02）
-- 现有库执行本脚本后，访客分享事件与访问事件分开统计。
CREATE TABLE IF NOT EXISTS exh_share_event (
  id          bigint      NOT NULL AUTO_INCREMENT,
  target_type varchar(16) NOT NULL COMMENT 'zone/item',
  target_id   bigint      NOT NULL,
  channel     varchar(16) NOT NULL COMMENT 'qrcode/link/terminal/portal/direct',
  share_type  varchar(16) NOT NULL COMMENT 'link/qr/card/system',
  anon_id     varchar(64)          DEFAULT NULL COMMENT '匿名会话标识，不采身份',
  share_time  datetime    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_se_target (target_type, target_id, share_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享事件（EL-04-02，与访问事件分开统计）';
