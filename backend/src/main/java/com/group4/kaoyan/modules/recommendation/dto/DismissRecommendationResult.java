package com.group4.kaoyan.modules.recommendation.dto;

/**
 * 忽略推荐返回。
 */
public record DismissRecommendationResult(
        Long recommendationId,
        String status) {
}
