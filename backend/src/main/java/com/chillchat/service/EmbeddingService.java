package com.chillchat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chillchat.entity.MessageEmbedding;
import com.chillchat.mapper.MessageEmbeddingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class EmbeddingService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    private static final String EMBEDDING_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    private static final String EMBEDDING_MODEL = "text-embedding-v3";

    @Autowired
    private MessageEmbeddingMapper messageEmbeddingMapper;

    /**
     * Call DashScope text-embedding-v3 and return the embedding vector.
     */
    public float[] getEmbedding(String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", EMBEDDING_MODEL);
            JSONArray input = new JSONArray();
            input.add(text);
            body.put("input", input);
            body.put("encoding_format", "float");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(EMBEDDING_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = JSON.parseObject(response.body());
                JSONArray data = json.getJSONArray("data");
                if (data != null && !data.isEmpty()) {
                    JSONArray embeddingArray = data.getJSONObject(0).getJSONArray("embedding");
                    float[] result = new float[embeddingArray.size()];
                    for (int i = 0; i < embeddingArray.size(); i++) {
                        result[i] = embeddingArray.getFloatValue(i);
                    }
                    return result;
                }
            } else {
                System.err.println("[EmbeddingService] API error: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[EmbeddingService] getEmbedding failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cosine similarity between two vectors.
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    /**
     * Serialize float array to JSON string for storage.
     */
    public String serializeEmbedding(float[] vector) {
        JSONArray arr = new JSONArray();
        for (float v : vector) {
            arr.add(v);
        }
        return arr.toString();
    }

    /**
     * Deserialize JSON string back to float array.
     */
    public float[] deserializeEmbedding(String json) {
        JSONArray arr = JSON.parseArray(json);
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.getFloatValue(i);
        }
        return result;
    }

    /**
     * Asynchronously generate embedding for a user message and store it in DB.
     */
    @Async
    public void asyncEmbedAndStore(Long messageId, Long userId, String content) {
        try {
            float[] vector = getEmbedding(content);
            if (vector == null) return;

            MessageEmbedding record = new MessageEmbedding();
            record.setMessageId(messageId);
            record.setUserId(userId);
            record.setEmbedding(serializeEmbedding(vector));
            record.setCreateTime(new Date());
            messageEmbeddingMapper.insert(record);
        } catch (Exception e) {
            System.err.println("[EmbeddingService] asyncEmbedAndStore failed for messageId=" + messageId + ": " + e.getMessage());
        }
    }
}
