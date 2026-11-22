package com.group4.kaoyan.platform.realtime.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 单连接发送工具。
 *
 * <p>{@code WebSocketSession.sendMessage} <b>不是线程安全的</b>：心跳回包、业务推送、
 * 多端广播可能同时写同一条连接，不锁就会出现
 * {@code IllegalStateException: The remote endpoint was in state [TEXT_PARTIAL_WRITING]}。
 * 这里用 session 对象本身做锁，简单且不会死锁（发送是短操作）。</p>
 */
@Slf4j
public final class RealtimeSessionSender {

    private RealtimeSessionSender() {
    }

    /**
     * 发一条文本消息。
     *
     * @return true 发送成功；false 连接已关闭或写失败（调用方可以顺手摘掉注册表里的记录）
     */
    public static boolean send(WebSocketSession session, String payload) {
        if (session == null || payload == null || !session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("WebSocket 推送失败（连接 {} 可能已断开）：{}", session.getId(), e.getMessage());
            return false;
        }
    }
}
