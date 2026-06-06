package com.example.server.service;



import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.example.server.entity.MediaFile;

import com.example.server.entity.TranscriptChunk;

import com.example.server.mapper.MediaFileMapper;

import com.example.server.mapper.TranscriptChunkMapper;

import com.example.server.pipeline.PipelineStage;

import com.example.server.rag.*;

import com.example.server.util.TranscriptStatusHelper;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;



import java.time.LocalDateTime;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.Objects;



@Service

public class RagIndexService {



    @Value("${rag.retrieve.top-k:8}")

    private int topK;



    /** 单次 Embedding 请求条数上限，避免 API 400（批量过大） */

    @Value("${rag.embed.batch-size:8}")

    private int embedBatchSize;



    @Autowired

    private MediaFileMapper mediaFileMapper;



    @Autowired

    private TranscriptChunkMapper chunkMapper;



    @Autowired

    private TextChunker textChunker;



    @Autowired

    private EmbeddingClient embeddingClient;



    @Autowired

    private SimilaritySearch similaritySearch;



    @Autowired

    private PipelineTraceService pipelineTrace;



    public boolean isIndexed(Long mediaId) {

        if (mediaId == null) return false;

        MediaFile media = mediaFileMapper.selectById(mediaId);

        if (media == null || media.getRagIndexedAt() == null) {

            return false;

        }

        QueryWrapper<TranscriptChunk> q = new QueryWrapper<>();

        q.eq("media_id", mediaId);

        if (chunkMapper.selectCount(q) <= 0) {

            return false;

        }

        String storedModel = media.getRagEmbedModel();

        if (storedModel == null || storedModel.isBlank()) {

            backfillEmbedModel(media);

            storedModel = media.getRagEmbedModel();

        }

        return Objects.equals(storedModel, embeddingClient.getModel());

    }



    /**

     * 在问答前同步建立索引；总结流程在完成后也会调用 indexTranscript（不在 retrieve 内隐式触发）。

     */

    public void ensureIndexedBlocking(Long mediaId) throws RagIndexException {

        if (isIndexed(mediaId)) {

            return;

        }

        MediaFile media = mediaFileMapper.selectById(mediaId);

        if (media == null) {

            throw new RagIndexException("媒体记录不存在");

        }

        if (!TranscriptStatusHelper.isReady(media)) {

            throw new RagIndexException("转写尚未完成，请先完成文字提取");

        }

        String indexError = indexTranscriptWithError(mediaId);
        if (indexError != null) {
            throw new RagIndexException(indexError);
        }

    }



    /** @return true 表示索引成功 */
    public boolean indexTranscript(Long mediaId) {
        return indexTranscriptWithError(mediaId) == null;
    }

    /** @return null 表示成功，否则为可读错误信息 */
    public String indexTranscriptWithError(Long mediaId) {
        MediaFile media = mediaFileMapper.selectById(mediaId);
        if (media == null || !TranscriptStatusHelper.isReady(media)) {
            return "转写尚未完成，无法建立 RAG 索引";
        }
        String text = media.getTranscriptText();
        if (text == null || text.isBlank() || text.startsWith("❌")) {
            return "无有效转写文本，无法建立 RAG 索引";
        }

        deleteChunks(mediaId);
        clearRagIndexedAt(mediaId);

        List<ChunkSlice> slices = textChunker.chunk(text);
        if (slices.isEmpty()) {
            return "转写切片为空，无法建立 RAG 索引";
        }

        long indexStart = System.currentTimeMillis();
        pipelineTrace.stageStart(mediaId, PipelineStage.RAG_INDEX,
                "切片数=" + slices.size() + ", 转写字数=" + text.length()
                        + ", embedModel=" + embeddingClient.getModel());

        try {
            List<String> contents = slices.stream()
                    .map(s -> embeddingClient.prepareText(s.getContent()))
                    .toList();
            List<float[]> vectors = embedInBatches(mediaId, contents);

            for (int i = 0; i < slices.size(); i++) {
                ChunkSlice slice = slices.get(i);
                TranscriptChunk row = new TranscriptChunk();
                row.setMediaId(mediaId);
                row.setChunkIndex(slice.getIndex());
                row.setContent(contents.get(i));
                row.setStartOffset(slice.getStartOffset());
                row.setEndOffset(slice.getEndOffset());
                row.setEmbedding(SimilaritySearch.toJson(vectors.get(i)));
                chunkMapper.insert(row);
            }

            media.setRagIndexedAt(LocalDateTime.now());
            media.setRagEmbedModel(embeddingClient.getModel());
            mediaFileMapper.updateById(media);

            long elapsed = System.currentTimeMillis() - indexStart;
            pipelineTrace.stageEnd(mediaId, PipelineStage.RAG_INDEX, true, "索引完成",
                    PipelineTraceService.metrics("chunks", slices.size(), "elapsedMs", elapsed));
            System.out.println("📚 [RAG] indexed mediaId=" + mediaId + " chunks=" + slices.size()
                    + " elapsedMs=" + elapsed);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            deleteChunks(mediaId);
            clearRagIndexedAt(mediaId);
            String detail = formatIndexError(e);
            pipelineTrace.stageEnd(mediaId, PipelineStage.RAG_INDEX, false, detail, null);
            System.err.println("❌ [RAG] index failed mediaId=" + mediaId + ": " + detail);
            return detail;
        }
    }

