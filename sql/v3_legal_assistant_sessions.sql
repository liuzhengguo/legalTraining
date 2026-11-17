CREATE TABLE IF NOT EXISTS edu_legal_assistant_session (
    session_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_assistant_session_owner (user_id, update_time, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @session_column_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
           AND table_name='edu_legal_assistant_record' AND column_name='session_id'),
    'SELECT 1',
    'ALTER TABLE edu_legal_assistant_record ADD COLUMN session_id BIGINT NULL, ADD KEY idx_assistant_session_records (session_id,record_id)');
PREPARE session_column_stmt FROM @session_column_sql;
EXECUTE session_column_stmt;
DEALLOCATE PREPARE session_column_stmt;

START TRANSACTION;
INSERT INTO edu_legal_assistant_session(user_id,title,create_time,update_time)
SELECT user_id,'历史问答',MIN(create_time),MAX(create_time)
FROM edu_legal_assistant_record WHERE session_id IS NULL GROUP BY user_id;

UPDATE edu_legal_assistant_record a
JOIN (SELECT user_id, MAX(session_id) session_id FROM edu_legal_assistant_session
      WHERE title='历史问答' GROUP BY user_id) s ON s.user_id=a.user_id
SET a.session_id=s.session_id WHERE a.session_id IS NULL;
COMMIT;
