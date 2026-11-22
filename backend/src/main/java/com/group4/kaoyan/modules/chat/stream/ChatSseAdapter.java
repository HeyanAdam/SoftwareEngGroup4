package com.group4.kaoyan.modules.chat.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE 兼容适配器：{@code Flux<ChatStreamEvent>} → 旧事件名 {@code meta/references/delta/done}。
 *
 * <p>旧前端不改也能用；新前端（C 组）直接消费 WebSocket 的同一套事件契约。
 * 错误事件保留旧口径：先发 {@code error}，再补一条带 {@code error} 字段的 {@code done}，
 * 防止只监听 {@code done} 的老客户端一直转圈。</p>
 */
@Slf4j
@Component
public class ChatSseAdapter {

    public SseEmitter adapt(Flux<ChatStreamEvent> events) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<Disposable> ref = new AtomicReference<>();

        Disposable subscription = events.subscribe(
                event -> handle(emitter, event, closed),
                error -> {
                    if (closed.compareAndSet(false, true)) {
                        log.warn("SSE 流异常：{}", error.getMessage());
                        send(emitter, "error", ChatStreamEvent.payload(
                                "code", ChatStreamService.errorCodeOf(error),
                                "message", ChatStreamService.safeMessage(error)));
                        emitter.complete();
                    }
                },
                () -> {
                    if (closed.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                });
        ref.set(subscription);
        emitter.onCompletion(() -> disposeQuietly(ref.get()));
        emitter.onTimeout(() -> disposeQuietly(ref.get()));
        emitter.onError(e -> disposeQuietly(ref.get()));
        return emitter;
    }

    private void handle(SseEmitter emitter, ChatStreamEvent event, AtomicBoolean closed) {
        if (closed.get()) {
            return;
        }
        if (ChatStreamEvent.TYPE_ERROR.equals(event.type())) {
            if (closed.compareAndSet(false, true)) {
                Map<String, Object> data = new LinkedHashMap<>(event.data());
                send(emitter, "error", data);
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("messageId", data.get("messageId"));
                done.put("error", data.get("message"));
                send(emitter, "done", done);
                emitter.complete();
            }
            return;
        }
        LegacyEvent legacy = legacyOf(event);
        if (legacy == null) {
            return;
        }
        if (!send(emitter, legacy.name(), legacy.data())) {
            if (closed.compareAndSet(false, true)) {
                emitter.complete();
            }
            return;
        }
        if (ChatStreamEvent.TYPE_DONE.equals(event.type()) && closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    /** 事件契约 → 旧 SSE 事件名与负载；未知类型返回 null（向前兼容新事件）。 */
    static LegacyEvent legacyOf(ChatStreamEvent event) {
        return switch (event.type()) {
            case ChatStreamEvent.TYPE_META -> new LegacyEvent("meta", event.data());
            case ChatStreamEvent.TYPE_REFERENCES -> new LegacyEvent("references",
                    event.data().getOrDefault("items", java.util.List.of()));
            case ChatStreamEvent.TYPE_DELTA -> new LegacyEvent("delta",
                    ChatStreamEvent.payload("content", event.data().get("content")));
            case ChatStreamEvent.TYPE_DONE -> new LegacyEvent("done", event.data());
            default -> null;
        };
    }

    private boolean send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            log.debug("SSE 推送失败（客户端可能已断开）：{}", e.getMessage());
            return false;
        }
    }

    private Disposable disposeQuietly(Disposable disposable) {
        if (disposable != null) {
            disposable.dispose();
        }
        return disposable;
    }

    record LegacyEvent(String name, Object data) {
    }
}
