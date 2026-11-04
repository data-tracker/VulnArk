-- V19: 审计日志批次 ID（一次编辑内的多条变更共享同一 ID，UI 折叠渲染）
ALTER TABLE `audit_log` ADD COLUMN `batch_id` VARCHAR(36) NULL COMMENT '操作批次（一次编辑/一次批量操作共享）' AFTER `ip`;
CREATE INDEX `idx_batch` ON `audit_log` (`batch_id`);
