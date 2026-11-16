CREATE TABLE IF NOT EXISTS edu_score_appeal (
  appeal_id   bigint       NOT NULL AUTO_INCREMENT,
  score_id    bigint       NOT NULL,
  reason      varchar(500) NOT NULL,
  status      tinyint      NOT NULL DEFAULT 0 COMMENT '0待处理 1维持成绩 2已更正',
  reply       varchar(500)          DEFAULT NULL,
  handler_id  bigint                DEFAULT NULL,
  create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handle_time datetime              DEFAULT NULL,
  PRIMARY KEY (appeal_id),
  UNIQUE KEY uk_score_appeal (score_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩异议（ST-03-03）';
