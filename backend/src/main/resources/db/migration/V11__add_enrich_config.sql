-- EPSS/KEV 自动补全开关（从 FIRST/CISA 原始数据源补全，DTrack 不提供这两个字段）
INSERT INTO `system_configs` (`config_key`, `config_value`, `description`, `category`) VALUES
('dtrack.enrich', 'true', '同步后从 FIRST(EPSS)/CISA(KEV) 自动补全风险字段', 'dtrack');
