package com.example.server.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChunkSlice {
    private int index;
    private String content;
    private int startOffset;
    private int endOffset;
}
