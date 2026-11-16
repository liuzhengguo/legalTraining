SET @panorama_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='exh_zone' AND column_name='panorama_url'),
  'SELECT 1',
  'ALTER TABLE exh_zone ADD COLUMN panorama_url varchar(500) NULL AFTER cover');
PREPARE panorama_stmt FROM @panorama_sql;
EXECUTE panorama_stmt;
DEALLOCATE PREPARE panorama_stmt;
