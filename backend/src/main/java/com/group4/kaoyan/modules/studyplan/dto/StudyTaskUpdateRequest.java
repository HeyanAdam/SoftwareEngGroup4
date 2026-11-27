package com.group4.kaoyan.modules.studyplan.dto;

/**
 * 修改任务状态请求体。对应契约 §5.3 PATCH /api/v1/study-tasks/{id}.
 */
public record StudyTaskUpdateRequest(
        String status,
        Integer expectedVersion) {

    public StudyTaskUpdateRequest {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (!status.equals("TODO") && !status.equals("IN_PROGRESS") && !status.equals("COMPLETED")) {
            throw new IllegalArgumentException("status 必须是 TODO / IN_PROGRESS / COMPLETED 之一");
        }
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion 必须 >= 0");
        }
    }
}
