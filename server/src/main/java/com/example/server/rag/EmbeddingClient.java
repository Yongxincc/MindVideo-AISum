package com.example.server.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.server.util.RetryHelper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class EmbeddingClient {

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${ai.embedding.model:BAAI/bge-large-zh-v1.5}")
    private String embeddingModel;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public float[] embed(String text) throws Exception {
        List<float[]> batch = embedBatch(List.of(text));
        return batch.isEmpty() ? new float[0] : batch.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        return RetryHelper.executeWithBackoff(
                3,
                1000,
                15000,
                () -> callEmbeddingsApi(texts),
                RetryHelper::isRetryableHttpOrNetwork
        );
    }

    private List<float[]> callEmbeddingsApi(List<String> texts) throws IOException {
        String url = baseUrl + "/embeddings";
        JSONObject body = new JSONObject();
        body.put("model", embeddingModel);
        body.put("input", texts);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
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
            List<JSONObject> items = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                items.add(data.getJSONObject(i));
            }
            items.sort(Comparator.comparingInt(o -> o.containsKey("index") ? o.getIntValue("index") : 0));

            List<float[]> result = new ArrayList<>(items.size());
            for (JSONObject item : items) {
                JSONArray emb = item.getJSONArray("embedding");
                float[] vec = new float[emb.size()];
                for (int j = 0; j < emb.size(); j++) {
                    vec[j] = emb.getFloatValue(j);
                }
                result.add(vec);
            }
            if (result.size() != texts.size()) {
                throw new IOException("Embedding count mismatch: expected " + texts.size()
                        + " got " + result.size());
            }
            return result;
        }
    }
}
