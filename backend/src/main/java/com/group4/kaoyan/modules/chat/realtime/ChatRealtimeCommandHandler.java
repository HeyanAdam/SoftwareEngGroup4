package com.group4.kaoyan.modules.chat.realtime;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.security.AccountStatusGuard;
import com.group4.kaoyan.common.security.PermissionConstants;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.chat.service.ChatService;
import com.group4.kaoyan.modules.chat.stream.AiRequestContext;
import com.group4.kaoyan.modules.chat.stream.PersistentChatLifecycle;
import com.group4.kaoyan.modules.chat.stream.ChatStreamEvent;
import com.group4.kaoyan.modules.chat.stream.ChatStreamService;
import com.group4.kaoyan.modules.chat.stream.ChatStreamStore;
import com.group4.kaoyan.platform.realtime.command.RealtimeCommandContext;
import com.group4.kaoyan.platform.realtime.command.RealtimeCommandHandler;
import com.group4.kaoyan.platform.realtime.config.RealtimeProperties;
import com.group4.kaoyan.platform.realtime.delivery.RealtimeOutboundMessage;
import com.group4.kaoyan.platform.realtime.limit.RealtimeChatRateLimiter;
import com.group4.kaoyan.platform.realtime.limit.RealtimeGenerationTracker;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeEnvelope;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeErrorCode;
import com.group4.kaoyan.platform.realtime.protocol.RealtimeProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * B-2：把 WebSocket 的 {@code chat.send} 接到 A-1 的统一流式问答核心。
 *
 * <p>命令处理放到 boundedElastic，WebSocket IO 线程只负责收帧；ack 只回发起连接，
 * 后续聊天事件广播给该用户全部在线连接。订阅不会因为某一个标签页关闭而取消，
 * 因此其他标签页仍能收到回答，服务端也会继续完成持久化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeCommandHandler implements RealtimeCommandHandler {

    private final ChatStreamService chatStreamService;
    private final ChatService chatService;
    private final ChatStreamStore chatStreamStore;
    private final RbacSnapshotCache rbacSnapshotCache;
    private final AccountStatusGuard accountStatusGuard;
    private final RealtimeChatRateLimiter rateLimiter;
    private final RealtimeGenerationTracker generationTracker;
    private final RealtimeProperties properties;

    @Override
    public Set<String> supportedTypes() {
        return Set.of(RealtimeProtocol.TYPE_CHAT_SEND, RealtimeProtocol.TYPE_CHAT_CANCEL);
    }

    @Override
    public void handle(RealtimeCommandContext context, RealtimeEnvelope envelope) {
        Schedulers.boundedElastic().schedule(() -> {
            if (RealtimeProtocol.TYPE_CHAT_CANCEL.equals(envelope.type())) {
                processCancel(context, envelope);
            } else {
                process(context, envelope);
            }
        });
    }

    private void process(RealtimeCommandContext context, RealtimeEnvelope envelope) {
        String requestId = envelope.requestId();
        try {
            accountStatusGuard.ensureActive(context.userId());

            RbacSnapshotCache.Snapshot snapshot = rbacSnapshotCache.get(context.userId());
            if (!snapshot.permissions().contains(PermissionConstants.CHAT_SEND)) {
                context.replyError(RealtimeErrorCode.FORBIDDEN, requestId, "缺少 chat:send 权限");
                return;
            }
            if (!rateLimiter.allow(context.userId(), properties.getMaxSendsPerWindow(),
                    properties.getSendRateWindow())) {
                context.replyError(RealtimeErrorCode.RATE_LIMITED, requestId, "聊天请求过于频繁");
                return;
            }
            if (!generationTracker.tryAcquire(context.userId(), context.connectionId(),
                    properties.getMaxConcurrentGenerationsPerUser(),
                    properties.getMaxConcurrentGenerationsPerConnection())) {
                context.replyError(RealtimeErrorCode.RATE_LIMITED, requestId, "同时生成的回答过多");
                return;
            }
        } catch (BizException e) {
            context.replyError(RealtimeErrorCode.FORBIDDEN, requestId, e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("WebSocket 聊天前置校验失败 requestId={}：{}", requestId, e.getMessage());
            context.replyError(RealtimeErrorCode.INTERNAL_ERROR, requestId, "聊天服务暂时不可用");
            return;
        }

        String content = envelope.text("content");
        String clientMessageId = envelope.clientMessageId();
        Long conversationId = envelope.number("conversationId");
        Long kbId = envelope.number("kbId");

        try {
            ChatConversation conversation = chatService.getOrCreate(
                    context.userId(), conversationId, kbId, content);
            String streamId = UUID.randomUUID().toString();
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("conversationId", conversation.getId());
            ack.put("streamId", streamId);
            ack.put("clientMessageId", clientMessageId);
            context.reply(RealtimeProtocol.TYPE_ACK, requestId, ack);

            RbacSnapshotCache.Snapshot snapshot = rbacSnapshotCache.get(context.userId());
            boolean admin = snapshot.roles().contains(RoleConstants.SYSTEM_ADMIN);
            AiRequestContext aiContext = AiRequestContext.forBackground(
                    context.userId(),
                    conversation.getId(),
                    kbId,
                    admin,
                    Set.copyOf(snapshot.roles()),
                    requestId,
                    streamId,
                    clientMessageId);

            PersistentChatLifecycle lifecycle = new PersistentChatLifecycle(
                    chatService, chatStreamStore, context.userId());
            chatStreamService.stream(aiContext, content, lifecycle)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            event -> broadcast(context, event),
                            error -> {
                                log.warn("WebSocket 聊天流异常 requestId={}：{}", requestId, error.getMessage());
                                broadcastInternalError(context, requestId);
                                release(context);
                            },
                            () -> release(context));
        } catch (BizException e) {
            release(context);
            context.replyError(RealtimeErrorCode.FORBIDDEN, requestId, e.getMessage());
        } catch (Exception e) {
            release(context);
            log.error("WebSocket 聊天启动失败 requestId={}", requestId, e);
            context.replyError(RealtimeErrorCode.INTERNAL_ERROR, requestId, "聊天服务暂时不可用");
        }
    }

    private void processCancel(RealtimeCommandContext context, RealtimeEnvelope envelope) {
        String requestId = envelope.requestId();
        try {
            accountStatusGuard.ensureActive(context.userId());
            RbacSnapshotCache.Snapshot snapshot = rbacSnapshotCache.get(context.userId());
            if (!snapshot.permissions().contains(PermissionConstants.CHAT_SEND)) {
                context.replyError(RealtimeErrorCode.FORBIDDEN, requestId, "缺少 chat:send 权限");
                return;
            }
        } catch (BizException e) {
            context.replyError(RealtimeErrorCode.FORBIDDEN, requestId, e.getMessage());
            return;
        } catch (Exception e) {
            context.replyError(RealtimeErrorCode.INTERNAL_ERROR, requestId, "取消服务暂时不可用");
            return;
        }

        String streamId = envelope.text("streamId");
        if (chatStreamService.cancel(context.userId(), streamId)) {
            return;
        }

        ChatMessage assistant = chatService.findAssistantByStreamId(streamId);
        if (assistant == null || !context.userId().equals(assistant.getUserId())) {
            context.replyError(RealtimeErrorCode.FORBIDDEN, requestId, "流不存在或无权取消");
            return;
        }

        String finishReason = switch (String.valueOf(assistant.getStatus())) {
            case ChatMessage.STATUS_CANCELLED -> "cancelled";
            case ChatMessage.STATUS_FAILED -> "failed";
            default -> "stop";
        };
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("messageId", assistant.getId());
        done.put("finishReason", finishReason);
        done.put("partialChars", assistant.getContent() == null ? 0 : assistant.getContent().length());
        done.put("lastSequence", assistant.getLastSequence());
        done.put("alreadyFinished", true);
        context.reply(RealtimeProtocol.TYPE_DONE, requestId, done);
    }

    private void broadcast(RealtimeCommandContext context, ChatStreamEvent event) {
        context.delivery().pushToUser(context.userId(), RealtimeOutboundMessage.stream(
                event.type(),
                event.requestId(),
                event.streamId(),
                event.sequence(),
                event.serverTime(),
                event.data()));
    }

    private void broadcastInternalError(RealtimeCommandContext context, String requestId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", RealtimeErrorCode.INTERNAL_ERROR.getCode());
        data.put("reason", RealtimeErrorCode.INTERNAL_ERROR.getReason());
        data.put("retryable", RealtimeErrorCode.INTERNAL_ERROR.isRetryable());
        context.delivery().pushToUser(context.userId(), RealtimeOutboundMessage.of(
                RealtimeProtocol.TYPE_ERROR, requestId, data));
    }

    private void release(RealtimeCommandContext context) {
        generationTracker.release(context.userId(), context.connectionId());
    }
}
