-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS anxin_travel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE anxin_travel;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `phone` VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号',
    `password` VARCHAR(100) COMMENT '密码',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `emergency_contact_name` VARCHAR(20) COMMENT '紧急联系人姓名',
    `emergency_contact_phone` VARCHAR(20) COMMENT '紧急联系人电话',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 司机表
CREATE TABLE IF NOT EXISTS `driver` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '司机ID',
    `phone` VARCHAR(20) NOT NULL UNIQUE COMMENT '司机手机号',
    `name` VARCHAR(50) NOT NULL COMMENT '司机姓名',
    `license_plate` VARCHAR(10) NOT NULL COMMENT '车牌号',
    `car_brand` VARCHAR(20) COMMENT '车辆品牌',
    `car_model` VARCHAR(20) COMMENT '车型',
    `car_color` VARCHAR(10) COMMENT '车辆颜色',
    `rating` DECIMAL(2,1) DEFAULT 5.0 COMMENT '评分',
    `status` TINYINT DEFAULT 0 COMMENT '0休息 1空闲 2接单中 3行程中',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='司机表';

-- 订单表
CREATE TABLE IF NOT EXISTS `order_info` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `driver_id` BIGINT COMMENT '司机ID',
    `start_lat` DOUBLE COMMENT '起点纬度',
    `start_lng` DOUBLE COMMENT '起点经度',
    `dest_lat` DOUBLE NOT NULL COMMENT '终点纬度',
    `dest_lng` DOUBLE NOT NULL COMMENT '终点经度',
    `dest_address` VARCHAR(255) NOT NULL COMMENT '终点地址',
    `status` TINYINT DEFAULT 0 COMMENT '0待接单 1已接单 2行程中 3已完成 4已取消',
    `platform_used` VARCHAR(20) COMMENT '使用的打车平台',
    `platform_order_id` VARCHAR(64) COMMENT '第三方平台订单号',
    `estimate_price` DECIMAL(10,2) COMMENT '预估价格',
    `actual_price` DECIMAL(10,2) COMMENT '实际价格',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
    FOREIGN KEY (`driver_id`) REFERENCES `driver`(`id`) ON DELETE SET NULL,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 插入一条司机测试数据
INSERT INTO driver (phone, name, license_plate, car_brand, car_model, car_color, status)
VALUES ('13800138888', '王师傅', '京A12345', '大众', '朗逸', '白色', 1);