    private static String formatIndexError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (msg.contains("HTTP 400") || msg.contains("20015") || msg.contains("invalid")) {
            return "RAG 向量索引失败：Embedding 单条输入超过模型 token 上限。"
                    + "请确认 ai.embedding.model=BAAI/bge-m3 并已重启服务；"
                    + "若使用 bge-large 请将 rag.chunk.size 设为 ≤300。详情: " + msg;
        }
        if (msg.contains("HTTP 401") || msg.contains("HTTP 403")) {
            return "RAG 向量索引失败：Embedding API 鉴权失败，请检查 ai.deepseek.api-key 是否有效并已重启服务。详情: " + msg;
        }
        if (msg.contains("HTTP 429")) {
            return "RAG 向量索引失败：Embedding API 请求过于频繁，请稍后重试。详情: " + msg;
        }
        if (msg.contains("timeout") || msg.contains("Timeout") || msg.contains("Connection")) {
            return "RAG 向量索引失败：无法连接 Embedding API，请检查网络与 ai.deepseek.base-url。详情: " + msg;
        }
        return "RAG 向量索引失败，请检查 Embedding API（ai.embedding.model / API Key）后重试。详情: " + msg;
    }

    /** 旧库仅有 rag_indexed_at、无 rag_embed_model 时补齐，避免每次问答都全量重建索引 */
    private void backfillEmbedModel(MediaFile media) {
        media.setRagEmbedModel(embeddingClient.getModel());
        mediaFileMapper.updateById(media);
        System.out.println("📚 [RAG] backfilled rag_embed_model mediaId=" + media.getId()
                + " model=" + media.getRagEmbedModel());
    }

    public void copyIndexFrom(Long sourceMediaId, Long targetMediaId) {

        deleteChunks(targetMediaId);

        clearRagIndexedAt(targetMediaId);

        QueryWrapper<TranscriptChunk> q = new QueryWrapper<>();

        q.eq("media_id", sourceMediaId).orderByAsc("chunk_index");

        List<TranscriptChunk> source = chunkMapper.selectList(q);

        if (source.isEmpty()) {

            indexTranscript(targetMediaId);

            return;

        }

        for (TranscriptChunk c : source) {

            TranscriptChunk copy = new TranscriptChunk();

            copy.setMediaId(targetMediaId);

            copy.setChunkIndex(c.getChunkIndex());

            copy.setContent(c.getContent());

            copy.setEmbedding(c.getEmbedding());

            copy.setStartOffset(c.getStartOffset());

            copy.setEndOffset(c.getEndOffset());

            chunkMapper.insert(copy);

        }

        MediaFile target = mediaFileMapper.selectById(targetMediaId);

        MediaFile sourceMedia = mediaFileMapper.selectById(sourceMediaId);

        if (target != null) {

            target.setRagIndexedAt(LocalDateTime.now());

            target.setRagEmbedModel(sourceMedia != null ? sourceMedia.getRagEmbedModel() : null);

            mediaFileMapper.updateById(target);

        }

    }



    public void deleteChunks(Long mediaId) {

        QueryWrapper<TranscriptChunk> q = new QueryWrapper<>();

        q.eq("media_id", mediaId);

        chunkMapper.delete(q);

    }



    public List<ScoredChunk> retrieve(Long mediaId, String queryText) throws Exception {

        ChunkVectors loaded = loadChunkVectors(mediaId);

        if (loaded.isEmpty()) {

            return List.of();

        }

        pipelineTrace.stageStart(mediaId, PipelineStage.RAG_RETRIEVE, "queryLen=" + queryText.length());

        long t0 = System.currentTimeMillis();

        float[] queryVec = embeddingClient.embedQuery(queryText);

        List<ScoredChunk> hits = similaritySearch.topK(loaded.vectors(), loaded.contents(), queryVec, topK);

        pipelineTrace.stageEnd(mediaId, PipelineStage.RAG_RETRIEVE, true, "命中 " + hits.size() + " 片段",

                PipelineTraceService.metrics("elapsedMs", System.currentTimeMillis() - t0, "topK", topK));

        return hits;

    }



    /**

     * 多路检索后按 chunk 去重，保留最高相关度（Map-Reduce 检索阶段）。

     */

    public List<ScoredChunk> retrieveMerged(Long mediaId, List<String> queries, int limit) throws Exception {

        if (queries == null || queries.isEmpty()) {

            return List.of();

        }

        ChunkVectors loaded = loadChunkVectors(mediaId);

        if (loaded.isEmpty()) {

            return List.of();

        }



        pipelineTrace.stageStart(mediaId, PipelineStage.RAG_RETRIEVE,

                "multiQuery=" + queries.size() + ", topK=" + topK);

        long t0 = System.currentTimeMillis();

        Map<Integer, ScoredChunk> merged = new LinkedHashMap<>();



        for (String query : queries) {

            if (query == null || query.isBlank()) continue;

            float[] queryVec = embeddingClient.embedQuery(query.trim());

            List<ScoredChunk> hits = similaritySearch.topK(

                    loaded.vectors(), loaded.contents(), queryVec, topK);

            for (ScoredChunk hit : hits) {

                merged.merge(hit.getChunkIndex(), hit, (a, b) -> a.getScore() >= b.getScore() ? a : b);

            }

        }



        List<ScoredChunk> result = merged.values().stream()

                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())

                .limit(Math.max(1, limit))

                .toList();



        pipelineTrace.stageEnd(mediaId, PipelineStage.RAG_RETRIEVE, true,

                "多路合并命中 " + result.size() + " 片段",

                PipelineTraceService.metrics(

                        "elapsedMs", System.currentTimeMillis() - t0,

                        "queries", queries.size(),

                        "merged", result.size()));

        return result;

    }



    private ChunkVectors loadChunkVectors(Long mediaId) {

        List<TranscriptChunk> rows = chunkMapper.selectList(

                new QueryWrapper<TranscriptChunk>().eq("media_id", mediaId).orderByAsc("chunk_index"));

        if (rows.isEmpty()) {

            return ChunkVectors.EMPTY;

        }

        List<float[]> vectors = new ArrayList<>();

        List<String> contents = new ArrayList<>();

        int expectedDim = -1;

        for (TranscriptChunk row : rows) {

            float[] vec = SimilaritySearch.parseEmbedding(row.getEmbedding());

            if (vec == null || vec.length == 0) {

                System.err.println("⚠️ [RAG] skip invalid embedding mediaId=" + mediaId

                        + " chunkIndex=" + row.getChunkIndex());

                continue;

            }

            if (expectedDim < 0) {

                expectedDim = vec.length;

            } else if (vec.length != expectedDim) {

                System.err.println("⚠️ [RAG] skip dim mismatch mediaId=" + mediaId

                        + " chunkIndex=" + row.getChunkIndex()

                        + " expected=" + expectedDim + " got=" + vec.length);

                continue;

            }

            vectors.add(vec);

            contents.add(row.getContent());

        }

        return new ChunkVectors(vectors, contents);

    }



    private List<float[]> embedInBatches(Long mediaId, List<String> contents) throws Exception {

        List<float[]> all = new ArrayList<>();

        int batchSize = Math.max(1, embedBatchSize);

        int totalBatches = (int) Math.ceil((double) contents.size() / batchSize);

        for (int i = 0; i < contents.size(); i += batchSize) {

            int batchIndex = i / batchSize + 1;

            int end = Math.min(contents.size(), i + batchSize);

            pipelineTrace.stageProgress(mediaId, PipelineStage.RAG_INDEX,

                    "Embedding 批次 " + batchIndex + "/" + totalBatches);

            long batchStart = System.currentTimeMillis();

            all.addAll(embeddingClient.embedBatch(contents.subList(i, end)));

            System.out.printf("📚 [RAG] embed batch %d/%d size=%d elapsedMs=%d%n",

                    batchIndex, totalBatches, end - i, System.currentTimeMillis() - batchStart);

        }

        return all;

    }



    private void clearRagIndexedAt(Long mediaId) {

        MediaFile media = mediaFileMapper.selectById(mediaId);

        if (media != null && (media.getRagIndexedAt() != null || media.getRagEmbedModel() != null)) {

            media.setRagIndexedAt(null);

            media.setRagEmbedModel(null);

            mediaFileMapper.updateById(media);

        }

    }



    private record ChunkVectors(List<float[]> vectors, List<String> contents) {

        static final ChunkVectors EMPTY = new ChunkVectors(List.of(), List.of());



        boolean isEmpty() {

            return vectors.isEmpty();

        }

    }

}


