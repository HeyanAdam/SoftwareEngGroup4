package com.group4.kaoyan.modules.auth.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.modules.auth.entity.AuthIdempotencyRecord;
import com.group4.kaoyan.modules.auth.mapper.AuthIdempotencyMapper;
import com.group4.kaoyan.modules.auth.support.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 幂等键（Idempotency-Key）实现，落在 sys_idempotency_record 表上，
 * 保证「同一用户 + 同一接口 + 同一幂等键」重复提交时返回第一次的处理结果。
 *
 * <p>流程：</p>
 * <pre>
 * begin()   插入 status=0 的占位记录；若已存在：
 *            status=1 直接返回缓存结果，status=0 返回 10011（仍在处理中），status=2 允许重试
 * success() 写入响应体，status=1
 * failure() 标记失败，status=2
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthIdempotencyService {

    /** 幂等记录保留 24 小时 */
    private static final Duration RECORD_TTL = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 128;

    private final AuthIdempotencyMapper AuthIdempotencyMapper;
    private final ObjectMapper objectMapper;

    /** 一次幂等请求的句柄；cachedResponseJson 不为空说明应直接返回上次结果。 */
    public record Guard(Long recordId, String cachedResponseJson) {
        public boolean hasCache() {
            return cachedResponseJson != null && !cachedResponseJson.isBlank();
        }
    }

    public Guard begin(long userId, String endpoint, String idempotencyKey, String requestHash) {
        String key = requireKey(idempotencyKey);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                LocalDateTime now = LocalDateTime.now();
                AuthIdempotencyRecord record = new AuthIdempotencyRecord();
                record.setUserId(userId);
                record.setEndpoint(endpoint);
                record.setIdempotencyKey(key);
                record.setRequestHash(requestHash);
                record.setStatus(AuthIdempotencyRecord.STATUS_PROCESSING);
                record.setExpiresAt(now.plus(RECORD_TTL));
                record.setCreateTime(now);
                record.setUpdateTime(now);
                AuthIdempotencyMapper.insertProcessing(record);
                return new Guard(record.getId(), null);
            } catch (DuplicateKeyException e) {
                AuthIdempotencyRecord existing = AuthIdempotencyMapper.selectByKey(userId, endpoint, key);
                if (existing == null) {
                    // 唯一键冲突后读不到记录，说明并发窗口里对方刚回滚，重试一次即可
                    continue;
                }
                int status = existing.getStatus() == null ? AuthIdempotencyRecord.STATUS_PROCESSING : existing.getStatus();
                if (status == AuthIdempotencyRecord.STATUS_SUCCESS) {
                    log.info("命中幂等缓存: endpoint={}, key={}", endpoint, key);
                    return new Guard(existing.getId(), existing.getResponseJson());
                }
                if (status == AuthIdempotencyRecord.STATUS_PROCESSING) {
                    throw AuthException.idempotencyInProgress();
                }
                // 上一次处理失败，删掉占位记录后重新执行
                AuthIdempotencyMapper.deleteById(existing.getId());
            }
        }
        throw AuthException.idempotencyInProgress();
    }

    public void success(Guard guard, int responseCode, Object data) {
        if (guard == null || guard.recordId() == null) {
            return;
        }
        AuthIdempotencyMapper.updateResult(guard.recordId(), AuthIdempotencyRecord.STATUS_SUCCESS,
                responseCode, toJson(data), LocalDateTime.now());
    }

    public void failure(Guard guard) {
        if (guard == null || guard.recordId() == null) {
            return;
        }
        AuthIdempotencyMapper.updateResult(guard.recordId(), AuthIdempotencyRecord.STATUS_FAILED,
                null, null, LocalDateTime.now());
    }

    /** 请求指纹：同一幂等键但请求体不同时也应当被视为新请求。 */
    public String requestHash(String... parts) {
        return DigestUtil.sha256Hex(String.join("|", parts));
    }

    public <T> T readCache(Guard guard, Class<T> type) {
        try {
            return objectMapper.readValue(guard.cachedResponseJson(), type);
        } catch (JsonProcessingException e) {
            log.error("幂等缓存反序列化失败", e);
            throw AuthException.serviceUnavailable("幂等缓存解析失败，请稍后重试");
        }
    }

    public String toJson(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("幂等响应序列化失败", e);
            throw AuthException.serviceUnavailable("响应序列化失败");
        }
    }

    private String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw AuthException.paramInvalid("缺少请求头 Idempotency-Key");
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw AuthException.paramInvalid("Idempotency-Key 长度不能超过 " + MAX_KEY_LENGTH + " 位");
        }
        return key;
    }
}
