package com.group4.kaoyan.modules.recommendation.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户推荐 (user_recommendation)。去重键 dedup_key UNIQUE INDEX 兜底并发。
 */
@Data
public class UserRecommendation {

    private Long id;
    private Long userId;
    /** TODAY / CHAT / TASK_COMPLETE */
    private String scene;
    private String contextType;
    private Long contextId;
    private String type;
    private String title;
    private String reason;
    /** CREATE_TASK / OPEN_CHAT / OPEN_RESOURCE */
    private String actionType;
    private String actionLabel;
    private String actionPath;
    private BigDecimal score;
    private Integer estimatedMinutes;
    private String subject;
    private String knowledgePoint;
    /** ACTIVE / ACCEPTED / DISMISSED / EXPIRED */
    private String status;
    private Long acceptedTaskId;
    private String dedupKey;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
