package com.example.server.rag;

import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SimilaritySearch {

    public List<ScoredChunk> topK(List<float[]> vectors, List<String> contents, float[] query, int k) {
        if (vectors == null || contents == null || query == null || vectors.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            float[] vec = vectors.get(i);
            if (vec == null || i >= contents.size()) continue;
            double sim = cosine(query, vec);
            scored.add(new ScoredChunk(i, contents.get(i), sim));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        int limit = Math.min(k, scored.size());
        return scored.subList(0, limit);
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static float[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) return new float[0];
        List<Float> list = JSON.parseArray(json, Float.class);
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    public static String toJson(float[] embedding) {
        return JSON.toJSONString(embedding);
    }
}
