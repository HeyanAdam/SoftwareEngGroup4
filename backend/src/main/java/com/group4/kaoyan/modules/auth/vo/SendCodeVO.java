package com.group4.kaoyan.modules.auth.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 发送邮箱验证码响应（接口文档 3.1）。验证码本身永不出现在响应里。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SendCodeVO {

    /** 验证码有效期（秒） */
    private long expiresIn;
    /** 下次允许重发的等待秒数 */
    private long retryAfter;
}
