-- ========================================
-- 新用户注册完整性校验 - 数据库改造
-- 执行时间: 2026-04-11
-- ========================================

USE anxin_travel;

-- 1. 添加 is_completed 字段（账号是否完善）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                    WHERE table_schema = 'anxin_travel' 
                    AND table_name = 'user' 
                    AND column_name = 'is_completed');
SET @sql := IF(@col_exists = 0, 
    'ALTER TABLE `user` ADD COLUMN `is_completed` TINYINT DEFAULT 0 COMMENT ''账号是否完善：0-未完善 1-已完善''', 
    'SELECT ''列 is_completed 已存在''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 为已有用户批量更新 is_completed 状态
-- 如果密码和昵称都不为空，则标记为已完善
UPDATE `user` 
SET `is_completed` = 1 
WHERE `password` IS NOT NULL 
  AND `password` != '' 
  AND `nickname` IS NOT NULL 
  AND `nickname` != '';

-- 3. 如果密码或昵称为空，标记为未完善
UPDATE `user` 
SET `is_completed` = 0 
WHERE `password` IS NULL 
   OR `password` = '' 
   OR `nickname` IS NULL 
   OR `nickname` = '';

-- 4. 验证更新结果
SELECT 
    id, 
    phone, 
    nickname, 
    CASE WHEN password IS NOT NULL AND password != '' THEN '已设置' ELSE '未设置' END as password_status,
    is_completed
FROM `user`
ORDER BY id;
