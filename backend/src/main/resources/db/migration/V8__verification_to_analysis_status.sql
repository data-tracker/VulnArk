-- 验证状态升级为 VEX 语义的分析状态：
-- 待验证(PENDING)/已验证(VERIFIED) -> 受影响(AFFECTED)
-- 新增：不受影响(NOT_AFFECTED)；误报(FALSE_POSITIVE)保留
-- 默认受影响（保守原则：未分析视为受影响，人工分析后降级改判）

-- 1) 枚举先扩展为新旧并集（容纳旧值迁移）
ALTER TABLE `vulnerabilities`
  MODIFY COLUMN `verification_status` enum('PENDING','VERIFIED','FALSE_POSITIVE','AFFECTED','NOT_AFFECTED') DEFAULT 'AFFECTED';

-- 2) 旧值映射：PENDING/VERIFIED 均为"确认真实漏洞" -> AFFECTED
UPDATE `vulnerabilities`
  SET `verification_status` = 'AFFECTED'
  WHERE `verification_status` IN ('PENDING','VERIFIED');

-- 3) 收缩为最终三态
ALTER TABLE `vulnerabilities`
  MODIFY COLUMN `verification_status` enum('AFFECTED','NOT_AFFECTED','FALSE_POSITIVE') DEFAULT 'AFFECTED';
