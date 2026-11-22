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

    /**
     * 按状态计数（大屏 Outbox 指标用）。
     *
     * <p>只 COUNT，不 SELECT payload_json：大屏永远拿不到消息正文。</p>
     */
    long countByStatus(@Param("status") String status);

    /**
     * 统计长时间停在 PUBLISHING 的记录数（疑似实例崩溃遗留）。
     *
     * @param staleBefore 早于该 update_time 的 PUBLISHING 视为卡住，通常传 now - 5 分钟
     */
    long countStuckPublishing(@Param("staleBefore") LocalDateTime staleBefore);
}
