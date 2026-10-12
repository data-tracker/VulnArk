-- 清理 data.sql 时代遗留的双编码乱码项目（摆设数据，无漏洞挂载），
-- 并确保存在内部"默认项目"（漏洞未指定项目时的自动归属）
-- 注意：乱码项目行被 project_members 引用（同为摆设数据），需先清子表
DELETE pm FROM `project_members` pm
JOIN `projects` p ON pm.project_id = p.id
WHERE p.name LIKE '%å%' OR p.name LIKE '%ç%';

DELETE FROM `projects` WHERE `name` LIKE '%å%' OR `name` LIKE '%ç%';

INSERT INTO `projects` (`created_time`, `updated_time`, `name`, `priority`, `status`, `description`)
SELECT CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), '默认项目', 'MEDIUM', 'ACTIVE',
       '内部默认项目：漏洞未指定项目时自动归属（项目维度已从表单移除）'
WHERE NOT EXISTS (SELECT 1 FROM `projects` WHERE `name` = '默认项目');
