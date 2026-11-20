package com.group4.kaoyan.platform.messaging.mapper;

import com.group4.kaoyan.platform.messaging.entity.ConsumeLog;
import org.apache.ibatis.annotations.Param;

public interface ConsumeLogMapper {

    int insert(ConsumeLog log);

    ConsumeLog selectByMessage(@Param("messageId") String messageId,
                              @Param("consumerName") String consumerName);

    int markSucceeded(@Param("id") Long id);

    /** 失败一次：retry_count + 1，状态置 FAILED（允许下一轮重跑）。 */
    int failAndIncrement(@Param("id") Long id, @Param("error") String error);

    /** 重新置为 PROCESSING（上一轮已中断或需要重试）。 */
    int resetProcessing(@Param("id") Long id);

    int markDeadLetter(@Param("id") Long id, @Param("error") String error);
}
