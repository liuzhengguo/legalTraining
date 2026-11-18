-- V3.0 本班资源跨班隔离迁移（F03）
-- 背景：visible_scope=2(本班) 的资源此前只校验课程成员资格，同课程下不同班可互相看到/下载。
--       现给 edu_resource 增加 class_id，本班资源必须绑定归属班级，学生侧查询按班级隔离。
-- 依赖：配合代码变更使用；建议与 v3_secure_file_migration.sql 一并执行（受控文件迁移）。

-- ============ 1. 增加归属班级字段 ============
ALTER TABLE edu_resource
  ADD COLUMN class_id bigint NULL COMMENT 'visible_scope=2(本班)时的归属班级（F03）' AFTER visible_scope,
  ADD KEY idx_res_class (class_id);

-- ============ 2. 历史数据兜底回填 ============
-- 用课程主班(edu_course.class_id)回填确实为"本班"且未指定班级的资源。
-- 一门课对应多个班且无法确定主班时保持 NULL，这些资源按 fail-closed 对学生隐藏，由教师重新编辑指定。
UPDATE edu_resource r
  JOIN edu_course_resource cr ON cr.resource_id = r.resource_id
  JOIN edu_course c ON c.course_id = cr.course_id AND c.class_id IS NOT NULL
SET r.class_id = c.class_id
WHERE r.visible_scope = 2 AND r.class_id IS NULL AND r.del_flag = '0';

-- ============ 3. 待教师补录清单（应逐条在资源页重新指定班级）============
SELECT r.resource_id, r.title, c.course_name
FROM edu_resource r
JOIN edu_course_resource cr ON cr.resource_id = r.resource_id
JOIN edu_course c ON c.course_id = cr.course_id
WHERE r.visible_scope = 2 AND r.class_id IS NULL AND r.del_flag = '0';

-- ============ 4. 非本班资源清理 class_id（防止历史脏数据）============
UPDATE edu_resource SET class_id = NULL WHERE visible_scope <> 2;

-- 说明：
-- - 教师资源页在 visible_scope=2 时会出现"归属班级"下拉，必选；保存时服务层校验 class_id 非空。
-- - 学生课程资源列表 / 下载、任务资料列表 / 下载 均已加入班级匹配条件（CLASS_MATCH），
--   口径与课程访问一致：cs.class_id = r.class_id 且 cs.term_id = c.term_id，归档课(c.status=2)沿用历史归属。
