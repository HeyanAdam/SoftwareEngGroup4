package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;

/**
 * 流式生命周期扩展点：A-1 的 HTTP 路径用默认空实现（结束时一次性落库）；
 * A-2 的 MQ 消费者用它把回答提前建成 {@code STREAMING}、持续更新并写 Redis Stream。
 *
 * <p>所有方法都在流订阅线程调用，实现方自行保证异常不影响主流程（除显式抛出的持久化失败）。</p>
 */
public interface ChatStreamLifecycle {

    ChatStreamLifecycle NOOP = new ChatStreamLifecycle() {
    };

    default void onStarted(ChatConversation conversation, ChatMessage userMessage, String streamId) {
    }

    default void onEvent(ChatStreamEvent event) {
    }

    /**
     * 持久化最终回答。返回 {@code null} 表示由 {@code ChatStreamService} 走默认落库；
     * 返回非空表示实现方已经自己写完（例如更新预建的 STREAMING 消息）。
     */
    default ChatMessage persistAssistant(ChatConversation conversation,
                                         Long userId,
                                         String content,
                                         String referencesJson,
                                         String modelName,
                                         String streamId,
                                         String status,
                                         String errorMessage,
                                         long lastSequence) {
        return null;
    }
}
