package com.group4.kaoyan.platform.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准 SSE {@code data:} 解析器（每个流一个实例，非线程共享）。
 *
 * <p>只做三件事：把 {@code data:} 负载拼成完整 JSON、抽取 {@code choices[*].delta.content}、
 * 识别 {@code [DONE]}。注释行（{@code :} 开头）、{@code event:}/{@code id:}/{@code retry:}
 * 一律忽略；上游在 {@code data:} 里回 {@code {"error":...}} 时按可重试错误抛出。</p>
 */
@Slf4j
public class OpenAiSseParser {

    private final ObjectMapper objectMapper;
    private final StringBuilder pending = new StringBuilder();

    private boolean completed;
    private long deltaCount;

    public OpenAiSseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 喂入一段原始响应（可能是一行，也可能是多行）。返回本次解析出的文本增量。 */
    public List<String> parse(String raw) {
        List<String> deltas = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return deltas;
        }
        String[] lines = raw.split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                // 空行在标准 SSE 里表示事件结束；但这里可能只是上一个 data 行尾的换行，
                // 半截 JSON 必须留到后续 data 行拼接，所以不强制丢弃。
                flush(deltas, false);
                continue;
            }
            if (trimmed.startsWith(":")) {
                continue;
            }
            if (trimmed.startsWith("data:")) {
                String payload = trimmed.substring(5).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    pending.setLength(0);
                    completed = true;
                    continue;
                }
                pending.append(payload).append('\n');
                flush(deltas, false);
                continue;
            }
            // 少数兼容实现不按 SSE 包 data:，直接回原始 JSON
            if (trimmed.startsWith("{")) {
                pending.append(trimmed);
                flush(deltas, true);
            }
        }
        return deltas;
    }

    /** 流结束时调用：把残留负载再尝试解析一次。 */
    public List<String> finish() {
        List<String> deltas = new ArrayList<>();
        flush(deltas, true);
        return deltas;
    }

    public boolean isCompleted() {
        return completed;
    }

    public long deltaCount() {
        return deltaCount;
    }

    private void flush(List<String> deltas, boolean force) {
        String text = pending.toString().trim();
        if (text.isEmpty()) {
            return;
        }
        // 增量 JSON 可能还没收完：非强制刷新时等看到完整对象再解析
        if (!force && !text.endsWith("}") && !text.endsWith("]")) {
            return;
        }
        pending.setLength(0);
        try {
            JsonNode root = objectMapper.readTree(text);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                String message = error.path("message").asText("模型上游返回错误");
                throw new LlmStreamException(LlmStreamException.UPSTREAM_ERROR, message, true, 0, null);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            for (JsonNode choice : choices) {
                String content = choice.path("delta").path("content").asText("");
                if (content.isEmpty()) {
                    content = choice.path("message").path("content").asText("");
                }
                if (!content.isEmpty()) {
                    deltas.add(content);
                    deltaCount++;
                }
                String finishReason = choice.path("finish_reason").asText("");
                if ("stop".equals(finishReason) || "length".equals(finishReason)) {
                    // finish_reason 只说明本轮生成结束；仍以 [DONE] 作为流结束标志
                    log.debug("模型返回 finish_reason={}", finishReason);
                }
            }
        } catch (LlmStreamException e) {
            throw e;
        } catch (Exception e) {
            // 非强制刷新时可能是半截 JSON；强制刷新时当作噪声跳过
            log.debug("跳过无法解析的 SSE 片段：{}", text);
        }
    }
}
