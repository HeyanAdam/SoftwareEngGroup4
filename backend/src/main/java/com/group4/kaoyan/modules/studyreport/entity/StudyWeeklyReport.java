package com.group4.kaoyan.modules.studyreport.entity;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户每周学习诊断。
 */
@Data
public class StudyWeeklyReport implements TimedEntity {

    private Long id;
    private Long userId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private BigDecimal completionRate;
    private Integer studyMinutes;
    private Integer chatQuestions;
    private List<String> weakSubjects;
    private List<String> nextActions;
    private String summary;
    private LocalDateTime generatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
