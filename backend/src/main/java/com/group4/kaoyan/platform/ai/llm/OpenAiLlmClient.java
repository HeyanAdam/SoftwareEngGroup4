package com.group4.kaoyan.platform.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI 兼容 /chat/completions 的流式实现（DeepSeek、通义、Ollama 均可用）。
 *
 * <p>A-1 改造点：</p>
 * <ul>
 *   <li>连接超时 / 读取超时分开配置，避免"连不上"和"上游长时间不出字"混为一个超时；</li>
 *   <li>按标准 SSE {@code data:} 解析（委托 {@link OpenAiSseParser}），支持一行多个事件、跨行分包；</li>
 *   <li>401/403、429、5xx、超时、上游断流统一映射为 {@link LlmStreamException}，
 *       由上层决定是否重试/如何下发 {@code error} 事件。</li>
 * </ul>
 */
@Slf4j
@Component
public class OpenAiLlmClient implements LlmClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiLlmClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        AiProperties.Llm cfg = props.getLlm();
        int connectSeconds = Math.max(1, cfg.getConnectTimeoutSeconds());
        int readSeconds = Math.max(1, cfg.getReadTimeoutSeconds());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectSeconds * 1000)
                .responseTimeout(Duration.ofSeconds(readSeconds))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readSeconds)));
        this.webClient = WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
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
        return Flux.defer(() -> {
            OpenAiSseParser parser = new OpenAiSseParser(objectMapper);
            Flux<String> raw = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(buildBody(messages, true))
                    .exchangeToFlux(resp -> {
                        HttpStatusCode status = resp.statusCode();
                        if (status.isError()) {
                            return resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMapMany(body -> Flux.error(mapStatus(status.value(), body)));
                        }
                        return resp.bodyToFlux(String.class);
                    })
                    .onErrorMap(this::translate);

            Flux<String> content = raw.concatMapIterable(parser::parse);
            Flux<String> tail = Flux.defer(() -> {
                List<String> rest = parser.finish();
                Flux<String> restFlux = rest.isEmpty() ? Flux.empty() : Flux.fromIterable(rest);
                if (!parser.isCompleted() && parser.deltaCount() == 0) {
                    return restFlux.concatWith(Flux.error(new LlmStreamException(
                            LlmStreamException.UPSTREAM_STREAM_CLOSED,
                            "模型连接在返回内容前中断", true)));
                }
                return restFlux;
            });
            return content.concatWith(tail);
        });
    }

    @Override
    public String chat(List<Msg> messages) {
        AiProperties.Llm cfg = props.getLlm();
        String raw = webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(messages, false))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(mapStatus(resp.statusCode().value(), body))))
                .bodyToMono(String.class)
                .onErrorMap(this::translate)
                .block(Duration.ofSeconds(Math.max(1, cfg.getReadTimeoutSeconds())));
        try {
            JsonNode node = objectMapper.readTree(raw == null ? "{}" : raw);
            return node.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            throw new BizException(500, "模型返回解析失败：" + e.getMessage());
        }
    }

    /** HTTP 状态码 → 明确错误码；401/403 不重试，429/5xx 可重试。 */
    static LlmStreamException mapStatus(int status, String body) {
        String detail = body == null || body.isBlank() ? "" : "：" + abbreviate(body);
        if (status == 401 || status == 403) {
            return new LlmStreamException(LlmStreamException.AUTH_FAILED,
                    "模型鉴权失败（HTTP " + status + "）" + detail, false, status, null);
        }
        if (status == 429) {
            return new LlmStreamException(LlmStreamException.RATE_LIMITED,
                    "模型限流（HTTP 429）" + detail, true, status, null);
        }
        if (status == 408 || status == 504) {
            return new LlmStreamException(LlmStreamException.TIMEOUT,
                    "模型超时（HTTP " + status + "）" + detail, true, status, null);
        }
        if (status >= 500) {
            return new LlmStreamException(LlmStreamException.UPSTREAM_ERROR,
                    "模型上游异常（HTTP " + status + "）" + detail, true, status, null);
        }
        return new LlmStreamException(LlmStreamException.BAD_REQUEST,
                "模型请求被拒绝（HTTP " + status + "）" + detail, false, status, null);
    }

    /** 网络/超时异常 → 错误码，避免把底层异常原样抛给业务层。 */
    LlmStreamException translate(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof LlmStreamException llm) {
                return llm;
            }
            if (cause instanceof ReadTimeoutException || cause instanceof TimeoutException) {
                return new LlmStreamException(LlmStreamException.TIMEOUT,
                        "模型响应超时", true, 0, throwable);
            }
            if (cause instanceof WebClientRequestException) {
                // Reactor Netty 的读超时会被包在 WebClientRequestException 里，
                // 必须先看 cause chain，否则会误判成普通网络失败。
                Throwable nested = cause.getCause();
                while (nested != null && nested != cause) {
                    if (nested instanceof ReadTimeoutException || nested instanceof TimeoutException) {
                        return new LlmStreamException(LlmStreamException.TIMEOUT,
                                "模型响应超时", true, 0, throwable);
                    }
                    nested = nested.getCause();
                }
                String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
                boolean timeout = message.contains("timeout") || message.contains("timed out")
                        || message.contains("response timeout");
                return new LlmStreamException(timeout ? LlmStreamException.TIMEOUT : LlmStreamException.NETWORK_ERROR,
                        timeout ? "模型响应超时" : "模型网络连接失败", true, 0, throwable);
            }
            cause = cause.getCause();
        }
        return new LlmStreamException(LlmStreamException.NETWORK_ERROR,
                "模型网络异常：" + (throwable.getMessage() == null ? throwable.getClass().getSimpleName()
                        : throwable.getMessage()), true, 0, throwable);
    }

    private static String abbreviate(String body) {
        String oneLine = body.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }
}
