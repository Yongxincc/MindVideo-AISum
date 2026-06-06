package com.example.server.langchain4j.retriever;

import com.example.server.langchain4j.MediaQaContext;
import com.example.server.langchain4j.RetrievalContext;
import com.example.server.langchain4j.store.TranscriptEmbeddingStoreFactory;
import com.example.server.rag.ScoredChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LangChain4j ContentRetriever：按当前 mediaId 从 MySQL 向量库检索转写片段。
 */
@Component
public class MediaContentRetriever implements ContentRetriever {

    @Value("${rag.retrieve.top-k:8}")
    private int topK;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private TranscriptEmbeddingStoreFactory storeFactory;

    @Override
    public List<Content> retrieve(Query query) {
        Long mediaId = MediaQaContext.getMediaId();
        if (mediaId == null || query == null || query.text() == null || query.text().isBlank()) {
            return List.of();
        }

        EmbeddingStore<TextSegment> store = storeFactory.forMedia(mediaId);
        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();
        EmbeddingSearchResult<TextSegment> result = store.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .build());

        List<ScoredChunk> hits = result.matches().stream()
                .map(this::toScoredChunk)
                .toList();
        RetrievalContext.mergeHits(hits);

        return result.matches().stream()
                .map(match -> Content.from(match.embedded()))
                .toList();
    }

    private ScoredChunk toScoredChunk(EmbeddingMatch<TextSegment> match) {
        int chunkIndex = 0;
        if (match.embedded() != null && match.embedded().metadata() != null) {
            Integer idx = match.embedded().metadata().getInteger("chunkIndex");
            if (idx != null) {
                chunkIndex = idx;
            }
        }
        String text = match.embedded() != null ? match.embedded().text() : "";
        return new ScoredChunk(chunkIndex, text, match.score());
    }
}
