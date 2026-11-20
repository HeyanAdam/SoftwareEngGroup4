package com.group4.kaoyan.modules.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 登录请求（接口文档 3.3）。两种方式共用一个请求体：
 * <pre>
 * PASSWORD    ：account + password + captcha_key + captcha_code
 * EMAIL_CODE  ：email + verify_code
 * </pre>
 * 字段按 login_type 分支必填，因此不使用注解校验，由 AuthService 逐分支校验并返回 10000。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LoginRequest {

    /** PASSWORD、EMAIL_CODE */
    private String loginType;

    private String account;

    private String password;

    private String captchaKey;

    private String captchaCode;

    private String email;

    private String verifyCode;
}
