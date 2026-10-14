-- 同步明细超长修复：detail 列 TEXT(64KB) -> LONGTEXT(4GB)
-- 两资产全量比对时明细 JSON 可超 64KB（新增+更新各含全部 CVE 明细）
ALTER TABLE `sync_logs` MODIFY COLUMN `detail` LONGTEXT;
