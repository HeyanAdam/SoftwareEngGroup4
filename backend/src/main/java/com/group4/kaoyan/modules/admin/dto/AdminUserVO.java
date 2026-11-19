package com.group4.kaoyan.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 后台用户视图对象（字段与《接口设计文档 V1.1》13.2 节用户列表示例一一对应）。
 *
 * <p>为什么不再直接返回 {@code SysUser}：文档要求的字段是
 * {@code user_id / username / nickname / email / status / roles}，
 * 直接序列化实体会得到 {@code id}、{@code createTime} 以及 {@code passwordHash} 这类不该外露的字段。
 * 用 VO 收敛返回结构，既是文档契约，也顺手堵住密码哈希外泄。</p>
 */
@Data
public class AdminUserVO {

    /** 文档字段 user_id（数据库主键 BIGINT，JSON 整数）。 */
    @JsonProperty("user_id")
    private Long userId;

    private String username;

    private String nickname;

    /** 脱敏展示，例：stu***@example.com（文档示例口径）。 */
    private String email;

    private Integer status;

    /** 该用户当前绑定的角色码（只含启用角色）。 */
    private List<String> roles;

    /** 邮箱脱敏：保留前三位 + *** + 域名。 */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String head = local.length() <= 3 ? local : local.substring(0, 3);
        return head + "***" + domain;
    }
}
