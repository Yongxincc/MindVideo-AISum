package com.example.server.langchain4j;

import com.example.server.dto.CitationDto;
import com.example.server.rag.ScoredChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记录本轮问答中 ContentRetriever / @Tool 命中的片段，用于生成引用溯源。
 */
public final class RetrievalContext {

    private static final ThreadLocal<Map<Integer, ScoredChunk>> HITS = ThreadLocal.withInitial(LinkedHashMap::new);

    private RetrievalContext() {
    }

    public static void setLastHits(List<ScoredChunk> hits) {
        Map<Integer, ScoredChunk> map = new LinkedHashMap<>();
        if (hits != null) {
            for (ScoredChunk hit : hits) {
                map.merge(hit.getChunkIndex(), hit,
                        (a, b) -> a.getScore() >= b.getScore() ? a : b);
            }
        }
        HITS.set(map);
    }

    public static void mergeHits(List<ScoredChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        Map<Integer, ScoredChunk> map = HITS.get();
        for (ScoredChunk hit : hits) {
            map.merge(hit.getChunkIndex(), hit,
                    (a, b) -> a.getScore() >= b.getScore() ? a : b);
        }
    }

    public static List<CitationDto> toCitations() {
        return HITS.get().values().stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .map(c -> new CitationDto(
                        c.getChunkIndex() + 1,
                        c.getScore(),
                        truncate(c.getContent(), 300)))
                .toList();
    }

    public static void clear() {
        HITS.remove();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
