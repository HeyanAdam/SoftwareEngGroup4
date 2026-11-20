package com.group4.kaoyan.platform.ai.llm;

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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /chat/completions 的流式实现（DeepSeek、通义、Ollama 均可用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiLlmClient implements LlmClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper;

    private WebClient webClient() {
        AiProperties.Llm cfg = props.getLlm();
        return WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .build();
    }

    private Map<String, Object> buildBody(List<Msg> messages, boolean stream) {
        AiProperties.Llm cfg = props.getLlm();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        List<Map<String, String>> msgs = new ArrayList<>();
        for (Msg m : messages) {
            msgs.add(Map.of("role", m.role(), "content", m.content()));
        }
        body.put("messages", msgs);
        body.put("stream", stream);
        body.put("temperature", cfg.getTemperature());
        body.put("max_tokens", cfg.getMaxTokens());
        return body;
    }

    @Override
    public Flux<String> streamChat(List<Msg> messages) {
        return webClient().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(messages, true))
                .retrieve()
                .bodyToFlux(String.class)
                .map(this::parseChunk)
                .filter(s -> !s.isEmpty());
    }

    @Override
    public String chat(List<Msg> messages) {
        String raw = webClient().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(messages, false))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode node = objectMapper.readTree(raw == null ? "{}" : raw);
            return node.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            throw new BizException(500, "模型返回解析失败：" + e.getMessage());
        }
    }

    /** 一次可能收到多行 SSE，逐行解析 data: 负载 */
    private String parseChunk(String chunk) {
        StringBuilder sb = new StringBuilder();
        for (String line : chunk.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("data:")) {
                continue;
            }
            String payload = t.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            try {
                JsonNode delta = objectMapper.readTree(payload)
                        .path("choices").path(0).path("delta").path("content");
                if (!delta.isMissingNode() && !delta.isNull()) {
                    sb.append(delta.asText());
                }
            } catch (Exception e) {
                log.debug("跳过无法解析的 SSE 片段：{}", payload);
            }
        }
        return sb.toString();
    }
}
