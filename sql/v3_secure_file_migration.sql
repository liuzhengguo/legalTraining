-- V3.0 受控教学文件隔离迁移（F01）
-- 说明：本脚本配合代码变更使用。执行前请先按【物理文件迁移】步骤把受控文件移动到 secure 子目录，
--       再执行本脚本的【字段加宽】与【前缀改写】。展厅素材、头像、导入文件不在迁移范围内。
--
-- 背景：教学资源、学生提交附件、任务模板此前经通用上传落到 /profile/upload/，该前缀被 Spring
--       静态放行，知道路径即可绕过业务鉴权直取。现将受控文件统一迁到 /profile/upload/secure/，
--       静态访问被拒绝，只能通过带业务鉴权的接口（serveFile / 附件 content 接口）读取。

-- ============ 1. 字段加宽（secure 前缀多 7 字符，原 varchar(64) 有溢出风险）============
ALTER TABLE edu_resource        MODIFY file_id       varchar(255) DEFAULT NULL COMMENT '文件存储标识（受控目录 /profile/upload/secure/）';
ALTER TABLE edu_submission_file MODIFY file_id       varchar(255) NOT NULL     COMMENT '文件服务标识（受控目录）';
-- edu_task.template_file 已是 varchar(500)，无需加宽
-- edu_achievement.auth_file（授权书扫描件）保留原上传方式，不在本次迁移范围

-- ============ 2. 前缀改写（在【物理文件迁移】完成后执行）============
-- 只改写确实指向 upload 且尚未在 secure 目录下的受控文件，避免重复执行把 secure 改成 secure/secure
UPDATE edu_resource        SET file_id = CONCAT('/profile/upload/secure/', SUBSTRING(file_id, LENGTH('/profile/upload/') + 1))
 WHERE file_id LIKE '/profile/upload/%' AND file_id NOT LIKE '/profile/upload/secure/%';
UPDATE edu_submission_file SET file_id = CONCAT('/profile/upload/secure/', SUBSTRING(file_id, LENGTH('/profile/upload/') + 1))
 WHERE file_id LIKE '/profile/upload/%' AND file_id NOT LIKE '/profile/upload/secure/%';
UPDATE edu_task            SET template_file = CONCAT('/profile/upload/secure/', SUBSTRING(template_file, LENGTH('/profile/upload/') + 1))
 WHERE template_file LIKE '/profile/upload/%' AND template_file NOT LIKE '/profile/upload/secure/%';

-- ============ 3. 迁移核查 ============
-- 执行后应为 0 行；若非 0，说明仍有受控文件停留在公开前缀，需手工核对
SELECT 'edu_resource' AS tbl, COUNT(*) AS remain FROM edu_resource
 WHERE file_id LIKE '/profile/upload/%' AND file_id NOT LIKE '/profile/upload/secure/%'
UNION ALL
SELECT 'edu_submission_file', COUNT(*) FROM edu_submission_file
 WHERE file_id LIKE '/profile/upload/%' AND file_id NOT LIKE '/profile/upload/secure/%'
UNION ALL
SELECT 'edu_task.template', COUNT(*) FROM edu_task
 WHERE template_file LIKE '/profile/upload/%' AND template_file NOT LIKE '/profile/upload/secure/%';

-- ============ 【物理文件迁移】在应用服务器执行（非 SQL）============
-- 目标：把被上述三张表引用的文件从 <profile>/upload/YYYY/MM/DD/ 移动到 <profile>/upload/secure/YYYY/MM/DD/，
--       保持目录层级不变；展厅素材（exh_*）、头像（avatar）、导入（import）不要移动。
-- 参考做法（Linux，按实际 profile 路径调整）：
--   mkdir -p <profile>/upload/secure
--   # 先导出三张表引用的 file_id 清单，逐个移动，例如：
--   #   f=/profile/upload/2026/11/17/x.pdf
--   #   rel=${f#/profile/upload/} && mkdir -p "<profile>/upload/secure/$(dirname "$rel")"
--   #   mv "<profile>/upload/$rel" "<profile>/upload/secure/$rel"
-- 若 legal-training.backup.managed-media=true，移动后需重新登记 ops_media_state：
--   旧相对路径 upload/YYYY/... -> 新相对路径 upload/secure/YYYY/...，否则备份恢复会还原到旧位置。
-- 迁移并确认无误后，重启后端使 SecurityConfig 的 /profile/upload/secure/** denyAll 规则生效。
