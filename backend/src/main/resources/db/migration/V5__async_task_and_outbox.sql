-- V5：异步任务、消息 Outbox 与消费幂等记录
-- 对应接口文档 V1.1 §6.3 图片搜题任务、§11.3 知识文档解析

CREATE TABLE IF NOT EXISTS sys_async_task (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '对外即 task_id',
  task_no       VARCHAR(64)   NOT NULL COMMENT '业务唯一任务号，内部排查与 MQ 关联用',
  task_type     VARCHAR(32)   NOT NULL COMMENT 'DOC_PARSE/DOC_CHUNK/EMBEDDING/IMAGE_QUESTION/AI_REPORT',
  biz_type      VARCHAR(32)   NOT NULL COMMENT 'DOCUMENT/QUESTION/KNOWLEDGE_BASE/...',
  biz_id        BIGINT        NULL COMMENT '业务对象主键',
  user_id       BIGINT        NULL COMMENT '发起用户，系统任务可为空',
  status        VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/FAILED/CANCELLED；接口对外映射 QUEUED/PROCESSING',
  progress      INT           NOT NULL DEFAULT 0 COMMENT '进度 0-100',
  retry_count   INT           NOT NULL DEFAULT 0 COMMENT '已重试次数',
  payload_json  JSON          NULL COMMENT '任务入参，如图片任务 extra_question',
  result_json   JSON          NULL COMMENT '任务结果，图片任务含识别结果与 result_path',
  error_message VARCHAR(1000) NULL COMMENT '失败原因',
  next_retry_at DATETIME      NULL COMMENT '下次重试时间',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  finished_time DATETIME      NULL COMMENT '进入终态的时间',
  UNIQUE KEY uk_async_task_no (task_no),
  KEY idx_async_task_status (status, next_retry_at),
  KEY idx_async_task_biz (biz_type, biz_id),
  KEY idx_async_task_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='异步任务';

CREATE TABLE IF NOT EXISTS sys_message_outbox (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id      VARCHAR(64)   NOT NULL COMMENT '消息唯一事件 ID，消费者据此幂等',
  exchange_name VARCHAR(128)  NOT NULL COMMENT '交换机，如 kaoyan.direct',
  routing_key   VARCHAR(128)  NOT NULL COMMENT '路由键，如 doc.parse.task',
  message_type  VARCHAR(64)   NOT NULL COMMENT '消息类型，如 DOC_PARSE_TASK',
  biz_key       VARCHAR(128)  NULL COMMENT '业务键，便于排查',
  payload_json  JSON          NOT NULL COMMENT '消息体',
  status        VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PUBLISHED/RETRY_WAIT/FAILED',
  retry_count   INT           NOT NULL DEFAULT 0 COMMENT '已重试次数',
  next_retry_at DATETIME      NULL COMMENT '下次重试时间',
  last_error    VARCHAR(1000) NULL COMMENT '最近一次发送错误',
  published_at  DATETIME      NULL COMMENT '发送成功时间',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_outbox_event (event_id),
  KEY idx_outbox_pending (status, next_retry_at),
  KEY idx_outbox_biz (message_type, biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息发送 Outbox';

CREATE TABLE IF NOT EXISTS sys_message_consume_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id    VARCHAR(64)   NOT NULL COMMENT '消息 ID，对应 sys_message_outbox.event_id',
  consumer_name VARCHAR(128)  NOT NULL COMMENT '消费者标识',
  biz_key       VARCHAR(128)  NULL COMMENT '业务键',
  status        VARCHAR(32)   NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCEEDED/FAILED/DEAD_LETTER',
  retry_count   INT           NOT NULL DEFAULT 0 COMMENT '消费重试次数',
  error_message VARCHAR(1000) NULL COMMENT '失败原因',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_consume_message (message_id, consumer_name),
  KEY idx_consume_status (status, update_time),
  KEY idx_consume_biz (biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息消费幂等记录';
