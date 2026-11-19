package com.group4.kaoyan.modules.auth.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 图形验证码响应。answer 永不返回，只在 Redis 中保存。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaptchaVO {

    private String captchaKey;
    /** data:image/png;base64,... 前端可直接放进 img.src */
    private String imageBase64;
    private long expiresIn;
}
