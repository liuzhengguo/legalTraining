CREATE TABLE IF NOT EXISTS edu_task_team_option (
  task_id bigint NOT NULL,
  any_member_submit tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS edu_task_team_contribution (
  task_id bigint NOT NULL,
  group_id bigint NOT NULL,
  user_id bigint NOT NULL,
  contribution varchar(500) DEFAULT NULL,
  PRIMARY KEY (task_id,group_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS edu_task_team_editor (
  task_id bigint NOT NULL,
  group_id bigint NOT NULL,
  user_id bigint NOT NULL,
  last_seen datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id,group_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
