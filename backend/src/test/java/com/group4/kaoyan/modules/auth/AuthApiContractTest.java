package com.group4.kaoyan.modules.auth;

import cn.dev33.satoken.exception.NotLoginException;
import com.group4.kaoyan.modules.auth.controller.AuthController;
import com.group4.kaoyan.modules.auth.service.AuthRateLimitService;
import com.group4.kaoyan.modules.auth.service.AuthService;
import com.group4.kaoyan.modules.auth.service.CaptchaService;
import com.group4.kaoyan.modules.auth.service.EmailCodeService;
import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.support.AuthExceptionHandler;
import com.group4.kaoyan.modules.auth.vo.CaptchaVO;
import com.group4.kaoyan.modules.auth.vo.LoginVO;
import com.group4.kaoyan.modules.auth.vo.RegisterVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口契约测试（P1 任务 12）。
 *
 * <p>用 standalone MockMvc 只跑 Controller + 认证异常处理器，Service 全部打桩，
 * 因此不依赖 MySQL / Redis / Mailpit，CI 里可以直接跑。校验的是「前端真正拿到的东西」：
 * HTTP 状态行、{@code {code,message,data}}、Retry-After 头、字段是否 snake_case。</p>
 *
 * <p>需要真实数据库的端到端流程见交接说明第 9 节的手工验证步骤。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthApiContractTest {

    @Mock
    private AuthService authService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private EmailCodeService emailCodeService;

    @Mock
    private AuthRateLimitService authRateLimitService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, captchaService, emailCodeService,
                authRateLimitService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthExceptionHandler())
                // 显式只装 JSON 转换器：工程里同时有 jackson-dataformat-xml，
                // 不指定时 MockMvc 会按默认顺序选中 XML
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        // 限流默认放行，个别用例再改成抛 429
        lenient().doNothing().when(authRateLimitService).require(anyString(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("GET /captcha 返回 snake_case 的 captcha_key 与 Base64 图片")
    void shouldReturnCaptcha() throws Exception {
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey("cap_0123456789abcdef");
        vo.setImageBase64("data:image/png;base64,AAAA");
        vo.setExpiresIn(300L);
        when(captchaService.generate()).thenReturn(vo);

        mvc.perform(get("/api/v1/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.captcha_key").value("cap_0123456789abcdef"))
                .andExpect(jsonPath("$.data.expires_in").value(300));
    }

    @Test
    @DisplayName("登录密码错误：401 + 10002")
    void shouldReturn401WhenBadCredentials() throws Exception {
        when(authService.login(any(), any())).thenThrow(AuthException.badCredentials());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_type\":\"PASSWORD\",\"account\":\"kaoyan_student\","
                                + "\"password\":\"wrong\",\"captcha_key\":\"cap_x\",\"captcha_code\":\"7k9p\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("账号锁定：423 + 10003 + Retry-After")
    void shouldReturn423WhenLocked() throws Exception {
        when(authService.login(any(), any())).thenThrow(AuthException.locked(540));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_type\":\"PASSWORD\",\"account\":\"kaoyan_student\","
                                + "\"password\":\"x\",\"captcha_key\":\"cap_x\",\"captcha_code\":\"7k9p\"}"))
                .andExpect(status().isLocked())
                .andExpect(header().string("Retry-After", "540"))
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("登录限流：429 + 10010 + Retry-After")
    void shouldReturn429WhenRateLimited() throws Exception {
        doThrow(AuthException.tooManyRequests("登录请求过于频繁", 42))
                .when(authRateLimitService).require(anyString(), anyString(), anyInt(), any(), anyString());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_type\":\"PASSWORD\",\"account\":\"kaoyan_student\","
                                + "\"password\":\"x\",\"captcha_key\":\"cap_x\",\"captcha_code\":\"7k9p\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.code").value(10010));
    }

    @Test
    @DisplayName("注册成功：201 + token_type/user_id 等字段 snake_case")
    void shouldReturn201WhenRegistered() throws Exception {
        RegisterVO vo = new RegisterVO();
        vo.setUserId(10001L);
        vo.setUsername("kaoyan_student");
        vo.setNickname("考研小白");
        vo.setToken("sa-token-value");
        vo.setTokenType("Bearer");
        when(authService.register(any(), anyString(), any())).thenReturn(vo);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "8f31c2a0-9b7e-4d21-a6c5-0e2f1a3b4c5d")
                        .content("{\"username\":\"kaoyan_student\",\"email\":\"student@example.com\","
                                + "\"verify_code\":\"482913\",\"password\":\"KaoYan@2026\","
                                + "\"confirm_password\":\"KaoYan@2026\",\"nickname\":\"考研小白\","
                                + "\"agree_agreement\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user_id").value(10001))
                .andExpect(jsonPath("$.data.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("注册缺 Idempotency-Key：400 + 10000")
    void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
        when(authService.register(any(), any(), any()))
                .thenThrow(AuthException.paramInvalid("缺少 Idempotency-Key 请求头"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kaoyan_student\",\"email\":\"student@example.com\","
                                + "\"verify_code\":\"482913\",\"password\":\"KaoYan@2026\","
                                + "\"confirm_password\":\"KaoYan@2026\",\"agree_agreement\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    @DisplayName("/me 未登录：401 + 10001")
    void shouldReturn401WhenNotLogin() throws Exception {
        doThrow(new NotLoginException("未提供 Token", "login", NotLoginException.NOT_TOKEN))
                .when(authService).currentUser();

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    @DisplayName("/me 已登录：返回角色与权限快照")
    void shouldReturnCurrentUserWithRoles() throws Exception {
        var vo = new com.group4.kaoyan.modules.auth.vo.CurrentUserVO();
        vo.setUserId(10001L);
        vo.setUsername("kaoyan_student");
        vo.setEmailMasked("stu***@example.com");
        vo.setRoles(List.of("STUDENT"));
        vo.setPermissions(List.of("chat:send"));
        vo.setHasProfile(false);
        when(authService.currentUser()).thenReturn(vo);

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(10001))
                .andExpect(jsonPath("$.data.email_masked").value("stu***@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.permissions[0]").value("chat:send"))
                .andExpect(jsonPath("$.data.has_profile").value(false));
    }

    @Test
    @DisplayName("退出登录：code=0 且带中文提示")
    void shouldLogout() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("退出成功"));
    }
}
