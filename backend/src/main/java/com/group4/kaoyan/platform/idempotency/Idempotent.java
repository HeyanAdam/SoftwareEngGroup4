package com.group4.kaoyan.platform.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等标记。只标注需要幂等的写接口，未标注的接口行为完全不变。
 *
 * <p>调用方需带请求头 {@code Idempotency-Key}（1–128 字符）。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
}
