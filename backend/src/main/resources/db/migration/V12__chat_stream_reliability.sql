-- V12：流式问答可靠性（A-2）
-- 为 chat_message 增加幂等键、流标识、回答状态与断线恢复游标

ALTER TABLE chat_message
  ADD COLUMN client_message_id VARCHAR(64) NULL COMMENT '客户端幂等键，仅用户消息有值' AFTER content,
  ADD COLUMN reply_to_id BIGINT NULL COMMENT 'AI 回答对应的用户消息 ID' AFTER client_message_id,
  ADD COLUMN stream_id VARCHAR(64) NULL COMMENT '一次回答的稳定流 ID' AFTER reply_to_id,
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
      COMMENT 'PENDING/STREAMING/COMPLETED/FAILED' AFTER stream_id,
  ADD COLUMN last_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '已落库的最后事件序号' AFTER status,
  ADD COLUMN error_message VARCHAR(1000) NULL COMMENT '失败原因' AFTER last_sequence,
  ADD COLUMN completed_at DATETIME NULL COMMENT '进入终态时间' AFTER error_message,
  ADD UNIQUE KEY uk_chat_client_msg (conversation_id, user_id, client_message_id),
  ADD KEY idx_chat_stream (stream_id),
  ADD KEY idx_chat_reply (reply_to_id),
  ADD KEY idx_chat_status (status);

-- 历史消息没有流式元数据，统一补成已完成，避免前端把旧回答当成生成中
UPDATE chat_message SET status = 'COMPLETED', completed_at = create_time
WHERE status IS NULL OR status = '';
