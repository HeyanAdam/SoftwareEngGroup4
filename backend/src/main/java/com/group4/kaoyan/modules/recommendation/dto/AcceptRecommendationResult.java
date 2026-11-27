package com.group4.kaoyan.modules.recommendation.dto;

import com.group4.kaoyan.modules.studyplan.dto.StudyTaskVO;

/**
 * 接受推荐返回。对应契约 §4.5 AcceptRecommendationResult.
 */
public record AcceptRecommendationResult(
        RecommendationVO recommendation,
        StudyTaskVO task) {
}
