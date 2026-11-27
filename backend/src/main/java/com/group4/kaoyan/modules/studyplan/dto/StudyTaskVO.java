package com.group4.kaoyan.modules.studyplan.dto;

import java.time.OffsetDateTime;

/**
 * 学习任务返回 VO。overdue 字段由服务端计算,对应契约 §4.1 StudyTask.
 * dueAt 用 OffsetDateTime,Jackson 默认序列化为带 +08:00 的 ISO 字符串。
 */
public record StudyTaskVO(
        Long id,
        String title,
        String subject,
        String knowledgePoint,
        Integer estimatedMinutes,
        OffsetDateTime dueAt,
        String status,
        boolean overdue,
        Integer priority,
        Integer version) {
}
