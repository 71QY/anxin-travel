-- ========================================
-- 收藏地点增强功能 - 数据库迁移脚本
-- ========================================

-- 1. user_favorites 表新增字段（phone 和 description）
ALTER TABLE user_favorite_locations 
ADD COLUMN phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
ADD COLUMN description TEXT DEFAULT NULL COMMENT '地点简介说明';

-- 2. 创建收藏分享记录表
CREATE TABLE IF NOT EXISTS favorite_shares (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    favorite_id BIGINT NOT NULL COMMENT '收藏地点ID',
    elder_user_id BIGINT NOT NULL COMMENT '长辈用户ID(分享者)',
    guardian_user_id BIGINT NOT NULL COMMENT '亲友用户ID(接收者)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待处理, 1-已使用, 2-已过期',
    shared_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '分享时间',
    used_at TIMESTAMP NULL COMMENT '使用时间',
    INDEX idx_guardian (guardian_user_id),
    INDEX idx_elder (elder_user_id),
    INDEX idx_favorite (favorite_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏地点分享记录表';

-- 3. 创建出行记录表（行程凭证）
CREATE TABLE IF NOT EXISTS travel_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID(长辈)',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    favorite_id BIGINT DEFAULT NULL COMMENT '关联的收藏地点ID',
    destination_name VARCHAR(100) NOT NULL COMMENT '目的地名称',
    destination_address VARCHAR(500) NOT NULL COMMENT '目的地地址',
    destination_lat DOUBLE NOT NULL COMMENT '目的地纬度',
    destination_lng DOUBLE NOT NULL COMMENT '目的地经度',
    start_time TIMESTAMP NOT NULL COMMENT '出发时间',
    arrive_time TIMESTAMP NULL COMMENT '到达时间',
    duration_minutes INT DEFAULT NULL COMMENT '行程时长(分钟)',
    distance_meters INT DEFAULT NULL COMMENT '行程距离(米)',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-进行中, 1-已完成, 2-已取消',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_order (order_id),
    INDEX idx_favorite (favorite_id),
    INDEX idx_date (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出行记录表(行程凭证)';
