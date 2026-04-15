-- ========================================
-- 亲情守护 - 私聊功能数据库表
-- ========================================

-- 创建私聊消息表
CREATE TABLE IF NOT EXISTS `private_chat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sender_id` BIGINT NOT NULL COMMENT '发送者用户ID',
  `receiver_id` BIGINT NOT NULL COMMENT '接收者用户ID',
  `message_type` TINYINT DEFAULT 1 COMMENT '1文字 2语音 3图片',
  `content` TEXT NOT NULL COMMENT '消息内容(文字或语音/图片URL)',
  `is_read` TINYINT DEFAULT 0 COMMENT '是否已读 0未读 1已读',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_sender_receiver` (`sender_id`, `receiver_id`) COMMENT '查询两人聊天记录',
  KEY `idx_receiver_unread` (`receiver_id`, `is_read`) COMMENT '查询未读消息'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='亲情守护私聊消息表';
