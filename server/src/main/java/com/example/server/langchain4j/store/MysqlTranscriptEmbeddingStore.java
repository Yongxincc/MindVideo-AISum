package com.example.server.langchain4j.store;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.entity.TranscriptChunk;
import com.example.server.mapper.TranscriptChunkMapper;
import com.example.server.rag.ScoredChunk;
import com.example.server.rag.SimilaritySearch;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 基于 MySQL transcript_chunks 的 LangChain4j EmbeddingStore（按 mediaId 隔离）。
 * 向量写入仍由 {@link com.example.server.service.RagIndexService} 负责，本类专注检索。
 */
public class MysqlTranscriptEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final Long mediaId;
    private final TranscriptChunkMapper chunkMapper;
    private final SimilaritySearch similaritySearch;

    public MysqlTranscriptEmbeddingStore(Long mediaId,
                                         TranscriptChunkMapper chunkMapper,
                                         SimilaritySearch similaritySearch) {
        this.mediaId = mediaId;
        this.chunkMapper = chunkMapper;
        this.similaritySearch = similaritySearch;
    }

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("请通过 RagIndexService 建立索引");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("请通过 RagIndexService 建立索引");
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        throw new UnsupportedOperationException("请通过 RagIndexService 建立索引");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("请通过 RagIndexService 建立索引");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        throw new UnsupportedOperationException("请通过 RagIndexService 建立索引");
    }

    @Override
    public void remove(String id) {
        throw new UnsupportedOperationException("请通过 RagIndexService 删除索引");
    }

    @Override
    public void removeAll(Collection<String> ids) {
        throw new UnsupportedOperationException("请通过 RagIndexService 删除索引");
    }

    @Override
    public void removeAll(dev.langchain4j.store.embedding.filter.Filter filter) {
        throw new UnsupportedOperationException("请通过 RagIndexService 删除索引");
    }

    @Override
    public void removeAll() {
        throw new UnsupportedOperationException("请通过 RagIndexService 删除索引");
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        LoadedChunks loaded = loadChunks();
        if (loaded.isEmpty() || request.queryEmbedding() == null) {
            return new EmbeddingSearchResult<>(List.of());
        }

        float[] queryVec = toFloatArray(request.queryEmbedding());
        int maxResults = request.maxResults() > 0 ? request.maxResults() : 8;
        List<ScoredChunk> hits = similaritySearch.topK(
                loaded.vectors(), loaded.contents(), queryVec, maxResults);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (ScoredChunk hit : hits) {
            int listIndex = hit.getChunkIndex();
            int chunkIndex = listIndex >= 0 && listIndex < loaded.chunkIndexes().size()
                    ? loaded.chunkIndexes().get(listIndex)
                    : listIndex;
            Metadata metadata = Metadata.from("chunkIndex", chunkIndex);
            TextSegment segment = TextSegment.from(hit.getContent(), metadata);
            matches.add(new EmbeddingMatch<>(
                    hit.getScore(),
                    String.valueOf(chunkIndex),
                    request.queryEmbedding(),
                    segment));
        }
        return new EmbeddingSearchResult<>(matches);
    }

    private LoadedChunks loadChunks() {
        List<TranscriptChunk> rows = chunkMapper.selectList(
                new QueryWrapper<TranscriptChunk>()
                        .eq("media_id", mediaId)
                        .orderByAsc("chunk_index"));
        if (rows.isEmpty()) {
            return LoadedChunks.EMPTY;
        }

        List<float[]> vectors = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<Integer> chunkIndexes = new ArrayList<>();
        int expectedDim = -1;

        for (TranscriptChunk row : rows) {
            float[] vec = SimilaritySearch.parseEmbedding(row.getEmbedding());
            if (vec == null || vec.length == 0) {
                continue;
            }
            if (expectedDim < 0) {
                expectedDim = vec.length;
            } else if (vec.length != expectedDim) {
                continue;
            }
            vectors.add(vec);
            contents.add(row.getContent());
            chunkIndexes.add(row.getChunkIndex() != null ? row.getChunkIndex() : chunkIndexes.size());
        }
        return new LoadedChunks(vectors, contents, chunkIndexes);
    }

    private static float[] toFloatArray(Embedding embedding) {
        float[] vector = embedding.vector();
        if (vector != null && vector.length > 0) {
            return vector;
        }
        List<Float> list = embedding.vectorAsList();
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private record LoadedChunks(List<float[]> vectors, List<String> contents, List<Integer> chunkIndexes) {
        static final LoadedChunks EMPTY = new LoadedChunks(List.of(), List.of(), List.of());

        boolean isEmpty() {
            return vectors.isEmpty();
        }
    }
}
