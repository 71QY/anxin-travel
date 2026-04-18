-- 创建测试司机
INSERT INTO driver (phone, name, license_plate, car_brand, car_model, car_color, rating, status) 
VALUES ('13900001111', '测试司机', '粤A TEST01', '丰田', '卡罗拉', '白色', 5.0, 1);

-- 获取刚插入的司机ID
SET @driver_id = LAST_INSERT_ID();

-- 创建测试订单（status=5 行程中）
INSERT INTO order_info (order_no, user_id, driver_id, start_lat, start_lng, dest_lat, dest_lng, dest_address, status, driver_name, driver_phone, car_no, car_type, car_color, rating, driver_lat, driver_lng, create_time, update_time) 
VALUES (CONCAT('AX_TEST_', UNIX_TIMESTAMP()), 25, @driver_id, 23.654980878613028, 116.66963695994166, 23.666123, 116.676392, '潮州市中心医院', 5, '测试司机', '13900001111', '粤A TEST01', '丰田卡罗拉', '白色', 5.0, 23.654980878613028, 116.66963695994166, NOW(), NOW());
