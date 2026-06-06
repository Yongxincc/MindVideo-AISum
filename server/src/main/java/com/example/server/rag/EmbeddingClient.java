package com.example.server.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.server.util.RetryHelper;
import okhttp3.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class EmbeddingClient {

    private static final String BGE_LARGE_ZH_QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";
    /** bge-large 系列约 512 token，中文安全上限 */
    private static final int BGE_LARGE_MAX_CHARS = 400;

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${ai.embedding.model:BAAI/bge-m3}")
    private String embeddingModel;

    @Value("${ai.embedding.max-input-chars:1500}")
    private int maxInputChars;

    @Value("${ai.embedding.query-prefix:}")
    private String queryPrefix;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    @PostConstruct
    void logConfig() {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            System.err.println("❌ [Embedding] ai.deepseek.api-key 未配置，RAG 向量索引将失败");
            return;
        }
        System.out.println("📐 [Embedding] model=" + embeddingModel
                + " maxInputChars=" + effectiveMaxInputChars()
                + " baseUrl=" + baseUrl);
    }

    public String getModel() {
        return embeddingModel;
    }

    /** 与 {@link #prepareText(String)} 一致，供 TextChunker 对齐切片上限 */
    public int effectiveMaxInputChars() {
        if (embeddingModel.contains("bge-large")) {
            return Math.min(maxInputChars, BGE_LARGE_MAX_CHARS);
        }
        return maxInputChars;
    }

    public String prepareText(String text) {
        return normalizeInput(text);
    }

    public float[] embed(String text) throws Exception {
        List<float[]> batch = embedBatch(List.of(text));
        return batch.isEmpty() ? new float[0] : batch.get(0);
    }

    public float[] embedQuery(String query) throws Exception {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Embedding 查询不能为空");
        }
        String prefix = resolveQueryPrefix();
        String text = prefix.isEmpty() ? query.trim() : prefix + query.trim();
        return embed(text);
    }

    public List<float[]> embedBatch(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> normalized = texts.stream().map(this::normalizeInput).toList();

        return RetryHelper.executeWithBackoff(
                3,
                1000,
                15000,
                () -> embedBatchWithSplitFallback(normalized),
                RetryHelper::isRetryableHttpOrNetwork
        );
    }

    private List<float[]> embedBatchWithSplitFallback(List<String> texts) throws Exception {
        try {
            return callEmbeddingsApi(texts);
        } catch (IOException e) {
            if (!isInvalidParameterError(e) || texts.size() <= 1) {
                throw e;
            }
            System.err.println("⚠️ [Embedding] batch size=" + texts.size()
                    + " failed (" + e.getMessage() + "), fallback to single requests");
            List<float[]> result = new ArrayList<>(texts.size());
            for (String text : texts) {
                result.addAll(callEmbeddingsApi(List.of(text)));
            }
            return result;
        }
    }

    private static boolean isInvalidParameterError(IOException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return msg.contains("HTTP 400") || msg.contains("20015") || msg.contains("invalid");
    }

    private String resolveQueryPrefix() {
        if (queryPrefix != null && !queryPrefix.isBlank()) {
            return queryPrefix;
        }
        if (embeddingModel.contains("bge-large")) {
            return BGE_LARGE_ZH_QUERY_PREFIX;
        }
        return "";
    }

    private String normalizeInput(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding 输入不能为空");
        }
        String trimmed = text.trim();
        int limit = effectiveMaxInputChars();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        System.out.println("⚠️ [Embedding] 单条截断 " + trimmed.length() + " -> " + limit
                + " chars (model=" + embeddingModel + ")");
        return trimmed.substring(0, limit);
    }

    private List<float[]> callEmbeddingsApi(List<String> texts) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Embedding API Key 未配置（ai.deepseek.api-key）");
        }
        String url = baseUrl + "/embeddings";
        JSONObject body = new JSONObject();
        body.put("model", embeddingModel);
        body.put("input", texts);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("Embedding HTTP " + response.code() + ": " + err);
            }
            String json = response.body().string();
            JSONObject root = JSON.parseObject(json);
            JSONArray data = root.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                throw new IOException("Embedding response empty");
            }
            return parseEmbeddingResponse(data, texts.size());
        }
    }

    private List<float[]> parseEmbeddingResponse(JSONArray data, int expectedCount) throws IOException {
        if (data.size() != expectedCount) {
            throw new IOException("Embedding count mismatch: expected " + expectedCount
                    + " got " + data.size());
        }

        List<float[]> result = new ArrayList<>(expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            result.add(null);
        }

        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            JSONArray emb = item.getJSONArray("embedding");
            if (emb == null || emb.isEmpty()) {
                throw new IOException("Embedding vector empty at response item " + i);
            }

            int idx;
            if (item.containsKey("index")) {
                idx = item.getIntValue("index");
            } else if (expectedCount == 1) {
                idx = 0;
            } else {
                throw new IOException("Embedding batch response missing index field");
            }
            if (idx < 0 || idx >= expectedCount) {
                throw new IOException("Embedding index out of range: " + idx);
            }
            result.set(idx, toVector(emb));
        }

        for (int i = 0; i < expectedCount; i++) {
            if (result.get(i) == null) {
                throw new IOException("Embedding response missing vector for index " + i);
            }
        }
        return result;
    }

    private static float[] toVector(JSONArray emb) {
        float[] vec = new float[emb.size()];
        for (int j = 0; j < emb.size(); j++) {
            vec[j] = emb.getFloatValue(j);
        }
        return vec;
    }
}
