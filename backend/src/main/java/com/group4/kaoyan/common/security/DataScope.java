package com.group4.kaoyan.common.security;

/**
 * 数据范围（P2 任务 3 的枚举基础，任务 1 先定义好，矩阵里直接引用）。
 *
 * <p>接口权限码只回答"能不能进这个接口"，数据范围回答"进了以后能看到谁的数据"：</p>
 * <ul>
 *   <li>{@link #SELF}：只能看/改本人数据（学生默认）。</li>
 *   <li>{@link #ALL}：可以看全部数据（系统管理员、知识库管理员）。</li>
 *   <li>{@link #MASKED}：可以看汇总或脱敏后的数据，但不能看敏感明细（数据查看员）。</li>
 * </ul>
 *
 * <p>安全默认：解析失败或为空时一律降级为 {@link #SELF}，
 * 绝不在"配置没写清楚"的情况下放大权限。</p>
 */
public enum DataScope {

    /** 仅本人数据。 */
    SELF,

    /** 全部数据。 */
    ALL,

    /** 脱敏/汇总数据（不含敏感明细）。 */
    MASKED;

    /** 是否可读全部数据。 */
    public boolean canReadAll() {
        return this == ALL;
    }

    /**
     * 宽松解析：忽略大小写与空白，非法值降级为 SELF。
     * 与 {@code Enum.valueOf} 的区别是有兜底，不会因为脏数据抛异常导致接口 500。
     */
    public static DataScope of(String value) {
        if (value == null || value.isBlank()) {
            return SELF;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SELF;
        }
    }
}
