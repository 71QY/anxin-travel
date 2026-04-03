-- 检查并修复数据库字符集问题
-- 执行前请确认数据库名 anxin_travel

USE anxin_travel;

-- 1. 查看当前数据库字符集设置
SHOW VARIABLES LIKE 'character_set_database';
SHOW VARIABLES LIKE 'collation_database';

-- 2. 查看表的字符集
SHOW TABLE STATUS WHERE Name = 'order_info';

-- 3. 查看字段的字符集
SHOW FULL COLUMNS FROM order_info WHERE Field = 'dest_address';

-- 4. 修复表和字段的字符集排序规则（重要！）
ALTER TABLE order_info CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 5. 确保所有 varchar 字段都是 utf8mb4
ALTER TABLE order_info MODIFY order_no VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号';
ALTER TABLE order_info MODIFY dest_address VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '终点地址';
ALTER TABLE order_info MODIFY platform_used VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的打车平台';
ALTER TABLE order_info MODIFY platform_order_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方平台订单号';

-- 6. 查看当前连接的字符集
SHOW VARIABLES LIKE 'character_set_client';
SHOW VARIABLES LIKE 'character_set_connection';
SHOW VARIABLES LIKE 'character_set_results';
