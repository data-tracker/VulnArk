-- V2 初始数据：默认账号
-- 账号 admin/manager/analyst/viewer，密码均为 Admin123456，首次登录后请立即修改
-- 密码哈希由应用注册流程生成并验证有效（原 db/data.sql 中的哈希与文档密码不符，已废弃）

INSERT INTO `users` VALUES (1,NULL,'2026-09-07 15:31:43.522103',_binary '\0',NULL,'admin@vulnark.com','系统管理员','2026-09-08 10:47:26.849433',NULL,'$2a$10$jOcmDntiHTglmVfyFL34TewAAHv2IJTEICBsAP2vCbesej3FFUk0C',NULL,NULL,'ADMIN','ACTIVE','2026-09-08 10:47:26.909935','admin'),(3,NULL,'2026-09-07 15:31:43.522103',_binary '\0',NULL,'analyst@vulnark.com','安全分析师',NULL,NULL,'$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9tYjKUznkNvAWGG',NULL,NULL,'ANALYST','ACTIVE',NULL,'analyst'),(2,NULL,'2026-09-07 15:31:43.522103',_binary '\0',NULL,'manager@vulnark.com','项目经理',NULL,NULL,'$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9tYjKUznkNvAWGG',NULL,NULL,'MANAGER','ACTIVE',NULL,'manager'),(4,NULL,'2026-09-07 15:31:43.522103',_binary '\0',NULL,'viewer@vulnark.com','查看者',NULL,NULL,'$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9tYjKUznkNvAWGG',NULL,NULL,'VIEWER','ACTIVE',NULL,'viewer');

-- 默认项目（VulnerabilityService 在漏洞未指定项目时回退到 projectId=1）
INSERT INTO `projects` (`id`, `created_time`, `updated_time`, `name`, `priority`, `status`, `description`)
VALUES (1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), '默认项目', 'MEDIUM', 'ACTIVE', '系统默认项目，未指定项目的漏洞将归入此处');
