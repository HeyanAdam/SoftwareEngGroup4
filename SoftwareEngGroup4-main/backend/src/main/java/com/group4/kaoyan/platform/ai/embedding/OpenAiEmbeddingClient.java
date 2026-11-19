package com.group4.kaoyan.platform.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper;

    private WebClient webClient() {
        AiProperties.Embedding cfg = props.getEmbedding();
        return WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        AiProperties.Embedding cfg = props.getEmbedding();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("input", texts);

        String raw = webClient().post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode data = objectMapper.readTree(raw == null ? "{}" : raw).path("data");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode arr = item.path("embedding");
                float[] v = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    v[i] = (float) arr.get(i).asDouble();
                }
                vectors.add(v);
            }
            if (vectors.isEmpty()) {
                throw new BizException(500, "Embedding 返回为空，请检查 EMBEDDING_BASE_URL / MODEL");
            }
            validateDimension(vectors.get(0).length);
            return vectors;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "向量化失败：" + e.getMessage());
        }
    }

    @Override
    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    private void validateDimension(int actual) {
        int expected = props.getEmbedding().getDimension();
        if (expected > 0 && actual != expected) {
            throw new BizException(500, "向量维度不匹配：配置 " + expected + "，实际 " + actual
                    + "，请同步修改 EMBEDDING_DIMENSION 与 Milvus collection 维度");
        }
    }
}
