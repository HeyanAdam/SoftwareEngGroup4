package com.group4.kaoyan.platform.realtime.limit;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 当前节点上的生成并发计数，分别在用户和连接两个维度限流。
 */
@Component
public class RealtimeGenerationTracker {

    private final Map<Long, AtomicInteger> byUser = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> byConnection = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long userId, String connectionId, int userLimit, int connectionLimit) {
        if (userId == null || connectionId == null) {
            return false;
        }
        if (!increment(byUser, userId, userLimit)) {
            return false;
        }
        if (increment(byConnection, connectionKey(userId, connectionId), connectionLimit)) {
            return true;
        }
        releaseUser(userId);
        return false;
    }

    public void release(Long userId, String connectionId) {
        if (userId == null || connectionId == null) {
            return;
        }
        releaseConnection(connectionKey(userId, connectionId));
        releaseUser(userId);
    }

    public int userCount(Long userId) {
        AtomicInteger count = userId == null ? null : byUser.get(userId);
        return count == null ? 0 : count.get();
    }

    public int connectionCount(Long userId, String connectionId) {
        if (userId == null || connectionId == null) {
            return 0;
        }
        AtomicInteger count = byConnection.get(connectionKey(userId, connectionId));
        return count == null ? 0 : count.get();
    }

    private <K> boolean increment(Map<K, AtomicInteger> counters, K key, int limit) {
        if (limit <= 0) {
            return false;
        }
        AtomicInteger counter = counters.computeIfAbsent(key, ignored -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= limit) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void releaseUser(Long userId) {
        decrement(byUser, userId);
    }

    private void releaseConnection(String key) {
        decrement(byConnection, key);
    }

    private <K> void decrement(Map<K, AtomicInteger> counters, K key) {
        counters.computeIfPresent(key, (ignored, counter) ->
                counter.decrementAndGet() <= 0 ? null : counter);
    }

    private String connectionKey(Long userId, String connectionId) {
        return userId + ":" + connectionId;
    }
}
