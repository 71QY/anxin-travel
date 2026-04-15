-- ========================================
-- 亲情守护功能 - 数据库改造脚本
-- 执行时间: 2026-04-08
-- ========================================

USE anxin_travel;

-- 1. user表新增字段(兼容已执行的情况)
-- 检查并添加 is_guarded 字段
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = 'anxin_travel' AND table_name = 'user' AND column_name = 'is_guarded');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `user` ADD COLUMN `is_guarded` TINYINT DEFAULT 0 COMMENT ''是否被守护 0否 1是''', 'SELECT ''列 is_guarded 已存在''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 guard_mode 字段
SET @col_exists2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = 'anxin_travel' AND table_name = 'user' AND column_name = 'guard_mode');
SET @sql2 := IF(@col_exists2 = 0, 'ALTER TABLE `user` ADD COLUMN `guard_mode` TINYINT DEFAULT 0 COMMENT ''0普通模式 1长辈精简模式''', 'SELECT ''列 guard_mode 已存在''');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 2. order_info表新增字段(兼容已执行的情况)
-- 检查并添加 proxy_user_id 字段
SET @col_exists3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = 'anxin_travel' AND table_name = 'order_info' AND column_name = 'proxy_user_id');
SET @sql3 := IF(@col_exists3 = 0, 'ALTER TABLE `order_info` ADD COLUMN `proxy_user_id` BIGINT DEFAULT NULL COMMENT ''代叫车人ID(亲友)''', 'SELECT ''列 proxy_user_id 已存在''');
PREPARE stmt3 FROM @sql3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- 检查并添加 elder_user_id 字段
SET @col_exists4 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = 'anxin_travel' AND table_name = 'order_info' AND column_name = 'elder_user_id');
SET @sql4 := IF(@col_exists4 = 0, 'ALTER TABLE `order_info` ADD COLUMN `elder_user_id` BIGINT DEFAULT NULL COMMENT ''长辈用户ID(冗余字段)''', 'SELECT ''列 elder_user_id 已存在''');
PREPARE stmt4 FROM @sql4;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;
-- 索引也需要检查
SET @index_exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'anxin_travel' AND table_name = 'order_info' AND index_name = 'idx_proxy_user_id');
SET @sql := IF(@index_exists = 0, 'ALTER TABLE `order_info` ADD INDEX `idx_proxy_user_id` (`proxy_user_id`)', 'SELECT ''索引 idx_proxy_user_id 已存在''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists2 := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'anxin_travel' AND table_name = 'order_info' AND index_name = 'idx_elder_user_id');
SET @sql2 := IF(@index_exists2 = 0, 'ALTER TABLE `order_info` ADD INDEX `idx_elder_user_id` (`elder_user_id`)', 'SELECT ''索引 idx_elder_user_id 已存在''');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 3. 创建emergency_contact表(紧急联系人)
CREATE TABLE IF NOT EXISTS `emergency_contact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '联系人姓名',
  `phone` VARCHAR(20) NOT NULL COMMENT '联系人电话',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`) COMMENT '查询用户紧急联系人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急联系人表';

-- 4. 创建family_guard表(亲情绑定关系)
CREATE TABLE IF NOT EXISTS `family_guard` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `guardian_id` BIGINT NOT NULL COMMENT '亲友ID',
  `elder_id` BIGINT DEFAULT NULL COMMENT '长辈ID(激活后)',
  `elder_phone` VARCHAR(20) NOT NULL COMMENT '长辈手机号',
  `elder_name` VARCHAR(50) DEFAULT NULL COMMENT '长辈姓名',
  `guardian_name` VARCHAR(50) NOT NULL COMMENT '亲友姓名',
  `guardian_id_card` VARCHAR(18) NOT NULL COMMENT '亲友身份证号',
  `guardian_phone` VARCHAR(20) NOT NULL COMMENT '亲友手机号',
  `status` TINYINT DEFAULT 0 COMMENT '0待激活 1已绑定 2已解绑',
  `bind_time` DATETIME DEFAULT NULL COMMENT '绑定时间(由MyBatis-Plus自动填充)',
  `activate_time` DATETIME DEFAULT NULL COMMENT '激活时间',
  `unbind_time` DATETIME DEFAULT NULL COMMENT '解绑时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guardian_phone` (`guardian_id`, `elder_phone`) COMMENT '防止重复绑定',
  KEY `idx_elder_phone` (`elder_phone`) COMMENT '查询待激活记录',
  KEY `idx_elder_id` (`elder_id`) COMMENT '查询长辈的亲友列表',
  KEY `idx_guardian_id` (`guardian_id`) COMMENT '查询亲友的长辈列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='亲情守护绑定关系表';

-- 5. 创建order_chat表(订单群聊)
CREATE TABLE IF NOT EXISTS `order_chat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID(群聊维度)',
  `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
  `sender_type` TINYINT NOT NULL COMMENT '1长辈 2亲友 3司机',
  `message_type` TINYINT DEFAULT 1 COMMENT '1文字 2语音 3快捷短语',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`) COMMENT '查询订单聊天记录',
  KEY `idx_sender_id` (`sender_id`) COMMENT '查询用户发送记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单群聊消息表';

-- ========================================
-- 验证脚本执行结果
-- ========================================
-- SELECT * FROM information_schema.COLUMNS WHERE TABLE_NAME = 'user' AND COLUMN_NAME IN ('is_guarded', 'guard_mode');
-- SELECT * FROM information_schema.COLUMNS WHERE TABLE_NAME = 'order_info' AND COLUMN_NAME IN ('proxy_user_id', 'elder_user_id');
-- SHOW TABLES LIKE 'family_guard';
-- SHOW TABLES LIKE 'order_chat';
-- SHOW TABLES LIKE 'emergency_contact';
