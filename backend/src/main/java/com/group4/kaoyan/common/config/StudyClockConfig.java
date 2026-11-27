package com.group4.kaoyan.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 学习计划模块时间配置：统一使用 Asia/Shanghai。
 * 测试时可提供不同的 Clock Bean 覆盖。
 */
@Configuration
public class StudyClockConfig {

    public static final ZoneId ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock studyClock() {
        return Clock.system(ASIA_SHANGHAI);
    }
}
