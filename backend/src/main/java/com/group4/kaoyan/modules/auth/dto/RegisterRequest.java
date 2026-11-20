package com.group4.kaoyan.modules.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求（接口文档 3.2）。要求请求头 {@code Idempotency-Key}。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RegisterRequest {

    @NotBlank(message = "不能为空")
    private String username;

    @NotBlank(message = "不能为空")
    @Email(message = "格式不正确")
    private String email;

    @NotBlank(message = "不能为空")
    private String verifyCode;

    @NotBlank(message = "不能为空")
    private String password;

    @NotBlank(message = "不能为空")
    private String confirmPassword;

    @Size(max = 64, message = "长度不能超过 64 位")
    private String nickname;

    @NotNull(message = "必须同意用户协议")
    private Boolean agreeAgreement;
}
