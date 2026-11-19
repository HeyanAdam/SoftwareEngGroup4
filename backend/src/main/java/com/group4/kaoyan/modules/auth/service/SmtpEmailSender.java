package com.group4.kaoyan.modules.auth.service;

import com.group4.kaoyan.modules.auth.config.AuthProperties;
import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.support.EmailCodePurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Spring Mail 实现：本地连接 Mailpit（localhost:1025），开发环境直接在
 * http://localhost:8025 收信查看验证码。
 *
 * <p>日志脱敏要求：这里只记录邮箱和用途，绝不记录验证码本身。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AuthProperties authProperties;

    @Override
    public void sendVerificationCode(String email, String code, EmailCodePurpose purpose) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.error("未配置 spring.mail，验证码邮件无法发送: email={}, purpose={}", email, purpose);
            throw AuthException.serviceUnavailable("邮件服务暂不可用，请稍后重试");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(authProperties.getMailFrom());
        message.setTo(email);
        message.setSubject(authProperties.getMailSubjectPrefix() + purpose.subjectAction() + "验证码");
        message.setText(buildText(code, purpose));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("验证码邮件发送失败: email={}, purpose={}, reason={}", email, purpose, e.getMessage());
            throw AuthException.serviceUnavailable("验证码邮件发送失败，请稍后重试");
        }
        log.info("验证码邮件已发送: email={}, purpose={}", email, purpose);
    }

    private String buildText(String code, EmailCodePurpose purpose) {
        return "你好：\n\n"
                + "你正在" + purpose.description() + "，验证码为：\n\n"
                + "    " + code + "\n\n"
                + "验证码 " + (authProperties.getEmailCodeTtl().toSeconds() / 60) + " 分钟内有效，且只能使用一次。\n"
                + "如果这不是你本人的操作，请忽略本邮件。\n\n"
                + "考研 AI 导学系统";
    }
}
