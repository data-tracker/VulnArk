-- V18: 审计日志（通用表，一级六类动作 + 二级预留）+ 用户最后活跃时间
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT COMMENT '操作人ID（0=SYSTEM 系统自动）',
  `user_name` VARCHAR(60) NOT NULL COMMENT '操作人名（冗余，人员删除后仍可读）',
  `action` VARCHAR(30) NOT NULL COMMENT '动作：ANALYSIS_CHANGE/STATUS_CHANGE/ASSIGNEE_CHANGE/TICKET_CHANGE/ASSET_CREATE/ASSET_UPDATE/ASSET_DELETE/AUTO_RESOLVE；二级预留：LOGIN/LOGOUT/EXPORT/BATCH_*',
  `object_type` VARCHAR(20) NOT NULL COMMENT '对象类型：VULNERABILITY/ASSET/USER/...',
  `object_id` BIGINT COMMENT '对象ID',
  `object_label` VARCHAR(120) COMMENT '对象可读标识（CVE号/资产名）',
  `field` VARCHAR(40) COMMENT '变更字段',
  `old_value` VARCHAR(200) COMMENT '旧值（NULL 表示无/未分析）',
  `new_value` VARCHAR(200) COMMENT '新值',
  `detail` TEXT COMMENT '扩展信息 JSON（批量明细清单等；二级 IP 等）',
  `ip` VARCHAR(45) COMMENT '操作来源 IP（二级预留）',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_object` (`object_type`, `object_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志（只增不改）';

ALTER TABLE `users` ADD COLUMN `last_active_at` DATETIME(6) NULL COMMENT '最后活跃时间（JWT 过滤器 60s 节流刷新）';
