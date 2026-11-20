package com.group4.kaoyan.modules.auth.mapper;

import com.group4.kaoyan.modules.auth.entity.AuthIdempotencyRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 幂等记录表读写。表结构见 Flyway V3，唯一键保证「同一用户 + 同一接口 + 同一幂等键」只有一条记录。
 */
@Mapper
public interface AuthIdempotencyMapper {

    @Insert("INSERT INTO sys_idempotency_record "
            + "(user_id, endpoint, idempotency_key, request_hash, status, expires_at, create_time, update_time) "
            + "VALUES (#{userId}, #{endpoint}, #{idempotencyKey}, #{requestHash}, 0, #{expiresAt}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProcessing(AuthIdempotencyRecord record);

    @Select("SELECT * FROM sys_idempotency_record "
            + "WHERE user_id = #{userId} AND endpoint = #{endpoint} AND idempotency_key = #{idempotencyKey} "
            + "LIMIT 1")
    AuthIdempotencyRecord selectByKey(@Param("userId") long userId,
                                  @Param("endpoint") String endpoint,
                                  @Param("idempotencyKey") String idempotencyKey);

    @Update("UPDATE sys_idempotency_record "
            + "SET status = #{status}, response_code = #{responseCode}, response_json = #{responseJson}, "
            + "update_time = #{now} WHERE id = #{id}")
    int updateResult(@Param("id") Long id,
                     @Param("status") int status,
                     @Param("responseCode") Integer responseCode,
                     @Param("responseJson") String responseJson,
                     @Param("now") LocalDateTime now);

    @Delete("DELETE FROM sys_idempotency_record WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
