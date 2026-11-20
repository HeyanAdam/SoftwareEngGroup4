package com.group4.kaoyan.platform.idempotency.mapper;

import com.group4.kaoyan.platform.idempotency.IdempotencyRecord;
import org.apache.ibatis.annotations.Param;

public interface IdempotencyMapper {

    IdempotencyRecord selectByKey(@Param("userId") Long userId,
                                 @Param("endpoint") String endpoint,
                                 @Param("idempotencyKey") String idempotencyKey);

    int insert(IdempotencyRecord record);

    /** 只有抢占者能写终态：AND status = 0。 */
    int updateResult(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("code") Integer code,
                     @Param("body") Object body);

    int deleteById(@Param("id") Long id);

    int deleteExpired(@Param("limit") int limit);
}
