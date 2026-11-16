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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
