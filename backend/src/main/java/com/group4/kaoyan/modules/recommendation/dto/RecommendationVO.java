package com.group4.kaoyan.modules.recommendation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 推荐返回 VO。对应契约 §4.3 Recommendation.
 * createdAt/expiresAt 用 OffsetDateTime,默认序列化为带 +08:00 的 ISO 字符串。
 */
public record RecommendationVO(
        Long id,
        String scene,
        String type,
        String title,
        String reason,
        String actionType,
        String actionLabel,
        String actionPath,
        BigDecimal score,
        Integer estimatedMinutes,
        String subject,
        String knowledgePoint,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt) {
}
