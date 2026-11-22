package com.group4.kaoyan.platform.realtime.command;

import com.group4.kaoyan.platform.realtime.protocol.RealtimeEnvelope;

import java.util.Set;

/**
 * 业务命令处理器扩展点（B-1 只留接口，B-2 实现 {@code chat.send}）。
 *
 * <p>这样分层的目的：连接层（票据、注册表、心跳、协议校验）完全不依赖聊天业务，
 * 可以独立合并、独立测试；B-2 只要新增一个实现类并声明 {@code supportedTypes}，
 * 就被 {@code RealtimeChatHandler} 自动发现，不用回头改连接层一行代码。</p>
 */
public interface RealtimeCommandHandler {

    /** 本处理器负责的消息类型，例如 {@code Set.of("chat.send")}。 */
    Set<String> supportedTypes();

    /**
     * 处理一条已通过协议校验的消息。
     *
     * <p>注意：处理器跑在 WebSocket 容器的 IO 线程上，耗时操作（LLM 生成、查库）必须异步，
     * 否则会卡住同一容器里其他连接的心跳。</p>
     */
    void handle(RealtimeCommandContext context, RealtimeEnvelope envelope) throws Exception;
}
