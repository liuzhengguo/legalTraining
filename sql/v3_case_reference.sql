CREATE TABLE IF NOT EXISTS ops_case_reference (
  target_type varchar(8) NOT NULL,
  target_id bigint NOT NULL,
  case_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  title varchar(250) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (target_type,target_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
