-- 1. 用户画像表
CREATE TABLE IF NOT EXISTS `user_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `gender` TINYINT NULL COMMENT '性别',
  `major` VARCHAR(100) NULL COMMENT '专业',
  `grade` VARCHAR(32) NULL COMMENT '年级',
  `base_level` TINYINT NULL COMMENT '基础水平',
  `weekly_study_minutes` INT NOT NULL DEFAULT 0 COMMENT '每周学习分钟数',
  `weak_points_json` JSON NULL COMMENT '薄弱点',
  `profile_json` JSON NULL COMMENT '扩展画像',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_profile_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

-- 2. 备考目标表
CREATE TABLE IF NOT EXISTS `user_study_goal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `target_school_id` BIGINT NULL COMMENT '目标院校ID',
  `target_school_name` VARCHAR(100) NULL COMMENT '目标院校名称',
  `target_major` VARCHAR(100) NULL COMMENT '目标专业',
  `target_score` DECIMAL(6,2) NULL COMMENT '目标总分',
  `target_subject_scores` JSON NULL COMMENT '单科目标分',
  `exam_year` INT NOT NULL COMMENT '考试年度',
  `exam_date` DATE NULL COMMENT '考试日期',
  `plan_type` TINYINT NOT NULL DEFAULT 1 COMMENT '计划类型',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `remark` VARCHAR(500) NULL COMMENT '备注',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goal_user_year` (`user_id`, `exam_year`),
  KEY `idx_goal_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备考目标表';