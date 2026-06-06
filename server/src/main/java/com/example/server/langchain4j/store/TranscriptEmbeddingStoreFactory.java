package com.example.server.langchain4j.store;

import com.example.server.mapper.TranscriptChunkMapper;
import com.example.server.rag.SimilaritySearch;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TranscriptEmbeddingStoreFactory {

    @Autowired
    private TranscriptChunkMapper chunkMapper;

    @Autowired
    private SimilaritySearch similaritySearch;

    public EmbeddingStore<TextSegment> forMedia(Long mediaId) {
        return new MysqlTranscriptEmbeddingStore(mediaId, chunkMapper, similaritySearch);
    }
}
