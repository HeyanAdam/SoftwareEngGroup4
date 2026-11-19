package com.group4.kaoyan.platform.idempotency;

import com.group4.kaoyan.common.mybatis.TimedEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录（sys_idempotency_record）。
 * 状态：0 PROCESSING / 1 SUCCESS / 2 FAILED。
 */
@Data
public class IdempotencyRecord implements TimedEntity {

    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    private Long id;
    private Long userId;
    private String endpoint;
    private String idempotencyKey;
    private String requestHash;
    private Integer responseCode;
    /** JSON 列：成功时的响应体 */
    private Object responseJson;
    private Integer status;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
