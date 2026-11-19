package com.group4.kaoyan.modules.user.dto;

/**
 * 画像读写参数。JSON 字段用 Object 承接，前端可传数组或对象。
 */
public record UserProfileDTO(
        Integer gender,
        String major,
        String grade,
        Integer baseLevel,
        Integer weeklyStudyMinutes,
        Object weakPoints,
        Object profile
) {
}
