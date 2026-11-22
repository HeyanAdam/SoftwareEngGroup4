package com.group4.kaoyan.modules.chat.stream;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 当前节点上的活跃聊天流取消注册表。
 *
 * <p>按 {@code streamId} 定位运行中的流，并校验取消人就是流所有者。
 * 回调最多执行一次，流正常结束或客户端断开后立即移除。</p>
 */
@Component
public class ChatCancellationRegistry {

    private final Map<String, Entry> active = new ConcurrentHashMap<>();

    public Registration register(Long userId, String streamId, Runnable cancellation) {
        if (userId == null || streamId == null || streamId.isBlank() || cancellation == null) {
            throw new IllegalArgumentException("取消注册参数不完整");
        }
        Entry entry = new Entry(userId, cancellation, new AtomicBoolean());
        active.put(streamId, entry);
        return () -> active.remove(streamId, entry);
    }

    /**
     * 取消指定用户的活跃流。
     *
     * @return true 表示流存在且属于该用户；重复取消也返回 true，但回调只执行一次
     */
    public boolean cancel(Long userId, String streamId) {
        if (userId == null || streamId == null) {
            return false;
        }
        Entry entry = active.get(streamId);
        if (entry == null || !entry.userId().equals(userId)) {
            return false;
        }
        if (entry.cancelled().compareAndSet(false, true)) {
            entry.cancellation().run();
        }
        return true;
    }

    public boolean isActive(Long userId, String streamId) {
        Entry entry = streamId == null ? null : active.get(streamId);
        return entry != null && entry.userId().equals(userId);
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private record Entry(Long userId, Runnable cancellation, AtomicBoolean cancelled) {
    }
}
