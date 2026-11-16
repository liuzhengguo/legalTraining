-- 教师账号与组织管理菜单
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, status, del_flag, create_by, create_time)
SELECT '教师', 'teacher', 3, '3', '0', '0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='teacher' AND del_flag='0');
SET @new_teacher_role_id = IF(ROW_COUNT() = 1, LAST_INSERT_ID(), NULL);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, route_name, menu_type, perms, icon, create_by, create_time)
SELECT '教师管理', org.menu_id, 6, 'teacher', 'edu/teacher/index', 'EduTeacher', 'C', 'edu:teacher:list', 'education', 'admin', NOW()
FROM sys_menu org
WHERE org.menu_id=2011 AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='edu:teacher:list');

INSERT INTO sys_menu (menu_name, parent_id, order_num, menu_type, perms, create_by, create_time)
SELECT actions.name, m.menu_id, actions.sort, 'F', actions.perms, 'admin', NOW()
FROM sys_menu m
CROSS JOIN (SELECT '新增教师' name, 1 sort, 'edu:teacher:add' perms UNION ALL SELECT '编辑教师', 2, 'edu:teacher:edit') actions
WHERE m.perms='edu:teacher:list' AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms=actions.perms);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r JOIN sys_menu m
  ON (r.role_id=@new_teacher_role_id AND (m.menu_id IN (2000,2001,2002,2030,2031,2032,2033,2034,2035) OR m.menu_id BETWEEN 2100 AND 2129)
      AND COALESCE(m.perms,'') NOT LIKE 'edu:submission:%')
  OR (r.role_key='registrar' AND m.perms IN ('edu:teacher:list','edu:teacher:add','edu:teacher:edit'))
WHERE r.status='0' AND r.del_flag='0';

COMMIT;
