-- 保留已关联的岗位 ID，将企业示例岗位迁移为学校岗位。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_post SET post_code='campus_admin', post_name='系统管理员', update_by='admin', update_time=NOW()
WHERE post_id=1 AND post_code='ceo' AND post_name='董事长';
UPDATE sys_post SET post_code='faculty_teacher', post_name='专任教师', update_by='admin', update_time=NOW()
WHERE post_id=2 AND post_code='se' AND post_name='项目经理';
UPDATE sys_post SET post_code='academic_affairs', post_name='教务管理', update_by='admin', update_time=NOW()
WHERE post_id=3 AND post_code='hr' AND post_name='人力资源';
UPDATE sys_post SET post_code='admin_support', post_name='行政教辅', update_by='admin', update_time=NOW()
WHERE post_id=4 AND post_code='user' AND post_name='普通员工';

INSERT INTO sys_post (post_code, post_name, post_sort, status, create_by, create_time)
SELECT p.code, p.name, p.sort_order, '0', 'admin', NOW()
FROM (
    SELECT 'faculty_head' code, '院系负责人' name, 5 sort_order
    UNION ALL SELECT 'student_counselor', '辅导员', 6
    UNION ALL SELECT 'training_admin', '实验实训管理员', 7
) p
WHERE NOT EXISTS (SELECT 1 FROM sys_post s WHERE s.post_code=p.code OR s.post_name=p.name);

COMMIT;
