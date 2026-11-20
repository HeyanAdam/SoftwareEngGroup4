package com.group4.kaoyan.modules.auth.service;

import com.group4.kaoyan.modules.auth.support.EmailCodePurpose;

/**
 * 验证码邮件发送端口。生产实现走 Spring Mail（本地连 Mailpit），
 * 保留接口是为了测试时可以替换成不真正发信的实现。
 */
public interface EmailSender {

    void sendVerificationCode(String email, String code, EmailCodePurpose purpose);
}
