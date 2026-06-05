package com.example.server.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoredChunk {
    private int chunkIndex;
    private String content;
    private double score;
}
