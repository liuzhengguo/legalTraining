-- 在已有数据库执行。先停止旧版后端，备份数据库，并使用 UTF-8 导入。
START TRANSACTION;

UPDATE sys_dept SET dept_name = 'legalTraining'
WHERE dept_id = 100 AND dept_name = '若依科技';

UPDATE sys_dept SET leader = 'legalTraining'
WHERE dept_id BETWEEN 100 AND 109 AND leader = '若依';

UPDATE sys_user SET nick_name = 'legalTraining'
WHERE user_id IN (1, 2) AND nick_name = '若依';

DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON rm.menu_id = m.menu_id
WHERE m.menu_id = 4 AND m.menu_name = '若依官网' AND m.path = 'http://ruoyi.vip';

DELETE FROM sys_menu
WHERE menu_id = 4 AND menu_name = '若依官网' AND path = 'http://ruoyi.vip';

UPDATE sys_notice SET notice_title = '温馨提醒：2018-07-01 legalTraining新版本发布啦'
WHERE notice_id = 1 AND notice_title = '温馨提醒：2018-07-01 若依新版本发布啦';

UPDATE sys_notice SET notice_title = '维护通知：2018-07-01 legalTraining系统凌晨维护'
WHERE notice_id = 2 AND notice_title = '维护通知：2018-07-01 若依系统凌晨维护';

UPDATE sys_notice
SET notice_title = 'legalTraining 系统介绍',
    notice_content = '<p>legalTraining 提供用户、角色、部门、岗位、菜单、参数、字典和通知公告管理，并支持任务调度、运行监控和代码生成。</p>'
WHERE notice_id = 3 AND notice_title = '若依开源框架介绍'
  AND notice_content LIKE '%http://ruoyi.vip%';

UPDATE gen_table
SET package_name = CONCAT('com.legaltraining', SUBSTRING(package_name, CHAR_LENGTH('com.ruoyi') + 1))
WHERE package_name = 'com.ruoyi' OR package_name LIKE 'com.ruoyi.%';

UPDATE sys_job
SET invoke_target = CONCAT('com.legaltraining', SUBSTRING(invoke_target, CHAR_LENGTH('com.ruoyi') + 1))
WHERE invoke_target LIKE 'com.ruoyi.%';

UPDATE sys_job
SET invoke_target = CONCAT('demoTask.demo', SUBSTRING(invoke_target, CHAR_LENGTH('ryTask.ry') + 1))
WHERE invoke_target = 'ryTask.ryNoParams' OR invoke_target LIKE 'ryTask.ryNoParams(%'
   OR invoke_target LIKE 'ryTask.ryParams(%' OR invoke_target LIKE 'ryTask.ryMultipleParams(%';

UPDATE sys_job
SET invoke_target = CONCAT('com.legaltraining.quartz.task.DemoTask.demo',
    SUBSTRING(invoke_target, CHAR_LENGTH('com.legaltraining.quartz.task.RyTask.ry') + 1))
WHERE invoke_target = 'com.legaltraining.quartz.task.RyTask.ryNoParams'
   OR invoke_target LIKE 'com.legaltraining.quartz.task.RyTask.ryNoParams(%'
   OR invoke_target LIKE 'com.legaltraining.quartz.task.RyTask.ryParams(%'
   OR invoke_target LIKE 'com.legaltraining.quartz.task.RyTask.ryMultipleParams(%';

COMMIT;
