-- 漏洞分析状态变更审计日志
-- 记录每次分析状态变化（谁/何时/从什么改成什么），供周报统计任意时段分析量
CREATE TABLE IF NOT EXISTS `vuln_analysis_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `vuln_id` BIGINT NOT NULL COMMENT '漏洞ID',
  `cve_id` VARCHAR(20) COMMENT 'CVE编号（冗余，便于直接查询）',
  `asset_id` BIGINT COMMENT '资产ID',
  `old_status` VARCHAR(20) COMMENT '原分析状态（NULL=未分析）',
  `new_status` VARCHAR(20) NOT NULL COMMENT '新分析状态',
  `changed_by` BIGINT COMMENT '操作人用户ID',
  `changed_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '变更时间',
  KEY `idx_changed_at` (`changed_at`),
  KEY `idx_vuln` (`vuln_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漏洞分析状态变更日志';
