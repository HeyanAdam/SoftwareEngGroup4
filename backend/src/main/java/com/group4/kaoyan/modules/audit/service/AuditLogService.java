package com.group4.kaoyan.modules.audit.service;

import com.group4.kaoyan.modules.audit.entity.AuditLogRecord;
import com.group4.kaoyan.modules.audit.mapper.AuditLogMapper;
import com.group4.kaoyan.common.mybatis.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计落库。异步 + 独立事务：主流程失败回滚不影响审计，审计失败也不回滚业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void save(AuditLogRecord record) {
        try {
            if (record.getCreateTime() == null) {
                record.setCreateTime(LocalDateTime.now());
            }
            auditLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("写入审计日志失败，已忽略：{}", e.getMessage());
        }
    }

    public List<AuditLogRecord> query(String module, String action,
                                      LocalDateTime from, LocalDateTime to,
                                      long pageNum, long pageSize) {
        PageQuery page = PageQuery.of(pageNum, pageSize);
        return auditLogMapper.selectByModule(module, action, from, to, page.getOffset(), page.getLimit());
    }

    public long count(String module, String action, LocalDateTime from, LocalDateTime to) {
        return auditLogMapper.countByModule(module, action, from, to);
    }
}
