package com.group4.kaoyan.modules.auth.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 注册成功响应（接口文档 3.2）。注册即建立登录态，因此一起返回 token。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RegisterVO {

    private Long userId;
    private String username;
    private String nickname;
    private String token;
    private String tokenType = "Bearer";
}
