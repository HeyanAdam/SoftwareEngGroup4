package com.group4.kaoyan.platform.messaging.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息发送 Outbox（sys_message_outbox）。业务写库与它同一本地事务，由 Job 异步投递。
 */
@Data
public class MessageOutbox implements TimedEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHING = "PUBLISHING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_RETRY_WAIT = "RETRY_WAIT";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    /** 作为消息 messageId，消费者据此幂等 */
    private String eventId;
    private String exchangeName;
    private String routingKey;
    private String messageType;
    private String bizKey;
    private Object payloadJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
