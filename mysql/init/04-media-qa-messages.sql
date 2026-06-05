-- RAG 问答历史（已有库请手动执行一次）
USE media_db;

CREATE TABLE IF NOT EXISTS media_qa_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  media_id BIGINT NOT NULL,
  question TEXT NOT NULL,
  answer TEXT,
  citations_json JSON NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OK',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_media_qa_created (media_id, created_at),
  CONSTRAINT fk_qa_media FOREIGN KEY (media_id) REFERENCES media_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
