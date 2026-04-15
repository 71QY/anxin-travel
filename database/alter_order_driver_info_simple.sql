-- ========================================
-- 订单表添加司机信息字段（简化版）
-- 执行时间: 2026-04-15
-- ========================================

-- 直接添加字段（如果已存在会报错，可忽略）
ALTER TABLE `order_info` ADD COLUMN `driver_name` VARCHAR(50) DEFAULT NULL COMMENT '司机姓名';
ALTER TABLE `order_info` ADD COLUMN `driver_phone` VARCHAR(20) DEFAULT NULL COMMENT '司机电话';
ALTER TABLE `order_info` ADD COLUMN `car_no` VARCHAR(20) DEFAULT NULL COMMENT '车牌号';
ALTER TABLE `order_info` ADD COLUMN `car_type` VARCHAR(50) DEFAULT NULL COMMENT '车型';
ALTER TABLE `order_info` ADD COLUMN `car_color` VARCHAR(10) DEFAULT NULL COMMENT '车辆颜色';
ALTER TABLE `order_info` ADD COLUMN `rating` DECIMAL(2,1) DEFAULT NULL COMMENT '司机评分';
ALTER TABLE `order_info` ADD COLUMN `driver_lat` DOUBLE DEFAULT NULL COMMENT '司机当前纬度';
ALTER TABLE `order_info` ADD COLUMN `driver_lng` DOUBLE DEFAULT NULL COMMENT '司机当前经度';

-- 添加索引
ALTER TABLE `order_info` ADD INDEX `idx_driver_status` (`driver_id`, `status`);

SELECT '✅ 订单表司机信息字段添加完成' AS result;
