package com.group4.kaoyan.common.mybatis;

import java.time.LocalDateTime;

/**
 * 带应用侧维护时间戳的实体。新写的原生 MyBatis 实体实现该接口，
 * 统一由 {@link EntityTimestamps} 赋值，不依赖 MyBatis-Plus 的 MetaObjectHandler。
 */
public interface TimedEntity {

    LocalDateTime getCreateTime();

    void setCreateTime(LocalDateTime createTime);

    LocalDateTime getUpdateTime();

    void setUpdateTime(LocalDateTime updateTime);
}
