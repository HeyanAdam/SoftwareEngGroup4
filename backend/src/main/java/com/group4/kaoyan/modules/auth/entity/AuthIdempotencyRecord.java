package com.group4.kaoyan.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录（sys_idempotency_record，Flyway V3 已建表）。
 * 匿名接口（注册、重置密码）用 userId = 0 占位；唯一键为 (user_id, endpoint, idempotency_key)。
 */
@Data
@TableName("sys_idempotency_record")
public class AuthIdempotencyRecord {

    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String endpoint;

    private String idempotencyKey;

    private String requestHash;

    private Integer responseCode;

    private String responseJson;

    private Integer status;

    private LocalDateTime expiresAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
