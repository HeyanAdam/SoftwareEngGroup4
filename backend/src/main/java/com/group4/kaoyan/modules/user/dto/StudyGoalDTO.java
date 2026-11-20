package com.group4.kaoyan.modules.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 备考目标读写参数。
 */
public record StudyGoalDTO(
        Long targetSchoolId,
        String targetSchoolName,
        String targetMajor,
        BigDecimal targetScore,
        Object targetSubjectScores,
        Integer examYear,
        LocalDate examDate,
        Integer planType,
        Integer status,
        String remark
) {
}
