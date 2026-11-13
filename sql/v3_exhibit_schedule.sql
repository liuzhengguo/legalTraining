-- Apply once to an existing V3.0 database. Fresh installs use biz_schema.sql.
ALTER TABLE exh_publish_job
  MODIFY COLUMN plan_time datetime NOT NULL COMMENT '计划发布时间 UTC',
  ADD COLUMN view_level tinyint NOT NULL DEFAULT 2 AFTER schedule_id,
  ADD COLUMN item_version int NOT NULL DEFAULT 0 AFTER view_level,
  ADD COLUMN operator_id bigint DEFAULT NULL AFTER item_version;
