package com.group4.kaoyan.modules.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重置密码请求（接口文档 3.6）。要求请求头 {@code Idempotency-Key}。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PasswordResetRequest {

    @NotBlank(message = "不能为空")
    @Email(message = "格式不正确")
    private String email;

    @NotBlank(message = "不能为空")
    private String verifyCode;

    @NotBlank(message = "不能为空")
    private String newPassword;

    @NotBlank(message = "不能为空")
    private String confirmPassword;
}
