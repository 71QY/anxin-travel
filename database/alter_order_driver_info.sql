-- ========================================
-- 订单表添加司机信息字段
-- 执行时间: 2026-04-15
-- ========================================

-- 1. 添加司机姓名字段
SET @col_exists1 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'driver_name');
SET @sql1 := IF(@col_exists1 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `driver_name` VARCHAR(50) DEFAULT NULL COMMENT ''司机姓名''', 
    'SELECT ''列 driver_name 已存在''');
PREPARE stmt1 FROM @sql1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- 2. 添加司机电话字段
SET @col_exists2 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'driver_phone');
SET @sql2 := IF(@col_exists2 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `driver_phone` VARCHAR(20) DEFAULT NULL COMMENT ''司机电话''', 
    'SELECT ''列 driver_phone 已存在''');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 3. 添加车牌号字段
SET @col_exists3 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'car_no');
SET @sql3 := IF(@col_exists3 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `car_no` VARCHAR(20) DEFAULT NULL COMMENT ''车牌号''', 
    'SELECT ''列 car_no 已存在''');
PREPARE stmt3 FROM @sql3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- 4. 添加车型字段
SET @col_exists4 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'car_type');
SET @sql4 := IF(@col_exists4 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `car_type` VARCHAR(50) DEFAULT NULL COMMENT ''车型''', 
    'SELECT ''列 car_type 已存在''');
PREPARE stmt4 FROM @sql4;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

-- 5. 添加车辆颜色字段
SET @col_exists5 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'car_color');
SET @sql5 := IF(@col_exists5 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `car_color` VARCHAR(10) DEFAULT NULL COMMENT ''车辆颜色''', 
    'SELECT ''列 car_color 已存在''');
PREPARE stmt5 FROM @sql5;
EXECUTE stmt5;
DEALLOCATE PREPARE stmt5;

-- 6. 添加司机评分字段
SET @col_exists6 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'rating');
SET @sql6 := IF(@col_exists6 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `rating` DECIMAL(2,1) DEFAULT NULL COMMENT ''司机评分''', 
    'SELECT ''列 rating 已存在''');
PREPARE stmt6 FROM @sql6;
EXECUTE stmt6;
DEALLOCATE PREPARE stmt6;

-- 7. 添加司机纬度字段
SET @col_exists7 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'driver_lat');
SET @sql7 := IF(@col_exists7 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `driver_lat` DOUBLE DEFAULT NULL COMMENT ''司机当前纬度''', 
    'SELECT ''列 driver_lat 已存在''');
PREPARE stmt7 FROM @sql7;
EXECUTE stmt7;
DEALLOCATE PREPARE stmt7;

-- 8. 添加司机经度字段
SET @col_exists8 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
                     WHERE table_schema = DATABASE() 
                     AND table_name = 'order_info' 
                     AND column_name = 'driver_lng');
SET @sql8 := IF(@col_exists8 = 0, 
    'ALTER TABLE `order_info` ADD COLUMN `driver_lng` DOUBLE DEFAULT NULL COMMENT ''司机当前经度''', 
    'SELECT ''列 driver_lng 已存在''');
PREPARE stmt8 FROM @sql8;
EXECUTE stmt8;
DEALLOCATE PREPARE stmt8;

-- 9. 添加索引优化查询
SET @index_exists := (SELECT COUNT(*) FROM information_schema.statistics 
                      WHERE table_schema = DATABASE() 
                      AND table_name = 'order_info' 
                      AND index_name = 'idx_driver_status');
SET @sql9 := IF(@index_exists = 0, 
    'ALTER TABLE `order_info` ADD INDEX `idx_driver_status` (`driver_id`, `status`)', 
    'SELECT ''索引 idx_driver_status 已存在''');
PREPARE stmt9 FROM @sql9;
EXECUTE stmt9;
DEALLOCATE PREPARE stmt9;

SELECT '✅ 订单表司机信息字段添加完成' AS result;
