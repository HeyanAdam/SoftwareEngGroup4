package com.group4.kaoyan.modules.studyplan.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习任务 (study_task)。状态只存 TODO/IN_PROGRESS/COMPLETED/CANCELLED,
 * 不存 OVERDUE —— "逾期"由服务端计算返回,见契约 §3.1。
 */
@Data
public class StudyTask implements TimedEntity {

    private Long id;
    private Long planId;
    private Long userId;
    private LocalDate planDate;
    private String title;
    private String subject;
    private String knowledgePoint;
    private Integer estimatedMinutes;
    private LocalDateTime dueAt;
    /** TODO / IN_PROGRESS / COMPLETED / CANCELLED */
    private String status;
    private Integer priority;
    private Integer version;
    private LocalDateTime completedAt;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
