-- ========================================
-- 亲情守护功能 v2.0 - 身份证唯一性改造
-- 执行时间: 2026-04-10
-- ========================================

USE anxin_travel;

-- 1. 确保 user 表有 real_name 和 id_card 字段
-- 检查并添加 real_name 字段
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                    WHERE table_schema = 'anxin_travel' 
                    AND table_name = 'user' 
                    AND column_name = 'real_name');
SET @sql := IF(@col_exists = 0, 
    'ALTER TABLE `user` ADD COLUMN `real_name` VARCHAR(50) DEFAULT NULL COMMENT ''真实姓名''', 
    'SELECT ''列 real_name 已存在''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 id_card 字段
SET @col_exists2 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = 'anxin_travel' 
                     AND table_name = 'user' 
                     AND column_name = 'id_card');
SET @sql2 := IF(@col_exists2 = 0, 
    'ALTER TABLE `user` ADD COLUMN `id_card` VARCHAR(18) DEFAULT NULL COMMENT ''身份证号''', 
    'SELECT ''列 id_card 已存在''');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 2. 【关键】为 id_card 字段添加唯一索引（一个身份证只能绑定一个账号）
-- 先删除可能存在的旧索引
SET @index_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                      WHERE table_schema = 'anxin_travel' 
                      AND table_name = 'user' 
                      AND index_name = 'uk_user_id_card');
SET @sql3 := IF(@index_exists > 0, 
    'ALTER TABLE `user` DROP INDEX `uk_user_id_card`', 
    'SELECT ''索引 uk_user_id_card 不存在，跳过删除''');
PREPARE stmt3 FROM @sql3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- 添加唯一索引（允许 NULL，但非 NULL 值必须唯一）
ALTER TABLE `user` ADD UNIQUE INDEX `uk_user_id_card` (`id_card`);

-- 3. family_guard 表新增 elder_id_card 字段（用于绑定校验）
SET @col_exists3 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = 'anxin_travel' 
                     AND table_name = 'family_guard' 
                     AND column_name = 'elder_id_card');
SET @sql4 := IF(@col_exists3 = 0, 
    'ALTER TABLE `family_guard` ADD COLUMN `elder_id_card` VARCHAR(18) DEFAULT NULL COMMENT ''长辈身份证号''', 
    'SELECT ''列 elder_id_card 已存在''');
PREPARE stmt4 FROM @sql4;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

-- ========================================
-- 验证脚本执行结果
-- ========================================
-- SELECT * FROM information_schema.COLUMNS WHERE TABLE_NAME = 'user' AND COLUMN_NAME IN ('real_name', 'id_card');
-- SHOW INDEX FROM `user` WHERE Key_name = 'uk_user_id_card';
-- SELECT * FROM information_schema.COLUMNS WHERE TABLE_NAME = 'family_guard' AND COLUMN_NAME = 'elder_id_card';
