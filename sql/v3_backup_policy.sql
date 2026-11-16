CREATE TABLE IF NOT EXISTS ops_backup_policy (
  id tinyint NOT NULL,
  enabled tinyint NOT NULL DEFAULT 0,
  full_hours int NOT NULL DEFAULT 168,
  incremental_hours int NOT NULL DEFAULT 24,
  retain_days int NOT NULL DEFAULT 30,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO ops_backup_policy(id) VALUES (1);

INSERT IGNORE INTO sys_menu VALUES (2028, '备份恢复', 2010, 6, 'backup', 'ops/backup/index', NULL, '', 1, 0, 'C', '0', '0', 'ops:backup:admin', 'database', 'admin', sysdate(), '', NULL, 'MG-06-03');
