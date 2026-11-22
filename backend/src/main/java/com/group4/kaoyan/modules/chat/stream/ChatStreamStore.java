package com.group4.kaoyan.modules.chat.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式事件的 Redis 持久化与断线补发（A-2）。
 *
 * <p>键口径：</p>
 * <ul>
 *   <li>{@code chat:stream:{streamId}} —— 事件 Stream，按 sequence 单调递增；</li>
 *   <li>{@code chat:stream:meta:{streamId}} —— 用户、会话、回答消息 ID、状态、最后序号；</li>
 *   <li>{@code chat:user:{userId}:streams} —— 用户近期流集合，便于排查前端漏流。</li>
 * </ul>
 *
 * <p>Redis 不可用时只降级为“不能补发”，不影响回答继续生成与数据库落库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamStore {

    private static final String STREAM_PREFIX = "chat:stream:";
    private static final String META_PREFIX = "chat:stream:meta:";
    private static final String USER_PREFIX = "chat:user:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void append(ChatStreamEvent event, Long userId, Long conversationId, Long assistantMessageId) {
        String streamId = event.streamId();
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("sequence", Long.toString(event.sequence()));
            fields.put("type", event.type());
            fields.put("streamId", streamId);
            fields.put("requestId", event.requestId() == null ? "" : event.requestId());
            fields.put("serverTime", event.serverTime());
            fields.put("data", objectMapper.writeValueAsString(event.data()));

            StreamOperations<String, Object, Object> ops = redisTemplate.opsForStream();
            ops.add(StreamRecords.string(fields).withStreamKey(STREAM_PREFIX + streamId));

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("streamId", streamId);
            meta.put("userId", String.valueOf(userId));
            meta.put("conversationId", conversationId == null ? "" : String.valueOf(conversationId));
            meta.put("assistantMessageId",
                    assistantMessageId == null ? "" : String.valueOf(assistantMessageId));
            meta.put("lastSequence", Long.toString(event.sequence()));
            meta.put("updatedAt", Long.toString(System.currentTimeMillis()));
            String metaKey = META_PREFIX + streamId;
            redisTemplate.opsForHash().putAll(metaKey, meta);
            // 终态由 markStatus 写入；后续到达的 done/error 事件不能把 COMPLETED 覆盖回 STREAMING
            redisTemplate.opsForHash().putIfAbsent(metaKey, "status", ChatStreamBacklog.STATUS_STREAMING);

            redisTemplate.expire(STREAM_PREFIX + streamId, TTL);
            redisTemplate.expire(metaKey, TTL);
            String userKey = USER_PREFIX + userId + ":streams";
            redisTemplate.opsForSet().add(userKey, streamId);
            redisTemplate.expire(userKey, TTL);
        } catch (Exception e) {
            log.warn("写入 Redis Stream 失败（降级为仅数据库持久化）streamId={}：{}", streamId, e.getMessage());
        }
    }

    /** 重试前清空旧事件，避免同一 streamId 下新旧序号混在一起。 */
    public void clear(String streamId) {
        try {
            redisTemplate.delete(List.of(STREAM_PREFIX + streamId, META_PREFIX + streamId));
        } catch (Exception e) {
            log.warn("清理 Redis 流失败 streamId={}：{}", streamId, e.getMessage());
        }
    }

    /** 终态收口：把 meta 状态改为 COMPLETED/FAILED，并记录最后序号。 */
    public void markStatus(String streamId, String status, long lastSequence, String errorMessage) {
        try {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("status", status);
            meta.put("lastSequence", Long.toString(lastSequence));
            meta.put("updatedAt", Long.toString(System.currentTimeMillis()));
            if (errorMessage != null) {
                meta.put("errorMessage", errorMessage.length() > 500
                        ? errorMessage.substring(0, 500) : errorMessage);
            }
            String metaKey = META_PREFIX + streamId;
            redisTemplate.opsForHash().putAll(metaKey, meta);
            redisTemplate.expire(metaKey, TTL);
        } catch (Exception e) {
            log.warn("更新 Redis 流状态失败 streamId={}：{}", streamId, e.getMessage());
        }
    }

    /**
     * 按 {@code streamId + lastSequence} 返回遗漏事件；已过期的流回 404，越权回 403。
     */
    public ChatStreamBacklog backlog(String streamId, long lastSequence, Long requesterId, boolean admin) {
        String metaKey = META_PREFIX + streamId;
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        if (meta == null || meta.isEmpty()) {
            throw new BizException(404, "流不存在或已过期");
        }
        long owner = parseLong(meta.get("userId"), -1L);
        if (!admin && (requesterId == null || requesterId != owner)) {
            throw new BizException(ErrorCode.NO_DATA_ACCESS, "无权读取该流");
        }
        String status = String.valueOf(meta.getOrDefault("status", ChatStreamBacklog.STATUS_STREAMING));
        long serverLast = parseLong(meta.get("lastSequence"), 0L);
        String assistantMessageId = String.valueOf(meta.getOrDefault("assistantMessageId", ""));

        List<ChatStreamEvent> events = new ArrayList<>();
        try {
            StreamOperations<String, Object, Object> ops = redisTemplate.opsForStream();
            List<MapRecord<String, Object, Object>> records =
                    ops.range(STREAM_PREFIX + streamId, Range.unbounded());
            if (records != null) {
                for (MapRecord<String, Object, Object> record : records) {
                    ChatStreamEvent event = toEvent(record.getValue());
                    if (event != null && event.sequence() > lastSequence) {
                        events.add(event);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 Redis Stream 失败 streamId={}：{}", streamId, e.getMessage());
            throw new BizException(503, "流事件暂不可用，请稍后重试");
        }
        return new ChatStreamBacklog(streamId, status, serverLast,
                assistantMessageId.isBlank() ? null : parseLong(assistantMessageId, -1L), events);
    }

    private ChatStreamEvent toEvent(Map<Object, Object> fields) {
        try {
            long sequence = parseLong(fields.get("sequence"), 0L);
            String type = String.valueOf(fields.getOrDefault("type", ""));
            String streamId = String.valueOf(fields.getOrDefault("streamId", ""));
            String requestId = String.valueOf(fields.getOrDefault("requestId", ""));
            String serverTime = String.valueOf(fields.getOrDefault("serverTime", ""));
            String dataJson = String.valueOf(fields.getOrDefault("data", "{}"));
            Map<String, Object> data = objectMapper.readValue(dataJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            return new ChatStreamEvent(ChatStreamEvent.VERSION, type, requestId, streamId,
                    sequence, serverTime, data);
        } catch (Exception e) {
            log.debug("跳过无法解析的 Redis 流事件：{}", e.getMessage());
            return null;
        }
    }

    private static long parseLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
