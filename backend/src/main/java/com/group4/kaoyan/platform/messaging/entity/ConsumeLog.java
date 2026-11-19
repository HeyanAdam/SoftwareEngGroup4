package com.group4.kaoyan.platform.messaging.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消费幂等记录（sys_message_consume_log）。唯一键 (message_id, consumer_name)。
 */
@Data
public class ConsumeLog implements TimedEntity {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    private Long id;
    private String messageId;
    private String consumerName;
    private String bizKey;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
