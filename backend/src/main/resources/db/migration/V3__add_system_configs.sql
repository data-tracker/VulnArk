-- 系统配置表（KV 存储，按 category 分组）
CREATE TABLE `system_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_value` text,
  `description` varchar(255) DEFAULT NULL,
  `category` varchar(50) NOT NULL DEFAULT 'general',
  `created_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 默认配置项
INSERT INTO `system_configs` (`config_key`, `config_value`, `description`, `category`) VALUES
('site.name', 'VulnArk', '站点名称', 'general'),
('smtp.enabled', 'false', '启用邮件通知', 'email'),
('smtp.host', '', 'SMTP 服务器地址', 'email'),
('smtp.port', '587', 'SMTP 端口', 'email'),
('smtp.username', '', 'SMTP 用户名', 'email'),
('smtp.password', '', 'SMTP 密码', 'email'),
('smtp.from', '', '发件人地址', 'email'),
('smtp.ssl', 'true', '启用 SSL/TLS', 'email');
