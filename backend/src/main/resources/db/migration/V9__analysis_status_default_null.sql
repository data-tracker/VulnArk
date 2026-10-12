-- 分析状态改为"空 = 未分析"语义：
-- 默认值受影响 -> 默认 NULL（未填写即未分析，人工分析后置三态）
-- 存量的"受影响"均为导入/迁移默认值产物（非人工判定），一并回到未分析；
-- 人工判定过的误报保留
ALTER TABLE `vulnerabilities`
  MODIFY COLUMN `verification_status` enum('AFFECTED','NOT_AFFECTED','FALSE_POSITIVE') DEFAULT NULL;

UPDATE `vulnerabilities` SET `verification_status` = NULL WHERE `verification_status` = 'AFFECTED';
