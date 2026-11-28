package com.group4.kaoyan.modules.recommendation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 推荐列表返回 VO。对应契约 §4.4 RecommendationList.
 */
public record RecommendationListVO(
        List<RecommendationVO> items,
        OffsetDateTime generatedAt) {
}
