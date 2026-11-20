package com.group4.kaoyan.modules.audit.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志（sys_login_log）。只增不改，Mapper 只有 insert。
 */
@Data
public class LoginLog {

    private Long id;
    private Long userId;
    private String account;
    /** 1 成功 / 0 失败 */
    private Integer success;
    private String ipAddress;
    private String userAgent;
    private String failureReason;
    private LocalDateTime createTime;
}
