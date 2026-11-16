CREATE TABLE IF NOT EXISTS ops_rec_inbox (
  id bigint NOT NULL AUTO_INCREMENT,
  source_app varchar(64) NOT NULL,
  source_rec_id bigint NOT NULL,
  source_teacher varchar(64),
  title varchar(200) NOT NULL,
  content mediumtext,
  reason varchar(1000),
  sign_mode varchar(16) NOT NULL,
  author_label varchar(100) NOT NULL DEFAULT '匿名',
  scope_desc varchar(32) NOT NULL,
  proof_type varchar(12) NOT NULL,
  confirmed_at varchar(32) NOT NULL,
  status tinyint NOT NULL DEFAULT 1,
  decision_checks json,
  opinion varchar(1000),
  reviewed_at datetime,
  reviewer_id bigint,
  item_id bigint,
  exhibit_status tinyint,
  exhibit_revision bigint NOT NULL DEFAULT 0,
  callback_state tinyint,
  attempts int NOT NULL DEFAULT 0,
  due_at datetime,
  lease_until datetime,
  lease_id varchar(36),
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(id),
  UNIQUE KEY uk_rec_inbox_source(source_app,source_rec_id),
  UNIQUE KEY uk_rec_inbox_item(item_id),
  KEY idx_rec_inbox_callback(callback_state,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_rec_exhibit_state (
  rec_id bigint NOT NULL,
  item_id bigint NOT NULL,
  exhibit_status tinyint NOT NULL,
  revision bigint NOT NULL,
  PRIMARY KEY(rec_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_rec_evidence_snapshot (
  rec_id bigint NOT NULL,
  submission_id bigint NOT NULL,
  submission_version int NOT NULL,
  source_content mediumtext,
  PRIMARY KEY(rec_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_rec_callback_receipt (
  rec_id bigint NOT NULL,
  callback_id varchar(36) NOT NULL,
  payload_hash char(64) NOT NULL,
  external_auditor_id bigint,
  received_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(rec_id,callback_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
