package com.group4.kaoyan.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 跨域：生产环境请把 allowedOrigins 收敛到实际前端域名 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 匿名可访问的接口白名单。
     * 认证模块只开放「验证码、注册、登录、重置密码」，/auth/me 与 /auth/logout 仍需登录。
     * 健康检查保持公开（接口文档第 14 章）。
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/captcha",
            "/api/v1/auth/verification-codes",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/password-reset",
            "/api/v1/health/**"
    };

    /** 除白名单与健康检查接口外，/api/** 均需登录 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> SaRouter.match("/api/**")
                        .notMatch(PUBLIC_PATHS)
                        .notMatch("/api/kb", "/api/kb/**")
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/**");
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** 文档解析/向量化专用线程池 */
    @Bean("documentExecutor")
    public ThreadPoolTaskExecutor documentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-proc-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
