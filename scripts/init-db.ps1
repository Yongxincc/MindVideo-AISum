# Idempotent: create users / media_files if missing (no DataGrip needed).
$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -ge 7) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$container = "mysql-media"
$running = docker ps --format "{{.Names}}" | Select-String -Pattern "^${container}$" -Quiet
if (-not $running) {
    Write-Host "MySQL container '$container' is not running. Run: docker compose up -d" -ForegroundColor Red
    exit 1
}

$sql = @"
USE media_db;
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(64),
  avatar VARCHAR(512),
  role VARCHAR(32),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS media_files (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  filename VARCHAR(255),
  display_name VARCHAR(128),
  status VARCHAR(32),
  file_path VARCHAR(512),
  ai_summary TEXT,
  transcript_text LONGTEXT,
  transcript_status VARCHAR(16) DEFAULT 'NONE',
  cover_url VARCHAR(512),
  upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'media_db' AND TABLE_NAME = 'media_files' AND COLUMN_NAME = 'display_name'
);
SET @sql = IF(
  @col_exists = 0,
  'ALTER TABLE media_files ADD COLUMN display_name VARCHAR(128) NULL AFTER filename',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ts_col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'media_db' AND TABLE_NAME = 'media_files' AND COLUMN_NAME = 'transcript_status'
);
SET @sql_ts = IF(
  @ts_col_exists = 0,
  'ALTER TABLE media_files ADD COLUMN transcript_status VARCHAR(16) NULL DEFAULT ''NONE'' AFTER transcript_text',
  'SELECT 1'
);
PREPARE stmt_ts FROM @sql_ts;
EXECUTE stmt_ts;
DEALLOCATE PREPARE stmt_ts;
UPDATE media_files SET transcript_status = 'FAILED'
WHERE (transcript_status IS NULL OR transcript_status = 'NONE')
  AND transcript_text IS NOT NULL
  AND (transcript_text LIKE '❌%' OR transcript_text LIKE '处理异常:%');
UPDATE media_files SET transcript_status = 'OK'
WHERE (transcript_status IS NULL OR transcript_status = 'NONE')
  AND transcript_text IS NOT NULL AND CHAR_LENGTH(transcript_text) > 20
  AND transcript_text NOT LIKE '❌%' AND transcript_text NOT LIKE '处理异常:%'
  AND transcript_text NOT LIKE '%正在提取语音%' AND transcript_text NOT LIKE '%正在识别%';
UPDATE media_files SET transcript_status = 'NONE' WHERE transcript_status IS NULL;
SHOW TABLES;
"@

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$output = $sql | docker exec -i $container mysql -uroot -proot 2>&1
$exitCode = $LASTEXITCODE
$ErrorActionPreference = $prevEap

$output | Where-Object { $_ -is [string] } | ForEach-Object { Write-Host $_ }

if ($exitCode -ne 0) {
    Write-Host "Failed to initialize database." -ForegroundColor Red
    exit $exitCode
}
Write-Host "Database tables ready in media_db." -ForegroundColor Green
