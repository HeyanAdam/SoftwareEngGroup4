package com.group4.kaoyan.platform.messaging.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步任务（sys_async_task）。对外映射：PENDING/RETRY_WAIT → QUEUED，RUNNING → PROCESSING。
 */
@Data
public class AsyncTask implements TimedEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_RETRY_WAIT = "RETRY_WAIT";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String TYPE_DOC_PARSE = "DOC_PARSE";
    public static final String BIZ_DOCUMENT = "DOCUMENT";

    private Long id;
    private String taskNo;
    private String taskType;
    private String bizType;
    private Long bizId;
    private Long userId;
    private String status;
    private Integer progress;
    private Integer retryCount;
    private Object payloadJson;
    private Object resultJson;
    private String errorMessage;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishedTime;
}
