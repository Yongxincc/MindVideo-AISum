-- 已有库：补齐 rag_embed_model，避免每次 RAG 提问都触发全量重建
USE media_db;

UPDATE media_files m
SET rag_embed_model = 'BAAI/bge-m3'
WHERE rag_indexed_at IS NOT NULL
  AND (rag_embed_model IS NULL OR rag_embed_model = '')
  AND EXISTS (SELECT 1 FROM transcript_chunks c WHERE c.media_id = m.id);
