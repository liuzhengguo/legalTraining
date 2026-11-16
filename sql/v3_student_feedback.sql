CREATE TABLE IF NOT EXISTS edu_student_feedback (
  feedback_id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint NOT NULL,
  content varchar(1000) NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (feedback_id),
  KEY idx_student_feedback_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生端意见反馈';
