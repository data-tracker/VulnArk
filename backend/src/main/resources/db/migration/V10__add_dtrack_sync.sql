-- Dependency-Track 定期同步：日志表 + 配置项
CREATE TABLE `sync_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sync_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `trigger_type` varchar(10) NOT NULL DEFAULT 'MANUAL',
  `added` int NOT NULL DEFAULT 0,
  `updated` int NOT NULL DEFAULT 0,
  `resolved` int NOT NULL DEFAULT 0,
  `failed` int NOT NULL DEFAULT 0,
  `detail` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `system_configs` (`config_key`, `config_value`, `description`, `category`) VALUES
('dtrack.url', '', 'Dependency-Track API 地址', 'dtrack'),
('dtrack.api_key', '', 'Dependency-Track API Key', 'dtrack'),
('dtrack.mapping', '{}', '项目映射白名单（DTrack 项目名 -> 资产名，JSON）', 'dtrack'),
('dtrack.frequency', 'daily', '同步频率：hourly / daily / off', 'dtrack'),
('dtrack.auto_resolve', 'true', 'DTrack 不再报告时自动将待处理置为已解决', 'dtrack'),
('dtrack.refresh_fields', 'true', '刷新风险字段并重算 Band（仅对 API 同步创建的漏洞）', 'dtrack');
