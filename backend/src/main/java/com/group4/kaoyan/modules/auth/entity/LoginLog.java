package com.group4.kaoyan.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志（sys_login_log，Flyway V3 已建表）。
 * 只记录账号、结果、IP、UA 和失败原因，禁止写入密码、验证码和 Token。
 */
@Data
@TableName("sys_login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录成功时才有值；账号不存在时为 null */
    private Long userId;

    private String account;

    /** 0 失败 1 成功 */
    private Integer success;

    private String ipAddress;

    private String userAgent;

    private String failureReason;

    private LocalDateTime createTime;
}
