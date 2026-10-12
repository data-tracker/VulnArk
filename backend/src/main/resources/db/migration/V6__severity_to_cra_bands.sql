-- 严重程度按 CRA Band 标准重构：INFO/LOW/MEDIUM/HIGH/CRITICAL -> LOW/MEDIUM/HIGH/CRITICAL/EMERGENCY
-- Band 判定标准（Criteria）：
--   Emergency: KEV 或 (CVSS>=9.0 且 EPSS>0.5)
--   Critical : CVSS>=9.0 且非 KEV 且 EPSS<=0.5
--   High     : CVSS 7.0-8.9 或 EPSS>0.5
--   Medium   : CVSS 4.0-6.9 且 EPSS<=0.5 且非 KEV
--   Low      : CVSS<4.0

-- 1) 旧 INFO 先归入 LOW（新枚举不再含 INFO）
UPDATE `vulnerabilities` SET `severity` = 'LOW' WHERE `severity` = 'INFO';

-- 2) 枚举列替换
ALTER TABLE `vulnerabilities`
  MODIFY COLUMN `severity` enum('LOW','MEDIUM','HIGH','CRITICAL','EMERGENCY') NOT NULL;

-- 3) 存量按 CVSS 重算（存量数据 kev 均为 false、epss 均为空 -> 不会产生 EMERGENCY）
UPDATE `vulnerabilities` SET `severity` = CASE
  WHEN `cvss_score` >= 9.0 THEN 'CRITICAL'
  WHEN `cvss_score` >= 7.0 THEN 'HIGH'
  WHEN `cvss_score` >= 4.0 THEN 'MEDIUM'
  ELSE 'LOW'
END;
