package com.group4.kaoyan.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_STREAMING = "STREAMING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long userId;

    /** user / assistant / system */
    private String role;

    private String content;

    /** 引用片段的 JSON，便于前端展示来源 */
    private String referencesJson;

    /** 客户端幂等键，仅用户消息有值；唯一约束 (conversation_id, user_id, client_message_id) */
    private String clientMessageId;

    /** AI 回答对应的用户消息 ID */
    private Long replyToId;

    /** 一次回答的稳定流 ID */
    private String streamId;

    /** PENDING / STREAMING / COMPLETED / FAILED */
    private String status;

    /** 已落库的最后事件序号，断线恢复游标 */
    private Long lastSequence;

    private String errorMessage;

    private LocalDateTime completedAt;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private LocalDateTime createTime;
}
