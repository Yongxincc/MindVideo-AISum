-- Auto-run on first MySQL container start (empty data volume).
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
