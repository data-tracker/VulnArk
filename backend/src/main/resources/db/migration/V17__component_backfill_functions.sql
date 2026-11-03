-- V17: 组件字段回填（函数式，幂等）
-- V16 中的 LIKE 回填因模式匹配异常不生效（本机实测），此迁移用字符串函数重写；
-- 已回填的环境（component_name 非空）执行为 0 条，幂等安全。
UPDATE `vulnerabilities`
SET `component_name`   = SUBSTRING_INDEX(SUBSTRING_INDEX(`title`, ' (', -1), '@', 1),
    `component_version` = SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(`title`, ' (', -1), '@', -1), ')', 1)
WHERE `deleted` = 0
  AND `component_name` IS NULL
  AND INSTR(`title`, ' (') > 0
  AND INSTR(SUBSTRING_INDEX(`title`, ' (', -1), '@') > 0;
