-- RAG + content MD5 (auto-run on first MySQL init; existing DB: run this script once manually)
USE media_db;

ALTER TABLE media_files
  ADD COLUMN content_md5 VARCHAR(32) NULL COMMENT 'file content fingerprint',
  ADD COLUMN rag_indexed_at DATETIME NULL COMMENT 'last RAG vector index time';

CREATE INDEX idx_media_content_md5 ON media_files (content_md5);

CREATE TABLE IF NOT EXISTS transcript_chunks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  media_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  embedding JSON NOT NULL,
  start_offset INT DEFAULT 0,
  end_offset INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_media_chunk (media_id, chunk_index),
  CONSTRAINT fk_chunks_media FOREIGN KEY (media_id) REFERENCES media_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
