-- 用户收藏地点表
CREATE TABLE IF NOT EXISTS `user_favorite_locations` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '地点名称（如：家、公司）',
    `address` VARCHAR(500) NOT NULL COMMENT '详细地址',
    `latitude` DOUBLE NOT NULL COMMENT '纬度',
    `longitude` DOUBLE NOT NULL COMMENT '经度',
    `type` VARCHAR(20) DEFAULT 'CUSTOM' COMMENT '类型：HOME, COMPANY, HOSPITAL, CUSTOM',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏地点表';
