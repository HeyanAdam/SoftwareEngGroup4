package com.group4.kaoyan.modules.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 认证模块可配置项，默认值即接口文档/分工文档给出的口径，
 * 实际部署可通过 {@code kaoyan.auth.*} 或环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "kaoyan.auth")
public class AuthProperties {

    /** 图形验证码有效期，默认 5 分钟 */
    private Duration captchaTtl = Duration.ofMinutes(5);
    /** 同一图形验证码允许输错的次数，超限直接作废 */
    private int captchaMaxAttempts = 3;
    /** 邮箱验证码有效期，默认 5 分钟 */
    private Duration emailCodeTtl = Duration.ofMinutes(5);
    /** 同一邮箱同一用途的重发间隔，默认 60 秒 */
    private Duration emailCodeInterval = Duration.ofSeconds(60);
    /** 同一 IP 的重发间隔，默认 60 秒 */
    private Duration emailCodeIpInterval = Duration.ofSeconds(60);
    /** 同一邮箱验证码允许输错的次数，超限直接作废 */
    private int emailCodeMaxAttempts = 5;
    /** 连续登录失败多少次锁定账号，默认 5 次 */
    private int loginMaxFailures = 5;
    /** 锁定时长，默认 15 分钟 */
    private Duration loginLockDuration = Duration.ofMinutes(15);
    /** 失败计数的保留窗口，默认 30 分钟 */
    private Duration loginFailureWindow = Duration.ofMinutes(30);
    /** 邮箱验证码发件人 */
    private String mailFrom = "no-reply@kaoyan.local";
    /** 邮件主题前缀 */
    private String mailSubjectPrefix = "[考研AI导学系统]";
}
