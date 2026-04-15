-- ========================================
-- Anxin Travel - Complete Database Script
-- Version: v2.0 (with Family Guard feature)
-- Created: 2026-04-08
-- Description: Includes AI agent, order system, family guard and all features
-- Usage: mysql -u root -p < anxin_travel_complete.sql
-- ========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Drop and create database
DROP DATABASE IF EXISTS `anxin_travel`;
CREATE DATABASE `anxin_travel` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `anxin_travel`;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `phone` varchar(20) NOT NULL COMMENT 'Phone number',
  `password` varchar(100) DEFAULT NULL COMMENT 'Password (BCrypt encrypted)',
  `nickname` varchar(50) DEFAULT NULL COMMENT 'Nickname',
  `avatar` varchar(255) DEFAULT NULL COMMENT 'Avatar URL',
  `emergency_contact_name` varchar(20) DEFAULT NULL COMMENT 'Emergency contact name',
  `emergency_contact_phone` varchar(20) DEFAULT NULL COMMENT 'Emergency contact phone',
  `real_name` varchar(50) DEFAULT NULL COMMENT 'Real name',
  `id_card` varchar(18) DEFAULT NULL COMMENT 'ID card number',
  `verified` tinyint DEFAULT '0' COMMENT 'Real-name verified: 0-no 1-yes',
  `is_guarded` tinyint DEFAULT '0' COMMENT 'Is guarded: 0-no 1-yes',
  `guard_mode` tinyint DEFAULT '0' COMMENT 'Mode: 0-normal 1-elder mode',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_phone` (`phone`),
  KEY `idx_guard_mode` (`guard_mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User table';

-- ----------------------------
-- Table structure for driver
-- ----------------------------
DROP TABLE IF EXISTS `driver`;
CREATE TABLE `driver` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Driver ID',
  `phone` varchar(20) NOT NULL COMMENT 'Driver phone',
  `name` varchar(50) NOT NULL COMMENT 'Driver name',
  `license_plate` varchar(10) NOT NULL COMMENT 'License plate',
  `car_brand` varchar(20) DEFAULT NULL COMMENT 'Car brand',
  `car_model` varchar(20) DEFAULT NULL COMMENT 'Car model',
  `car_color` varchar(10) DEFAULT NULL COMMENT 'Car color',
  `rating` decimal(2,1) DEFAULT '5.0' COMMENT 'Rating',
  `status` tinyint DEFAULT '0' COMMENT 'Status: 0-rest 1-available 2-busy 3-ontrip',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Driver table';

-- ----------------------------
-- Table structure for order_info
-- ----------------------------
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Order ID',
  `order_no` varchar(32) NOT NULL COMMENT 'Order number',
  `user_id` bigint NOT NULL COMMENT 'User ID (passenger/elder)',
  `driver_id` bigint DEFAULT NULL COMMENT 'Driver ID',
  `proxy_user_id` bigint DEFAULT NULL COMMENT 'Proxy user ID (family member)',
  `elder_user_id` bigint DEFAULT NULL COMMENT 'Elder user ID (redundant field)',
  `start_lat` double DEFAULT NULL COMMENT 'Start latitude',
  `start_lng` double DEFAULT NULL COMMENT 'Start longitude',
  `dest_lat` double NOT NULL COMMENT 'Destination latitude',
  `dest_lng` double NOT NULL COMMENT 'Destination longitude',
  `dest_address` varchar(255) NOT NULL COMMENT 'Destination address',
  `status` tinyint DEFAULT '0' COMMENT 'Status: 0-pending 1-accepted 2-ontrip 3-completed 4-cancelled',
  `platform_used` varchar(20) DEFAULT NULL COMMENT 'Platform used',
  `platform_order_id` varchar(64) DEFAULT NULL COMMENT 'Third-party platform order ID',
  `estimate_price` decimal(10,2) DEFAULT NULL COMMENT 'Estimated price',
  `actual_price` decimal(10,2) DEFAULT NULL COMMENT 'Actual price',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_driver_id` (`driver_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_proxy_user_id` (`proxy_user_id`),
  KEY `idx_elder_user_id` (`elder_user_id`),
  KEY `idx_status_time` (`status`,`create_time`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_order_driver` FOREIGN KEY (`driver_id`) REFERENCES `driver` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order table';

-- ----------------------------
-- Table structure for family_guard
-- ----------------------------
DROP TABLE IF EXISTS `family_guard`;
CREATE TABLE `family_guard` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `guardian_id` bigint NOT NULL COMMENT 'Guardian (family member) ID',
  `elder_id` bigint DEFAULT NULL COMMENT 'Elder ID (after activation)',
  `elder_phone` varchar(20) NOT NULL COMMENT 'Elder phone number',
  `elder_name` varchar(50) DEFAULT NULL COMMENT 'Elder name',
  `guardian_name` varchar(50) NOT NULL COMMENT 'Guardian name',
  `guardian_id_card` varchar(18) NOT NULL COMMENT 'Guardian ID card number',
  `guardian_phone` varchar(20) NOT NULL COMMENT 'Guardian phone number',
  `status` tinyint DEFAULT '0' COMMENT 'Status: 0-pending 1-active 2-unbound',
  `bind_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Bind time',
  `activate_time` datetime DEFAULT NULL COMMENT 'Activation time',
  `unbind_time` datetime DEFAULT NULL COMMENT 'Unbind time',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guardian_phone` (`guardian_id`,`elder_phone`) COMMENT 'Prevent duplicate binding',
  KEY `idx_elder_phone` (`elder_phone`) COMMENT 'Query pending records',
  KEY `idx_elder_id` (`elder_id`) COMMENT 'Query elder guardians',
  KEY `idx_guardian_id` (`guardian_id`) COMMENT 'Query guardian elders'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Family guard binding table';

-- ----------------------------
-- Table structure for order_chat
-- ----------------------------
DROP TABLE IF EXISTS `order_chat`;
CREATE TABLE `order_chat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `order_id` bigint NOT NULL COMMENT 'Order ID (chat group dimension)',
  `sender_id` bigint NOT NULL COMMENT 'Sender ID',
  `sender_type` tinyint NOT NULL COMMENT 'Sender type: 1-elder 2-guardian 3-driver',
  `message_type` tinyint DEFAULT '1' COMMENT 'Message type: 1-text 2-voice 3-quickphrase',
  `content` text NOT NULL COMMENT 'Message content',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Send time',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`) COMMENT 'Query order chat history',
  KEY `idx_sender_id` (`sender_id`) COMMENT 'Query user messages'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order chat message table';

-- ----------------------------
-- Initialize test data
-- ----------------------------

-- Test driver
INSERT INTO `driver` VALUES 
(1,'13800138888','Driver Wang','京A12345','Volkswagen','Lavida','White',5.0,1,NOW());

-- Test user
INSERT INTO `user` VALUES 
(1,'13800138000',NULL,'Test User',NULL,NULL,NULL,NULL,NULL,0,0,0,NOW(),NOW());

-- Test order
INSERT INTO `order_info` VALUES 
(1,'AX20260408001',1,NULL,NULL,NULL,39.9042,116.4074,31.2304,121.4737,'Shanghai Pudong',4,'gaode',NULL,30.00,NULL,NOW(),NOW());

SET FOREIGN_KEY_CHECKS = 1;

-- ========================================
-- Database script execution completed
-- Version: v2.0
-- Features included:
--   1. User system (with family guard support)
--   2. Driver management
--   3. Order system (with proxy booking)
--   4. Family guard binding
--   5. Order group chat
-- ========================================
