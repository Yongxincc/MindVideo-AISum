-- Migrate existing media_db volumes that already ran 01-schema.sql
USE media_db;

SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'media_db'
    AND TABLE_NAME = 'media_files'
    AND COLUMN_NAME = 'transcript_status'
);

SET @sql = IF(
  @col_exists = 0,
  'ALTER TABLE media_files ADD COLUMN transcript_status VARCHAR(16) NULL DEFAULT ''NONE'' AFTER transcript_text',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE media_files
SET transcript_status = 'FAILED'
WHERE (transcript_status IS NULL OR transcript_status = 'NONE')
  AND transcript_text IS NOT NULL
  AND (
    transcript_text LIKE '❌%'
    OR transcript_text LIKE '处理异常:%'
  );

UPDATE media_files
SET transcript_status = 'OK'
WHERE (transcript_status IS NULL OR transcript_status = 'NONE')
  AND transcript_text IS NOT NULL
  AND CHAR_LENGTH(transcript_text) > 20
  AND transcript_text NOT LIKE '❌%'
  AND transcript_text NOT LIKE '处理异常:%'
  AND transcript_text NOT LIKE '%正在提取语音%'
  AND transcript_text NOT LIKE '%正在识别%';

UPDATE media_files
SET transcript_status = 'NONE'
WHERE transcript_status IS NULL;
