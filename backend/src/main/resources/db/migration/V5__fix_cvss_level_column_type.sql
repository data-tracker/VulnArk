-- 修正 cvss_level 列类型：V4 误用 varchar，MySQL 方言下 Hibernate
-- 对 @Enumerated(STRING) 校验期望原生 ENUM 类型（与 severity 列一致）
ALTER TABLE `vulnerabilities`
  MODIFY COLUMN `cvss_level` enum('NONE','LOW','MEDIUM','HIGH','CRITICAL') DEFAULT NULL;
