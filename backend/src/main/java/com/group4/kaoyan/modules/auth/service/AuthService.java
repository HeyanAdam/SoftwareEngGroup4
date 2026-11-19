package com.group4.kaoyan.modules.auth.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.auth.dto.LoginRequest;
import com.group4.kaoyan.modules.auth.dto.PasswordResetRequest;
import com.group4.kaoyan.modules.auth.dto.RegisterRequest;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.auth.service.IdempotencyService.Guard;
import com.group4.kaoyan.modules.auth.support.AuthException;
import com.group4.kaoyan.modules.auth.support.AuthTexts;
import com.group4.kaoyan.modules.auth.support.ClientRequest;
import com.group4.kaoyan.modules.auth.support.EmailCodePurpose;
import com.group4.kaoyan.modules.auth.support.EmailMasker;
import com.group4.kaoyan.modules.auth.support.LoginType;
import com.group4.kaoyan.modules.auth.support.PasswordPolicy;
import com.group4.kaoyan.modules.auth.vo.CurrentUserVO;
import com.group4.kaoyan.modules.auth.vo.LoginVO;
import com.group4.kaoyan.modules.auth.vo.RegisterVO;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import com.group4.kaoyan.modules.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 认证核心服务：账号密码登录、邮箱验证码登录、注册、重置密码、当前用户、退出登录。
 *
 * <p>登录失败一律返回 10002「账号或密码错误」，不区分账号不存在与密码错误；
 * 账号锁定返回 423 + 10003；验证码相关返回 10005。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 匿名接口（注册、重置密码）在幂等表里用 0 占位 user_id */
    private static final long ANONYMOUS_USER_ID = 0L;
    private static final String ENDPOINT_REGISTER = "/api/v1/auth/register";
    private static final String ENDPOINT_PASSWORD_RESET = "/api/v1/auth/password-reset";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserProfileMapper userProfileMapper;
    private final CaptchaService captchaService;
    private final EmailCodeService emailCodeService;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final LoginLogService loginLogService;
    private final IdempotencyService idempotencyService;
    /** 权限快照缓存：与 StpInterfaceImpl 共用，保证「登录返回的角色权限」和「注解鉴权」口径一致 */
    private final RbacSnapshotCache rbacSnapshotCache;
    private final TransactionTemplate transactionTemplate;

    // ==================== 登录 ====================

    public LoginVO login(LoginRequest request, ClientRequest client) {
        LoginType loginType = LoginType.parse(request.getLoginType());
        return switch (loginType) {
            case PASSWORD -> loginByPassword(request, client);
            case EMAIL_CODE -> loginByEmailCode(request, client);
        };
    }

    private LoginVO loginByPassword(LoginRequest request, ClientRequest client) {
        String account = AuthTexts.requireText(request.getAccount(), "账号不能为空");
        String password = AuthTexts.requireText(request.getPassword(), "密码不能为空");

        long lockedSeconds = loginAttemptService.lockedSeconds(account);
        if (lockedSeconds > 0) {
            loginLogService.record(null, account, false, client, "账号锁定中");
            throw AuthException.locked(lockedSeconds);
        }

        // 校验图形验证码（一次性，输错计数在 CaptchaService 内处理）
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());

        SysUser user = findByAccount(account);
        if (user == null) {
            loginAttemptService.onFailure(account, client.ipKey());
            loginLogService.record(null, account, false, client, "账号不存在");
            log.info("登录失败：账号不存在, account={}, ip={}", account, client.ip());
            throw AuthException.badCredentials();
        }
        if (isDisabled(user)) {
            loginLogService.record(user.getId(), account, false, client, "账号已禁用");
            throw AuthException.disabled();
        }
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            int failures = loginAttemptService.onFailure(account, client.ipKey());
            loginLogService.record(user.getId(), account, false, client, "密码错误");
            log.info("登录失败：密码错误, account={}, ip={}, failures={}", account, client.ip(), failures);
            throw AuthException.badCredentials();
        }

        loginAttemptService.onSuccess(account, client.ipKey());
        return issueToken(user, client);
    }

    private LoginVO loginByEmailCode(LoginRequest request, ClientRequest client) {
        String email = AuthTexts.normalizeEmail(request.getEmail());
        String verifyCode = AuthTexts.requireText(request.getVerifyCode(), "验证码不能为空");

        emailCodeService.consume(email, EmailCodePurpose.EMAIL_LOGIN, verifyCode);

        SysUser user = findByEmail(email);
        if (user == null) {
            loginLogService.record(null, email, false, client, "邮箱未注册");
            throw AuthException.userNotFound("用户不存在");
        }
        if (isDisabled(user)) {
            loginLogService.record(user.getId(), email, false, client, "账号已禁用");
            throw AuthException.disabled();
        }
        return issueToken(user, client);
    }

    /** 登录成功：签发 Sa-Token，返回角色与权限快照，并写入登录日志。 */
    private LoginVO issueToken(SysUser user, ClientRequest client) {
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(SaManager.getConfig().getTimeout());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        RbacSnapshotCache.Snapshot snapshot = rbacSnapshotCache.get(user.getId());
        vo.setRoles(snapshot.roles());
        vo.setPermissions(snapshot.permissions());
        vo.setHasProfile(userProfileMapper.countByUserId(user.getId()) > 0);

        loginLogService.record(user.getId(), user.getUsername(), true, client, null);
        return vo;
    }

    // ==================== 注册 ====================

    public RegisterVO register(RegisterRequest request, String idempotencyKey, ClientRequest client) {
        if (!Boolean.TRUE.equals(request.getAgreeAgreement())) {
            throw AuthException.paramInvalid("请先阅读并同意用户协议");
        }
        String username = AuthTexts.normalizeUsername(request.getUsername());
        String email = AuthTexts.normalizeEmail(request.getEmail());
        PasswordPolicy.validateMatch(request.getPassword(), request.getConfirmPassword());
        String verifyCode = AuthTexts.requireText(request.getVerifyCode(), "验证码不能为空");

        Guard guard = idempotencyService.begin(ANONYMOUS_USER_ID, ENDPOINT_REGISTER, idempotencyKey,
                idempotencyService.requestHash(username, email, verifyCode));
        if (guard.hasCache()) {
            return idempotencyService.readCache(guard, RegisterVO.class);
        }

        try {
            RegisterVO vo = transactionTemplate.execute(status ->
                    createAccount(username, email, verifyCode, request));
            idempotencyService.success(guard, 0, vo);
            return vo;
        } catch (RuntimeException e) {
            idempotencyService.failure(guard);
            throw e;
        }
    }

    /** 注册事务体：查重 → 消费验证码 → 落库 → 绑定 STUDENT → 建立登录态。 */
    private RegisterVO createAccount(String username, String email, String verifyCode, RegisterRequest request) {
        if (countByUsername(username) > 0) {
            throw AuthException.accountExists("账号已存在");
        }
        if (countByEmail(email) > 0) {
            throw AuthException.accountExists("邮箱已被注册");
        }
        emailCodeService.consume(email, EmailCodePurpose.REGISTER, verifyCode);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(request.getPassword()));
        user.setNickname(AuthTexts.normalizeNickname(request.getNickname(), username));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw AuthException.accountExists("账号已存在");
        }

        // 接口文档 3.2：注册不得自动把第一个注册用户设为管理员，一律绑定 STUDENT
        SysRole studentRole = findRole(RoleConstants.STUDENT);
        if (studentRole == null) {
            log.warn("注册时未找到 STUDENT 角色，用户 {} 尚未绑定角色，请检查 RbacInitializer", username);
        } else {
            roleMapper.bindUserRole(user.getId(), studentRole.getId());
            // 新用户刚落库，清掉可能存在的空快照，避免首次鉴权拿着空权限
            rbacSnapshotCache.evict(user.getId());
        }

        StpUtil.login(user.getId());
        RegisterVO vo = new RegisterVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setToken(StpUtil.getTokenValue());
        vo.setTokenType("Bearer");
        log.info("注册成功: userId={}, username={}", user.getId(), username);
        return vo;
    }

    // ==================== 重置密码 ====================

    public void resetPassword(PasswordResetRequest request, String idempotencyKey, ClientRequest client) {
        String email = AuthTexts.normalizeEmail(request.getEmail());
        PasswordPolicy.validateMatch(request.getNewPassword(), request.getConfirmPassword());
        String verifyCode = AuthTexts.requireText(request.getVerifyCode(), "验证码不能为空");

        Guard guard = idempotencyService.begin(ANONYMOUS_USER_ID, ENDPOINT_PASSWORD_RESET, idempotencyKey,
                idempotencyService.requestHash(email, verifyCode));
        if (guard.hasCache()) {
            return;
        }

        try {
            Long userId = transactionTemplate.execute(status -> {
                emailCodeService.consume(email, EmailCodePurpose.RESET_PASSWORD, verifyCode);
                SysUser user = findByEmail(email);
                if (user == null) {
                    throw AuthException.userNotFound("用户不存在");
                }
                user.setPasswordHash(BCrypt.hashpw(request.getNewPassword()));
                user.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(user);
                return user.getId();
            });

            // 密码变了，旧登录态必须全部失效（接口文档 3.6）
            if (userId != null) {
                sessionService.logoutUser(userId);
                loginLogService.record(userId, email, true, client, "重置密码");
            }
            idempotencyService.success(guard, 0, Boolean.TRUE);
        } catch (RuntimeException e) {
            idempotencyService.failure(guard);
            throw e;
        }
    }

    // ==================== 当前用户 / 退出 ====================

    public CurrentUserVO currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw AuthException.userNotFound("用户不存在");
        }
        CurrentUserVO vo = new CurrentUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmailMasked(EmailMasker.mask(user.getEmail()));
        RbacSnapshotCache.Snapshot snapshot = rbacSnapshotCache.get(userId);
        vo.setRoles(snapshot.roles());
        vo.setPermissions(snapshot.permissions());
        vo.setHasProfile(userProfileMapper.countByUserId(userId) > 0);
        return vo;
    }

    public void logout() {
        sessionService.logoutCurrentSession();
    }

    // ==================== 查询辅助 ====================

    private SysUser findByAccount(String account) {
        SysUser byUsername = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, account)
                .last("LIMIT 1"));
        if (byUsername != null) {
            return byUsername;
        }
        return findByEmail(account.toLowerCase(Locale.ROOT));
    }

    private SysUser findByEmail(String email) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .apply("LOWER(email) = {0}", email)
                .last("LIMIT 1"));
    }

    private long countByUsername(String username) {
        return userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    }

    private long countByEmail(String email) {
        return userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().apply("LOWER(email) = {0}", email));
    }

    private SysRole findRole(String roleCode) {
        return roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
    }

    private boolean isDisabled(SysUser user) {
        return user.getStatus() != null && user.getStatus() == 0;
    }

    /** 供 P2/P3 复用的权限快照查询：与 StpInterfaceImpl 走同一份缓存。 */
    public List<String> permissionsOf(Long userId) {
        return rbacSnapshotCache.get(userId).permissions();
    }

    public List<String> rolesOf(Long userId) {
        return rbacSnapshotCache.get(userId).roles();
    }
}
