package com.group4.kaoyan.modules.chat.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.knowledge.service.PromptTemplateLoader;
import com.group4.kaoyan.modules.knowledge.service.RetrievalService;
import com.group4.kaoyan.platform.ai.llm.LlmClient;
import com.group4.kaoyan.platform.ai.llm.LlmStreamException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流式问答核心：检索 → 组 Prompt → 模型流式生成 → 统一事件流。
 *
 * <p>本类<b>不出现 {@code SseEmitter}</p>：HTTP 适配器负责把事件流翻成旧 SSE 事件名，
 * MQ 消费者负责把事件写入 Redis Stream 并更新数据库。两条链路共用同一份编排逻辑。</p>
 *
 * <p>所有身份/权限信息来自 {@link AiRequestContext}，不读取 {@code StpUtil}，
 * 因此可以在 Tomcat 线程之外（RabbitMQ 监听线程）安全执行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatService chatService;
    private final RetrievalService retrievalService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LlmClient llmClient;
    private final AiProperties props;
    private final ObjectMapper objectMapper;

    public Flux<ChatStreamEvent> stream(AiRequestContext context, String question) {
        return stream(context, question, ChatStreamLifecycle.NOOP);
    }

    /** 带生命周期扩展点的入口：A-2 消费者用它提前建 STREAMING 消息并写 Redis。 */
    public Flux<ChatStreamEvent> stream(AiRequestContext context, String question,
                                        ChatStreamLifecycle lifecycle) {
        ChatStreamLifecycle effective = lifecycle == null ? ChatStreamLifecycle.NOOP : lifecycle;
        return Flux.create(sink -> new Session(context, question, sink, effective).start(),
                FluxSink.OverflowStrategy.BUFFER);
    }

    private final class Session {

        private final AiRequestContext ctx;
        private final String question;
        private final FluxSink<ChatStreamEvent> sink;
        private final ChatStreamLifecycle lifecycle;
        private final AtomicLong sequence = new AtomicLong(0);
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private volatile Disposable subscription;
        private volatile boolean cancelled;

        private ChatConversation conversation;
        private ChatMessage userMessage;
        private List<RetrievalService.Retrieved> hits = List.of();
        private List<Map<String, Object>> references = List.of();
        private final StringBuilder answer = new StringBuilder();

        private Session(AiRequestContext ctx, String question, FluxSink<ChatStreamEvent> sink,
                        ChatStreamLifecycle lifecycle) {
            this.ctx = ctx;
            this.question = question == null ? null : question.trim();
            this.sink = sink;
            this.lifecycle = lifecycle;
        }

        private void start() {
            sink.onCancel(() -> {
                cancelled = true;
                Disposable sub = subscription;
                if (sub != null) {
                    sub.dispose();
                }
                // 用户主动断开：已生成的部分仍然落库，避免刷新后回答凭空消失；
                // 一个字都没生成的（仅收到 meta/references）不落空回答。
                if (answer.length() > 0) {
                    persistAssistantIfPossible(null, "FAILED",
                            "客户端断开，仅保存已生成片段");
                }
                finished.set(true);
            });

            if (question == null || question.isBlank()) {
                fail(ChatStreamException.invalidRequest("问题不能为空"), 0);
                return;
            }
            try {
                conversation = ctx.conversationId() != null
                        ? chatService.findOwned(ctx.userId(), ctx.conversationId())
                        : chatService.createConversation(ctx.userId(), ctx.kbId(), question);
                Long kbId = ctx.kbId() != null ? ctx.kbId() : conversation.getKbId();
                String history = chatService.recentHistory(conversation.getId());
                userMessage = chatService.saveUserMessage(conversation, ctx.userId(), question,
                        ctx.clientMessageId(), ChatMessage.STATUS_COMPLETED);
                lifecycle.onStarted(conversation, userMessage, ctx.streamId());

                emit(ChatStreamEvent.TYPE_META, ChatStreamEvent.payload(
                        "conversationId", conversation.getId(),
                        "userMessageId", userMessage == null ? null : userMessage.getId(),
                        "model", props.getLlm().getModel()));

                // 普通用户不认 kbId；管理员可指定库。视角来自 context，不再读 StpUtil。
                hits = retrievalService.retrieve(kbId, question, ctx.isAdmin());
                references = toReferences(hits, ctx.isAdmin());
                emit(ChatStreamEvent.TYPE_REFERENCES, ChatStreamEvent.payload("items", references));

                String prompt = promptTemplateLoader.render(retrievalService.buildContext(hits), history, question);
                List<LlmClient.Msg> messages = List.of(
                        LlmClient.Msg.system(prompt), LlmClient.Msg.user(question));

                subscription = llmClient.streamChat(messages).subscribe(
                        this::onDelta,
                        this::onError,
                        this::onComplete);
            } catch (Exception e) {
                fail(e, 0);
            }
        }

        private void onDelta(String delta) {
            if (cancelled || delta == null || delta.isEmpty()) {
                return;
            }
            answer.append(delta);
            emit(ChatStreamEvent.TYPE_DELTA, ChatStreamEvent.payload("content", delta));
        }

        private void onError(Throwable error) {
            fail(error, answer.length());
        }

        private void onComplete() {
            if (cancelled || !finished.compareAndSet(false, true)) {
                return;
            }
            ChatMessage saved = persistAssistantIfPossible(null, "COMPLETED", null);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("messageId", saved == null ? null : saved.getId());
            data.put("model", props.getLlm().getModel());
            data.put("promptTokens", 0);
            data.put("completionTokens", 0);
            data.put("finishReason", "stop");
            emit(ChatStreamEvent.TYPE_DONE, data);
            sink.complete();
        }

        private void fail(Throwable error, int emittedChars) {
            if (cancelled || !finished.compareAndSet(false, true)) {
                return;
            }
            ChatMessage saved = persistAssistantIfPossible(error, "FAILED", safeMessage(error));
            String code = errorCodeOf(error);
            boolean retryable = error instanceof LlmStreamException llm && llm.isRetryable();
            String message = safeMessage(error);
            log.warn("流式问答失败：code={} retryable={} message={}", code, retryable, message);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", code);
            data.put("message", message);
            data.put("retryable", retryable);
            data.put("messageId", saved == null ? null : saved.getId());
            data.put("partialChars", emittedChars);
            emit(ChatStreamEvent.TYPE_ERROR, data);
            sink.complete();
        }

        private ChatMessage persistAssistantIfPossible(Throwable error, String status, String errorMessage) {
            if (conversation == null) {
                return null;
            }
            try {
                String referencesJson = toJson(references);
                ChatMessage fromLifecycle = lifecycle.persistAssistant(conversation, ctx.userId(),
                        answer.toString(), referencesJson, props.getLlm().getModel(),
                        ctx.streamId(), status, errorMessage, sequence.get());
                if (fromLifecycle != null) {
                    return fromLifecycle;
                }
                return chatService.saveAssistantMessage(conversation, ctx.userId(),
                        answer.toString(), referencesJson, props.getLlm().getModel());
            } catch (Exception saveError) {
                log.warn("保存回答失败（不影响流关闭）：{}", saveError.getMessage());
                return null;
            }
        }

        private void emit(String type, Map<String, Object> data) {
            if (cancelled) {
                return;
            }
            ChatStreamEvent event = ChatStreamEvent.of(type, ctx.requestId(), ctx.streamId(),
                    sequence.incrementAndGet(), data);
            sink.next(event);
            try {
                lifecycle.onEvent(event);
            } catch (Exception e) {
                log.warn("流式事件旁路处理失败（不影响主流程）：{}", e.getMessage());
            }
        }
    }

    /** 管理员可见文档 id / 切片 id；普通学生只拿脱敏文件名、摘要与相似度。 */
    List<Map<String, Object>> toReferences(List<RetrievalService.Retrieved> hits, boolean admin) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (hits == null) {
            return list;
        }
        for (int i = 0; i < hits.size(); i++) {
            RetrievalService.Retrieved r = hits.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i + 1);
            if (admin) {
                m.put("docName", r.docName());
                m.put("docId", r.docId());
                m.put("chunkId", r.chunkId());
                m.put("score", r.score());
                m.put("snippet", snippet(r.content(), 120));
            } else {
                m.put("fileName", maskFileName(r.docName()));
                m.put("score", Math.round(r.score() * 100) / 100.0);
                m.put("summary", snippet(r.content(), 80));
            }
            list.add(m);
        }
        return list;
    }

    static String maskFileName(String name) {
        if (name == null || name.isBlank()) {
            return "资料";
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String visible = base.length() <= 2 ? base.substring(0, 1) : base.substring(0, 2);
        return visible + "***" + ext;
    }

    private static String snippet(String content, int max) {
        if (content == null) {
            return "";
        }
        return content.length() > max ? content.substring(0, max) : content;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    static String errorCodeOf(Throwable error) {
        if (error instanceof ChatStreamException cse) {
            return cse.getCode();
        }
        if (error instanceof LlmStreamException llm) {
            return llm.getCode();
        }
        if (error instanceof BizException biz) {
            int code = biz.getCode();
            if (code == 404) {
                return "CONVERSATION_NOT_FOUND";
            }
            if (code == com.group4.kaoyan.common.ErrorCode.NO_DATA_ACCESS || code == 403) {
                return "FORBIDDEN";
            }
            if (code == 429) {
                return "RATE_LIMITED";
            }
            if (code >= 400 && code < 500) {
                return "INVALID_REQUEST";
            }
        }
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            return errorCodeOf(cause);
        }
        return "INTERNAL_ERROR";
    }

    static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "生成失败";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
