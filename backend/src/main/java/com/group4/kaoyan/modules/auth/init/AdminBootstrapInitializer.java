package com.group4.kaoyan.modules.auth.init;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理员受控初始化（P2 任务 5）。
 *
 * <p>只认环境变量，不在代码里写死账号密码，也不允许"谁先注册谁是管理员"：
 * 生产环境由运维注入 {@code KAOYAN_ADMIN_USERNAME} / {@code KAOYAN_ADMIN_PASSWORD}
 * 后再启动，账号是<b>受控创建</b>的。</p>
 *
 * <p>三个约束：</p>
 * <ol>
 *   <li>系统里已经有 SYSTEM_ADMIN 时直接跳过，不会覆盖已有管理员密码；</li>
 *   <li>没配环境变量就什么都不做，只在日志里提示怎么开启（本地开发按需开启）；</li>
 *   <li>密码统一 BCrypt 哈希入库，明文不落库、不落日志。</li>
 * </ol>
 *
 * <p>审计：当前库里还没有审计表（sys_audit_log 待数据库成员建表），
 * 本类先把"谁在什么时间创建了管理员"写进应用日志，接口层改动权限的审计
 * 也统一走 {@code log.info}，建表后再补持久化。</p>
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final String ENV_USERNAME = "KAOYAN_ADMIN_USERNAME";
    private static final String ENV_PASSWORD = "KAOYAN_ADMIN_PASSWORD";
    private static final String ENV_NICKNAME = "KAOYAN_ADMIN_NICKNAME";

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = readEnv(ENV_USERNAME);
        String password = readEnv(ENV_PASSWORD);
        if (username == null || password == null) {
            log.info("未配置 {} / {}，跳过后台管理员受控初始化", ENV_USERNAME, ENV_PASSWORD);
            return;
        }
        if (roleMapper.countUsersWithRoleCode(RoleConstants.SYSTEM_ADMIN) > 0) {
            log.info("系统已存在管理员，跳过受控初始化");
            return;
        }
        SysRole adminRole = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, RoleConstants.SYSTEM_ADMIN));
        if (adminRole == null) {
            log.warn("未找到角色 {}，跳过受控初始化，请确认 RBAC 种子已执行", RoleConstants.SYSTEM_ADMIN);
            return;
        }
        long sameName = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (sameName > 0) {
            log.warn("用户名 {} 已被占用，跳过受控初始化", username);
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(username);
        admin.setPasswordHash(BCrypt.hashpw(password));
        admin.setNickname(readEnv(ENV_NICKNAME) == null ? "系统管理员" : readEnv(ENV_NICKNAME));
        admin.setStatus(1);
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        userMapper.insert(admin);
        roleMapper.bindUserRole(admin.getId(), adminRole.getId());
        log.info("审计：受控初始化创建系统管理员 {}（userId={}），请首次登录后立即修改密码", username, admin.getId());
    }

    /** 读环境变量：去空白，空串视为未配置。 */
    private static String readEnv(String key) {
        String value = System.getenv(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
