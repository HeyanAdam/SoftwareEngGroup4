package com.group4.kaoyan.platform.messaging.mapper;

import com.group4.kaoyan.platform.messaging.entity.MessageOutbox;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxMapper {

    int insert(MessageOutbox outbox);

    List<MessageOutbox> selectPending(@Param("limit") int limit);

    /** 抢单：只有把 PENDING/RETRY_WAIT 改成 PUBLISHING 成功的实例才发送。 */
    int claim(@Param("id") Long id);

    int markPublished(@Param("id") Long id);

    int markRetry(@Param("id") Long id,
                  @Param("error") String error,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt);

    int markFailed(@Param("id") Long id, @Param("error") String error);

    /** 长时间停在 PUBLISHING 的记录复位为 RETRY_WAIT（实例崩溃兜底）。 */
    int resetStuck(@Param("staleBefore") LocalDateTime staleBefore);
}
