package com.group4.kaoyan.modules.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送邮箱验证码请求（接口文档 3.1）。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SendCodeRequest {

    @NotBlank(message = "不能为空")
    @Email(message = "格式不正确")
    @Size(max = 128, message = "长度不能超过 128 位")
    private String email;

    /** REGISTER、EMAIL_LOGIN、RESET_PASSWORD */
    @NotBlank(message = "不能为空")
    private String purpose;
}
