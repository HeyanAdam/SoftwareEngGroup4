package com.group4.kaoyan.modules.auth.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * 登录成功响应（接口文档 3.3）。字段顺序与文档一致，前端按 snake_case 读取。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LoginVO {

    private String token;
    private String tokenType = "Bearer";
    /** Token 有效期（秒），来自 sa-token.timeout */
    private long expiresIn;
    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
    private List<String> permissions;
    private boolean hasProfile;
}
