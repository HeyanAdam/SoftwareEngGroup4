-- 1. 幂等记录表
CREATE TABLE IF NOT EXISTS `sys_idempotency_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `endpoint` VARCHAR(255) NOT NULL COMMENT '请求路径',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
  `request_hash` VARCHAR(64) NULL COMMENT '请求哈希',
  `response_code` INT NULL COMMENT '响应码',
  `response_json` JSON NULL COMMENT '响应体',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0处理中,1成功,2失败)',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency` (`user_id`, `endpoint`, `idempotency_key`),
  KEY `idx_idempotency_expire` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统幂等记录表';

-- 2. 登录日志表
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NULL COMMENT '用户ID',
  `account` VARCHAR(128) NOT NULL COMMENT '账号',
  `success` TINYINT NOT NULL COMMENT '是否成功(0否,1是)',
  `ip_address` VARCHAR(64) NULL COMMENT 'IP地址',
  `user_agent` VARCHAR(512) NULL COMMENT 'UA',
  `failure_reason` VARCHAR(255) NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- 3. 操作审计表
CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NULL COMMENT '用户ID',
  `module` VARCHAR(64) NOT NULL COMMENT '模块',
  `action` VARCHAR(64) NOT NULL COMMENT '动作',
  `resource_type` VARCHAR(64) NULL COMMENT '资源类型',
  `resource_id` VARCHAR(64) NULL COMMENT '资源ID',
  `request_id` VARCHAR(64) NULL COMMENT '请求ID',
  `ip_address` VARCHAR(64) NULL COMMENT 'IP地址',
  `before_json` JSON NULL COMMENT '变更前',
  `after_json` JSON NULL COMMENT '变更后',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计表';