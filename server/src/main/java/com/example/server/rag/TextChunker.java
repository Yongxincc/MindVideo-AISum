package com.example.server.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    @Value("${rag.chunk.size:800}")
    private int chunkSize;

    @Value("${rag.chunk.overlap:100}")
    private int overlap;

    @Autowired
    private EmbeddingClient embeddingClient;

    public List<ChunkSlice> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int effectiveSize = Math.min(chunkSize, embeddingClient.effectiveMaxInputChars());
        int effectiveOverlap = Math.min(overlap, Math.max(0, effectiveSize / 5));

        String normalized = text.trim();
        List<ChunkSlice> slices = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + effectiveSize);
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                slices.add(new ChunkSlice(index++, piece, start, end));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - effectiveOverlap);
        }
        return slices;
    }
}
