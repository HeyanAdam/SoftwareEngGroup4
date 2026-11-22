package com.group4.kaoyan.dto;

import lombok.Data;

import java.util.Set;

/**
 * 聊天回答异步任务消息体（Outbox → kaoyan.chat.answer.queue）。
 *
 * <p>携带完整请求上下文（用户、会话、知识库、管理员视角、权限角色），
 * 消费者不再依赖 HTTP 登录态即可重建 {@code AiRequestContext}。</p>
 */
@Data
public class ChatAnswerTaskDTO {

    private Long userId;
    private Long conversationId;
    private Long kbId;
    private String question;
    private String requestId;
    private String streamId;
    private String clientMessageId;
    private boolean adminScope;
    private Set<String> roles;
    /** 用户消息 ID，消费者据此把 PENDING 收口并建立 reply_to 关系 */
    private Long userMessageId;
}
