package com.group4.kaoyan.modules.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计标记。只挂在后台、知识库、文件的写接口上。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 模块，如 admin / kb / file */
    String module();

    /** 动作，如 CREATE / UPDATE / DELETE / REINDEX */
    String action();

    /** 资源类型，如 USER / DOCUMENT / FILE */
    String resourceType() default "";
}
