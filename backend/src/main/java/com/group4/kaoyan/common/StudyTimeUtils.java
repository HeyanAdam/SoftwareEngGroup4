package com.group4.kaoyan.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 学习计划/推荐模块的时间口径工具：统一把数据库 LocalDateTime 转成带 +08:00 的 OffsetDateTime,
 * 对齐《共同契约》要求的 ISO 8601 带偏移时间格式。
 */
public final class StudyTimeUtils {

    public static final ZoneOffset SERVER_OFFSET = ZoneOffset.ofHours(8);

    private StudyTimeUtils() {
    }

    /** LocalDateTime → OffsetDateTime(+08:00);null 透传。 */
    public static OffsetDateTime toOffset(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atOffset(SERVER_OFFSET);
    }

    /** 当前时间的 OffsetDateTime(+08:00)。 */
    public static OffsetDateTime nowOffset() {
        return OffsetDateTime.now(SERVER_OFFSET);
    }
}
