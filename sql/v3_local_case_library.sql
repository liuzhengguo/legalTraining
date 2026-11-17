SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ops_local_case (
  case_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  title varchar(250) NOT NULL,
  summary varchar(1000) NOT NULL DEFAULT '',
  source varchar(250) NOT NULL DEFAULT '',
  content mediumtext NOT NULL,
  active tinyint NOT NULL DEFAULT 1,
  created_by bigint NOT NULL,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_local_case_active (active, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,route_name,menu_type,perms,icon,create_by,create_time)
SELECT '案例库', menu_id, 6, 'case-library', 'edu/caseLibrary/index', 'EduCaseLibrary', 'C',
       'edu:case:manage', 'documentation', 'admin', NOW()
FROM sys_menu
WHERE menu_id=2030 AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='edu:case:manage');

INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_key IN ('teacher','registrar') AND r.status='0' AND r.del_flag='0'
AND (m.perms='edu:case:manage' OR m.menu_id=2030);
COMMIT;
