package com.group4.kaoyan.modules.studyplan.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日学习计划 (study_plan)。一人一天一条,UNIQUE (user_id, plan_date)。
 */
@Data
public class StudyPlan implements TimedEntity {

    private Long id;
    private Long userId;
    private LocalDate planDate;
    private String title;
    /** ACTIVE / ARCHIVED */
    private String status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
