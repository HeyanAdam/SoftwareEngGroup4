package com.group4.kaoyan.modules.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import com.group4.kaoyan.common.R.PageData;
import com.group4.kaoyan.common.security.AccountStatusGuard;
import com.group4.kaoyan.common.security.RbacSnapshotCache;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.admin.dto.AdminUserVO;
import com.group4.kaoyan.modules.auth.entity.SysPermission;
import com.group4.kaoyan.modules.auth.entity.SysRole;
import com.group4.kaoyan.modules.auth.mapper.PermissionMapper;
import com.group4.kaoyan.modules.auth.mapper.RoleMapper;
import com.group4.kaoyan.modules.user.entity.SysUser;
import com.group4.kaoyan.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    /** 管理员角色码，与 RoleConstants 保持一致 */
    private static final String ROLE_ADMIN = RoleConstants.SYSTEM_ADMIN;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    /** 授权变更后立即失效权限快照，保证"改完就生效" */
    private final RbacSnapshotCache snapshotCache;
    /** 账号状态兜底缓存：禁用/启用后立即生效，不必等 TTL */
    private final AccountStatusGuard accountStatusGuard;

    /**
     * 用户分页列表（参数与文档 1.7 节一致：page_num / page_size / status）。
     *
     * <p>status 传 null 表示不过滤；keyword 为项目扩展的模糊搜索。
     * 返回结构按文档 13.2 节收敛为 {@link AdminUserVO}（user_id + roles），不直接暴露实体。</p>
     *
     * <p>每个用户的角色按页内 ID 逐个查（页大小上限 100），
     * 相比一次联表再在内存里分组，这条路径的 SQL 更简单、也更容易解释清楚。</p>
     */
    public PageData<AdminUserVO> pageUsers(String keyword, Integer status, long pageNum, long pageSize) {
        long safeNum = pageNum < 1 ? 1 : pageNum;
        long safeSize = pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<SysUser> p = new Page<>(safeNum, safeSize);
        IPage<SysUser> result = userMapper.selectPage(p, Wrappers.<SysUser>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getId));
        List<AdminUserVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageData.of(result.getTotal(), list, safeNum, safeSize);
    }

    /**
     * 用户详情（文档 13.2 节 GET /admin/users/{user_id}，此前代码缺失，本次补齐）。
     */
    public AdminUserVO userDetail(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return toVO(user);
    }

    /** 实体 → 后台视图对象（字段名与文档对齐，邮箱脱敏）。 */
    private AdminUserVO toVO(SysUser user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(AdminUserVO.maskEmail(user.getEmail()));
        vo.setStatus(user.getStatus());
        vo.setRoles(roleMapper.selectRoleCodesByUserId(user.getId()));
        return vo;
    }

    /**
     * 启用 / 禁用用户。
     *
     * <p>禁用后要做三件事，缺一件都会留下"已禁用但还能用"的漏洞：</p>
     * <ol>
     *   <li>清掉该用户的权限快照，避免 TTL 内继续命中旧快照；</li>
     *   <li>清掉账号状态兜底缓存，让 AccountStatusGuard 下一次请求立刻查到 status=0；</li>
     *   <li>踢掉已有会话，否则他手上的 Token 还能继续发请求（Sa-Token 已登录态不查库）。</li>
     * </ol>
     * <p>第 2 条是第 3 条的兜底：kickout 只能踢当前会话，多端登录或踢人失手时，
     * 鉴权链上的状态校验仍然会把请求挡在门外（403 + 10013）。</p>
     */
    public void updateStatus(Long userId, Integer status) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        snapshotCache.evict(userId);
        accountStatusGuard.evict(userId);
        if (status != null && status == 0) {
            try {
                StpUtil.kickout(userId);
            } catch (RuntimeException e) {
                // 未登录时踢人可能报"账号未登录"，不影响禁用结果，只记日志
                log.info("禁用用户 {} 后踢下线未成功（可能本就未登录）：{}", userId, e.getMessage());
            }
        }
    }

    @Transactional
    public void assignRoles(Long operatorId, Long userId, List<Long> roleIds) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException("用户不存在");
        }
        boolean targetIsAdmin = roleMapper.selectRoleCodesByUserId(userId).contains(ROLE_ADMIN);
        boolean keepAdmin = roleIds != null && roleIds.stream()
                .map(roleMapper::selectById)
                .anyMatch(r -> r != null && ROLE_ADMIN.equals(r.getRoleCode()));
        // 覆盖式保存很容易漏勾管理员角色。禁止当前操作者给自己降权，
        // 避免保存成功后当前 token 立刻失去后台权限，页面只剩 403。
        if (userId.equals(operatorId) && targetIsAdmin && !keepAdmin) {
            throw new BizException("不能移除自己的系统管理员角色；请让另一位系统管理员操作");
        }
        // 兜底：不能把系统里最后一个管理员降权，否则谁都没法再进后台
        if (targetIsAdmin && !keepAdmin && roleMapper.countUsersWithRoleCode(ROLE_ADMIN) <= 1) {
            throw new BizException("这是当前唯一的系统管理员，不能降权；请先把其他用户提权");
        }
        roleMapper.unbindUserRoles(userId);
        if (roleIds != null) {
            roleIds.forEach(rid -> roleMapper.bindUserRole(userId, rid));
        }
        // 角色变了，该用户的权限快照立即作废，下次请求重新查库
        snapshotCache.evict(userId);
    }

    public List<SysRole> rolesOfUser(Long userId) {
        return roleMapper.selectByUserId(userId);
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
    }

    @Transactional
    public SysRole createRole(String roleCode, String roleName, String description) {
        if (roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode)) > 0) {
            throw new BizException("角色标识已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription(description);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        roleMapper.insert(role);
        return role;
    }

    public List<SysPermission> listPermissions() {
        return permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery().orderByAsc(SysPermission::getId));
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException("角色不存在");
        }
        permissionMapper.unbindRolePermissions(roleId);
        if (permissionIds != null) {
            permissionIds.forEach(pid -> permissionMapper.bindRolePermission(roleId, pid));
        }
        // 该角色的授权变了，角色下所有用户的权限快照立即作废
        snapshotCache.evictByRoleId(roleId);
    }

    /**
     * 编辑角色：只允许改展示用的名称与描述。
     *
     * <p>role_code 不动：它是鉴权与迁移脚本引用的唯一标识，改了等于把已授权用户锁在门外体系之外；
     * status 也不在本次开放，因为当前鉴权链不读角色状态，停用角色不会真正生效，开放只会造成误解。</p>
     */
    @Transactional
    public SysRole updateRole(Long roleId, String roleName, String description) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        if (roleName == null || roleName.isBlank()) {
            throw new BizException("角色名称不能为空");
        }
        String name = roleName.trim();
        long sameName = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleName, name)
                .ne(SysRole::getId, roleId));
        if (sameName > 0) {
            throw new BizException("角色名称已存在");
        }
        SysRole update = new SysRole();
        update.setId(roleId);
        update.setRoleName(name);
        update.setDescription(description);
        roleMapper.updateById(update);
        return roleMapper.selectById(roleId);
    }

    /**
     * 删除角色：内置角色不可删，仍有用户绑定的角色不可删。
     *
     * <p>删除顺序：先清角色授权（sys_role_permission），再删角色本体；
     * 用户绑定在调用前已校验为空，所以不必再动 sys_user_role。</p>
     */
    @Transactional
    public void deleteRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        if (RoleConstants.isBuiltin(role.getRoleCode())) {
            throw new BizException("内置角色不允许删除：" + role.getRoleCode());
        }
        if (roleMapper.countUsersWithRoleCode(role.getRoleCode()) > 0) {
            throw new BizException("仍有用户绑定该角色，请先解除绑定再删除");
        }
        permissionMapper.unbindRolePermissions(roleId);
        roleMapper.deleteById(roleId);
        // 防御性失效：正常路径下已无用户绑定，这里只保证缓存里不残留旧角色
        snapshotCache.evictByRoleId(roleId);
    }

    /**
     * 给用户追加一个角色（不覆盖已有角色）。
     *
     * <p>与 {@link #assignRoles} 的区别：assignRoles 是"整体替换"，适合表单式编辑；
     * 本方法是"逐个增减"，适合后台列表里点一下加一个角色的交互，重复调用幂等。</p>
     */
    @Transactional
    public void bindRole(Long userId, Long roleId) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException("用户不存在");
        }
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException("角色不存在");
        }
        roleMapper.bindUserRole(userId, roleId);
        snapshotCache.evict(userId);
    }

    /**
     * 解除用户的某个角色。
     *
     * <p>兜底：如果这是系统里最后一个管理员，拒绝解绑，否则没人能再进后台。</p>
     */
    @Transactional
    public void unbindRole(Long operatorId, Long userId, Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        boolean targetIsAdmin = roleMapper.selectRoleCodesByUserId(userId).contains(ROLE_ADMIN);
        if (ROLE_ADMIN.equals(role.getRoleCode())
                && userId.equals(operatorId)
                && targetIsAdmin) {
            throw new BizException("不能解除自己的系统管理员角色；请让另一位系统管理员操作");
        }
        if (ROLE_ADMIN.equals(role.getRoleCode())
                && targetIsAdmin
                && roleMapper.countUsersWithRoleCode(ROLE_ADMIN) <= 1) {
            throw new BizException("这是当前唯一的系统管理员，不能解除管理员角色");
        }
        roleMapper.unbindUserRole(userId, roleId);
        snapshotCache.evict(userId);
    }

    public List<String> permissionCodesOfRole(Long roleId) {
        return permissionMapper.selectPermCodesByRoleId(roleId);
    }
}
