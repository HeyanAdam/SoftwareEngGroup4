package com.group4.kaoyan.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.security.AccountStatusGuard;
import com.group4.kaoyan.platform.idempotency.IdempotencyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;
    private final AccountStatusGuard accountStatusGuard;

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

    /**
     * 除白名单外，/api/** 一律先校验登录态，再由方法上的 @SaCheckPermission 校验权限码。
     *
     * <p>注意：不要再往这里加「业务路径放行」（历史上的 {@code /api/kb/**} 放行已删除）——
     * 匿名可访问的接口只能由 {@link #PUBLIC_PATHS} 显式声明，否则新增接口忘加注解就等于裸奔。</p>
     *
     * <p>登录校验之后紧跟一次账号状态兜底：Sa-Token 的登录态存在 Redis 里、之后不再查库，
     * 单靠 {@code kickout} 无法覆盖多端会话或踢人失手的情况，所以每次请求确认
     * {@code status != 0}，详见 {@link AccountStatusGuard}。</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> SaRouter.match("/api/**")
                        .notMatch(PUBLIC_PATHS)
                        .check(r -> {
                            StpUtil.checkLogin();
                            accountStatusGuard.ensureActive(StpUtil.getLoginIdAsLong());
                        })))
                .addPathPatterns("/**");
        // 幂等拦截器排在登录校验之后，只对 @Idempotent 方法生效
        registry.addInterceptor(idempotencyInterceptor).addPathPatterns("/api/**");
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
