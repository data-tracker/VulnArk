-- 同步邮件通知配置
INSERT INTO `system_configs` (`config_key`, `config_value`, `description`, `category`) VALUES
('notify.enabled', 'false', '同步完成后发送邮件通知（有变更时才发）', 'notify'),
('notify.recipients', '', '收件人邮箱列表（逗号分隔）', 'notify');
