-- V7：数据持久化配套索引（只加索引，不改任何表结构）
-- 对应 docs/02_数据设计/03_数据持久化实现方案.md 第四节

CREATE INDEX idx_login_account_time ON sys_login_log (account, create_time);
CREATE INDEX idx_audit_module_time ON sys_audit_log (module, action, create_time);
CREATE INDEX idx_task_type_status ON sys_async_task (task_type, status, next_retry_at);
CREATE INDEX idx_outbox_type_status ON sys_message_outbox (message_type, status, next_retry_at);
CREATE INDEX idx_upload_part_hash ON file_upload_part (part_hash);
