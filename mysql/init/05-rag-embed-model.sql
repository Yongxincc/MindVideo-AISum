-- 记录 RAG 索引所用 Embedding 模型（换模型后自动触发重建）
-- 已有库请手动执行一次
USE media_db;

ALTER TABLE media_files
  ADD COLUMN rag_embed_model VARCHAR(128) NULL COMMENT 'embedding model used for RAG index' AFTER rag_indexed_at;
