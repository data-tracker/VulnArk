-- V15: 多渠道来源体系 + 组件结构化 + 工单关联 + BOM 静默积累
-- 设计蓝图见 PR 描述；存量清洗：全部归 DT_SYNC/SCA（Excel 与同步均源自 DTrack）

-- 1. 新字段
ALTER TABLE `vulnerabilities`
  ADD COLUMN `discovery_channel` VARCHAR(30) NULL COMMENT '发现渠道：SCA/SCANNER/PENTEST/CUSTOMER_FEEDBACK/INTERNAL_REPORT/EXTERNAL_REPORT/ADVISORY/MANUAL_REVIEW' AFTER `source`,
  ADD COLUMN `discovery_detail` VARCHAR(200) NULL COMMENT '渠道明细（工具名/客户名/通告出处）' AFTER `discovery_channel`,
  ADD COLUMN `component_name` VARCHAR(120) NULL COMMENT '主组件名（结构化）' AFTER `discovery_detail`,
  ADD COLUMN `component_version` VARCHAR(60) NULL COMMENT '主组件版本' AFTER `component_name`,
  ADD COLUMN `ticket_key` VARCHAR(40) NULL COMMENT '工单编号（通用，跨工单系统）' AFTER `component_version`;

-- 2. source 洗枚举：API/NULL -> DT_SYNC（存量全部为 DTrack 管辖）
UPDATE `vulnerabilities` SET `source` = 'API' WHERE `source` IS NULL;
ALTER TABLE `vulnerabilities` MODIFY COLUMN `source` ENUM('MANUAL','SCAN','IMPORT','API','DT_SYNC') NULL;
UPDATE `vulnerabilities` SET `source` = 'DT_SYNC' WHERE `source` = 'API';

-- 3. 渠道回填：存量全部为组件分析发现
UPDATE `vulnerabilities` SET `discovery_channel` = 'SCA' WHERE `discovery_channel` IS NULL;

-- 4. 组件回填：从 title 反解 " (name@version)" 后缀
UPDATE `vulnerabilities`
SET `component_name` = SUBSTRING_INDEX(SUBSTRING_INDEX(`title`, ' (', -1), '@', 1),
    `component_version` = SUBSTRING_INDEX(SUBSTRING_INDEX(`title`, '@', -1), ')', 1)
WHERE `title` LIKE '% (@%)'
  AND `component_name` IS NULL;

-- 5. BOM 表（资产-组件清单，同步静默积累，界面二期）
CREATE TABLE `asset_components` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `asset_id` BIGINT NOT NULL,
  `component_name` VARCHAR(120) NOT NULL,
  `component_version` VARCHAR(60) NULL,
  `component_uuid` VARCHAR(64) NULL COMMENT 'DTrack 组件 UUID',
  `source` VARCHAR(20) NULL COMMENT 'BOM 来源（DT_SYNC）',
  `last_seen` DATE NULL COMMENT '最近一次同步确认存在',
  `created_time` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  `updated_time` DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_comp` (`asset_id`, `component_name`, `component_version`),
  KEY `idx_comp_name` (`component_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产组件清单（BOM）';

-- 6. 工单系统地址配置
INSERT INTO `system_configs` (`config_key`, `config_value`, `description`, `category`) VALUES
('ticket.base_url', '', '工单系统地址（用于工单编号跳转链接）', 'general');
