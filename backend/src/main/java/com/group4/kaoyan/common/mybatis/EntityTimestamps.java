package com.group4.kaoyan.common.mybatis;

import java.time.LocalDateTime;

/**
 * 应用侧时间戳填充。
 *
 * <p>表上虽然有 {@code DEFAULT CURRENT_TIMESTAMP}，但不依赖它：
 * 显式写入可以保证应用与数据库使用同一时区，也让 MQ 重放/补偿时的语义可控。</p>
 */
public final class EntityTimestamps {

    private EntityTimestamps() {
    }

    /** 新增前调用：createTime / updateTime 都补齐，已存在的值不覆盖。 */
    public static void fillOnInsert(TimedEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        entity.setUpdateTime(now);
    }

    /** 更新前调用：只刷新 updateTime（保持 createTime 不变）。 */
    public static void fillOnUpdate(TimedEntity entity) {
        entity.setUpdateTime(LocalDateTime.now());
    }

    /** 更新前调用，createTime 显式清空，避免 UPDATE 语句误写创建时间。 */
    public static void fillOnUpdateAndClearCreateTime(TimedEntity entity) {
        entity.setCreateTime(null);
        entity.setUpdateTime(LocalDateTime.now());
    }
}
