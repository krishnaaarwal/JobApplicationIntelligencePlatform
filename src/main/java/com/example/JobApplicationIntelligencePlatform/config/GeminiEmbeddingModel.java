package com.example.JobApplicationIntelligencePlatform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GeminiEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final String apiKey;
    private String cachedModelName;

    public GeminiEmbeddingModel(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    private String getActiveEmbeddingModel() {
        if (cachedModelName != null) {
            return cachedModelName;
        }
        try {
            ModelsListResponse list = restClient.get()
                    .uri("/models?key={key}", apiKey)
                    .retrieve()
                    .body(ModelsListResponse.class);

            if (list != null && list.models() != null) {
                for (ModelInfo m : list.models()) {
                    if (m.supportedGenerationMethods() != null &&
                            m.supportedGenerationMethods().contains("embedContent")) {
                        cachedModelName = m.name().replace("models/", "");
                        log.info("Auto-detected Gemini Embedding Model: {}", cachedModelName);
                        return cachedModelName;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not auto-detect embedding model, falling back to gemini-embedding-001", e);
        }
        cachedModelName = "gemini-embedding-001";
        return cachedModelName;
    }

    @Override
    public float[] embed(String text) {
        String modelName = getActiveEmbeddingModel();

        Map<String, Object> requestBody = Map.of(
                "model", "models/" + modelName,
                "content", Map.of(
                        "parts", List.of(Map.of("text", text))
                ),
                "outputDimensionality", 768
        );

        GeminiEmbeddingResponse response = restClient.post()
                .uri("/models/{model}:embedContent?key={key}", modelName, apiKey)
                .body(requestBody)
                .retrieve()
                .body(GeminiEmbeddingResponse.class);

        float[] vector = new float[768];
        if (response != null && response.embedding() != null && response.embedding().values() != null) {
            List<Double> values = response.embedding().values();
            int limit = Math.min(values.size(), 768);
            for (int i = 0; i < limit; i++) {
                vector[i] = values.get(i).floatValue();
            }
        }
        return vector;
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        throw new UnsupportedOperationException("Batch call not required; use embed(text)");
    }

    public record GeminiEmbeddingResponse(EmbeddingData embedding) {}
    public record EmbeddingData(List<Double> values) {}
    public record ModelsListResponse(List<ModelInfo> models) {}
    public record ModelInfo(String name, List<String> supportedGenerationMethods) {}
}