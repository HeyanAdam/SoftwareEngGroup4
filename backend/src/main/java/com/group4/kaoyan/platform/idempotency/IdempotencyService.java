package com.group4.kaoyan.platform.idempotency;

import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.mybatis.JsonTypeHandler;
import com.group4.kaoyan.platform.idempotency.mapper.IdempotencyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 接口幂等状态机：0 PROCESSING / 1 SUCCESS / 2 FAILED。
 *
 * <p>并发保护靠 uk_idempotency(user_id, endpoint, idempotency_key) 唯一索引：
 * 先插后查，插入冲突再重查分支，不做"先查后插"这种有竞态的判断。</p>
 *
 * <p>注意：begin() 不加事务，PROCESSING 记录必须立刻对其他实例可见。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    /** 幂等记录保留 24 小时 */
    private static final long EXPIRE_HOURS = 24;
    private static final int KEY_MAX_LENGTH = 128;
    private static final int ENDPOINT_MAX_LENGTH = 255;

    private final IdempotencyMapper idempotencyMapper;

    /** begin 的结果：要么新开一次执行，要么直接回放上次响应。 */
    public record Begin(Long recordId, boolean replay, Integer replayCode, String replayBody) {

        static Begin started(Long recordId) {
            return new Begin(recordId, false, null, null);
        }

        static Begin replay(Integer code, String body) {
            return new Begin(null, true, code, body);
        }
    }

    public Begin begin(Long userId, String endpoint, String key, String requestHash) {
        if (key == null || key.isBlank()) {
            throw new BizException(400, "缺少 Idempotency-Key 请求头");
        }
        if (key.length() > KEY_MAX_LENGTH) {
            throw new BizException(400, "Idempotency-Key 超长（最多 128 字符）");
        }
        if (endpoint != null && endpoint.length() > ENDPOINT_MAX_LENGTH) {
            endpoint = endpoint.substring(0, ENDPOINT_MAX_LENGTH);
        }

        IdempotencyRecord existing = idempotencyMapper.selectByKey(userId, endpoint, key);
        if (existing == null) {
            Long id = tryInsert(userId, endpoint, key, requestHash);
            if (id != null) {
                return Begin.started(id);
            }
            existing = idempotencyMapper.selectByKey(userId, endpoint, key);
            if (existing == null) {
                throw new BizException(409, "幂等记录创建冲突，请稍后重试");
            }
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            if (existing.getExpiresAt() != null && existing.getExpiresAt().isBefore(LocalDateTime.now())) {
                // 过期：清掉旧记录重新执行一次
                idempotencyMapper.deleteById(existing.getId());
                Long id = tryInsert(userId, endpoint, key, requestHash);
                if (id != null) {
                    return Begin.started(id);
                }
                existing = idempotencyMapper.selectByKey(userId, endpoint, key);
                if (existing == null) {
                    throw new BizException(409, "幂等记录创建冲突，请稍后重试");
                }
                continue;
            }

            // 同一把 key 换了请求体：说明调用方复用了 key，直接拒绝
            if (requestHash != null && existing.getRequestHash() != null
                    && !Objects.equals(requestHash, existing.getRequestHash())) {
                throw new BizException(422, "同一 Idempotency-Key 的请求内容不一致");
            }

            int status = existing.getStatus() == null ? IdempotencyRecord.STATUS_PROCESSING : existing.getStatus();
            if (status == IdempotencyRecord.STATUS_SUCCESS) {
                String body = existing.getResponseJson() == null
                        ? null : JsonTypeHandler.toJson(existing.getResponseJson());
                return Begin.replay(existing.getResponseCode(), body);
            }
            if (status == IdempotencyRecord.STATUS_PROCESSING) {
                throw new BizException(409, "相同 Idempotency-Key 的请求正在处理中，请稍后重试");
            }
            // FAILED：允许重试，删掉旧记录重新抢占
            idempotencyMapper.deleteById(existing.getId());
            Long id = tryInsert(userId, endpoint, key, requestHash);
            if (id != null) {
                return Begin.started(id);
            }
            existing = idempotencyMapper.selectByKey(userId, endpoint, key);
            if (existing == null) {
                throw new BizException(409, "幂等记录创建冲突，请稍后重试");
            }
        }
        throw new BizException(409, "幂等状态异常，请稍后重试");
    }

    public void success(Long recordId, Integer code, Object body) {
        if (recordId == null) {
            return;
        }
        try {
            idempotencyMapper.updateResult(recordId, IdempotencyRecord.STATUS_SUCCESS, code, body);
        } catch (Exception e) {
            log.warn("写入幂等成功结果失败 recordId={}：{}", recordId, e.getMessage());
        }
    }

    public void fail(Long recordId) {
        if (recordId == null) {
            return;
        }
        try {
            idempotencyMapper.updateResult(recordId, IdempotencyRecord.STATUS_FAILED, null, null);
        } catch (Exception e) {
            log.warn("写入幂等失败状态失败 recordId={}：{}", recordId, e.getMessage());
        }
    }

    /** 清理任务：循环删除过期记录，单次最多 1000 条。 */
    public int cleanup() {
        int total = 0;
        for (int i = 0; i < 10; i++) {
            int deleted = idempotencyMapper.deleteExpired(1000);
            total += deleted;
            if (deleted < 1000) {
                break;
            }
        }
        return total;
    }

    private Long tryInsert(Long userId, String endpoint, String key, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setEndpoint(endpoint);
        record.setIdempotencyKey(key);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyRecord.STATUS_PROCESSING);
        LocalDateTime now = LocalDateTime.now();
        record.setExpiresAt(now.plusHours(EXPIRE_HOURS));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            idempotencyMapper.insert(record);
            return record.getId();
        } catch (DuplicateKeyException e) {
            return null;
        }
    }
}
