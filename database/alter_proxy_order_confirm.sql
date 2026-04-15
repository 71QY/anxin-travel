-- ========================================
-- 帮长辈叫车（需确认）功能 - 数据库改造
-- 执行时间: 2026-04-11
-- ========================================

USE anxin_travel;

-- 1. order_info表新增字段
-- 检查并添加 confirm_time 字段
SET @col_exists1 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = 'anxin_travel' 
                     AND table_name = 'order_info' 
                     AND column_name = 'confirm_time');
SET @sql1 := IF(@col_exists1 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `confirm_time` DATETIME DEFAULT NULL COMMENT ''确认时间''', 
    'SELECT ''列 confirm_time 已存在''');
PREPARE stmt1 FROM @sql1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- 检查并添加 reject_reason 字段
SET @col_exists2 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = 'anxin_travel' 
                     AND table_name = 'order_info' 
                     AND column_name = 'reject_reason');
SET @sql2 := IF(@col_exists2 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT ''拒绝原因''', 
    'SELECT ''列 reject_reason 已存在''');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 2. 更新订单状态说明
-- status字段含义：
-- 0: 待确认（新状态，等待长辈确认）
-- 1: 已确认/待接单（长辈已同意，等待司机接单）
-- 2: 司机已接单
-- 3: 行程中
-- 4: 已完成
-- 5: 已取消
-- 6: 已拒绝（新状态，长辈拒绝了代叫车请求）

-- 3. 验证字段是否添加成功
SELECT 
    COLUMN_NAME,
    COLUMN_TYPE,
    COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE table_schema = 'anxin_travel'
  AND table_name = 'order_info'
  AND COLUMN_NAME IN ('confirm_time', 'reject_reason')
ORDER BY ORDINAL_POSITION;
