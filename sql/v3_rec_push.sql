CREATE TABLE IF NOT EXISTS ops_rec_push_job (
    rec_id bigint NOT NULL,
    attempts int NOT NULL DEFAULT 0,
    due_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_id varchar(36),
    lease_until datetime,
    status tinyint NOT NULL DEFAULT 0 COMMENT '0待发送 1已送达 2失败 3已取消',
    last_error varchar(200),
    PRIMARY KEY(rec_id),
    KEY idx_rec_push_due(status,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
