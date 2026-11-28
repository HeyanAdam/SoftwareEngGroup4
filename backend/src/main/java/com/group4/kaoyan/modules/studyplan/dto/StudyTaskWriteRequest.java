package com.group4.kaoyan.modules.studyplan.dto;

import java.time.LocalDateTime;

/**
 * 手工创建或编辑今日任务。
 *
 * <p>状态仍由 PATCH /study-tasks/{id} 单独维护；本请求只修改任务内容，
 * 避免在编辑标题时意外触发“完成”推荐。</p>
 */
public record StudyTaskWriteRequest(
        String title,
        String subject,
        String knowledgePoint,
        Integer estimatedMinutes,
        LocalDateTime dueAt,
        Integer priority,
        Integer expectedVersion
) {
}
