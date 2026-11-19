package com.group4.kaoyan.modules.user.dto;

/**
 * 画像读写参数（对齐《接口设计文档 V1.1 适配版》4.2 节 PUT /api/v1/user/profile）。
 *
 * <p>{@code nickname / email / avatarUrl} 属于账号字段（sys_user），由同一个接口一并更新；
 * 其余字段写入 user_profile。{@code weakPoints / profile} 用 Object 承接，前端可传数组或对象。</p>
 */
public record UserProfileDTO(
        Integer gender,
        String major,
        String grade,
        Integer baseLevel,
        Integer weeklyStudyMinutes,
        Object weakPoints,
        Object profile,
        String nickname,
        String email,
        String avatarUrl
) {
}
