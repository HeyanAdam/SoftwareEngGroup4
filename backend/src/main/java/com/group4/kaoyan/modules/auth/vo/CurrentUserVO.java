package com.group4.kaoyan.modules.auth.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * 当前用户响应（接口文档 3.4 / P1 任务 9）。邮箱只给脱敏值。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CurrentUserVO {

    private Long userId;
    private String username;
    private String nickname;
    private String emailMasked;
    private List<String> roles;
    private List<String> permissions;
    private boolean hasProfile;
}
