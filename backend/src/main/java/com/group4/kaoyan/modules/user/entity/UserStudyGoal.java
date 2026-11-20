package com.group4.kaoyan.modules.user.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 备考目标（user_study_goal）。同一年度同一用户只能有一条有效目标（uk_goal_user_year）。
 */
@Data
public class UserStudyGoal implements TimedEntity {

    private Long id;
    private Long userId;
    private Long targetSchoolId;
    private String targetSchoolName;
    private String targetMajor;
    private BigDecimal targetScore;
    /** JSON 列：单科目标分，如 {"english":70,"math":110} */
    private Object targetSubjectScores;
    private Integer examYear;
    private LocalDate examDate;
    /** 计划类型：1 常规 / 2 冲刺 / 3 在职 */
    private Integer planType;
    /** 状态：1 进行中 / 2 已达成 / 3 已放弃 */
    private Integer status;
    private String remark;
    private Integer deleted;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
