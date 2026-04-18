-- 插入长辈用户（密码: Abc@12345）
-- 身份证: 110101199001011234 (北京东城区, 1990年01月01日)
INSERT INTO user (phone, password, nickname, real_name, id_card, is_completed) 
VALUES ('13400134000', '$2a$10$9TIXqz8F5vZ3K2mN4pQ7rOeW6sY1uA3bC5dE7fG9hI0jK2lM4nO6p', '长辈用户', '长辈姓名', '110101199001011234', 1);

-- 插入亲友用户（密码: Abc@12345）
-- 身份证: 110102199505052345 (北京西城区, 1995年05月05日)
INSERT INTO user (phone, password, nickname, real_name, id_card, is_completed) 
VALUES ('13500135000', '$2a$10$9TIXqz8F5vZ3K2mN4pQ7rOeW6sY1uA3bC5dE7fG9hI0jK2lM4nO6p', '亲友用户', '亲友姓名', '110102199505052345', 1);

-- 绑定亲友关系
INSERT INTO family_guard (guardian_id, elder_id, elder_phone, elder_name, elder_id_card, guardian_name, guardian_id_card, guardian_phone, status, bind_time, activate_time)
SELECT 
    u2.id as guardian_id,
    u1.id as elder_id,
    u1.phone as elder_phone,
    u1.real_name as elder_name,
    u1.id_card as elder_id_card,
    u2.real_name as guardian_name,
    u2.id_card as guardian_id_card,
    u2.phone as guardian_phone,
    1 as status,
    NOW() as bind_time,
    NOW() as activate_time
FROM user u1, user u2
WHERE u1.phone = '13400134000' AND u2.phone = '13500135000';
