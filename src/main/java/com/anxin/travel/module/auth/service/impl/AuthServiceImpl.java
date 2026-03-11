package com.anxin.travel.module.auth.service.impl;

import com.anxin.travel.common.util.JwtUtil;
import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.auth.dto.LoginRequest;
import com.anxin.travel.module.auth.dto.LoginResponse;
import com.anxin.travel.module.auth.service.AuthService;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;  // 添加导入
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RedisUtil redisUtil;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final Environment environment;  // 添加 Environment 注入

    @Override
    public void sendVerificationCode(String phone) {
        try {
            String code = String.valueOf(new Random().nextInt(900000) + 100000);
            String key = "sms:code:" + phone;
            redisUtil.set(key, code, 300);
            log.info("验证码发送成功，手机号：{}，验证码：{}，Redis key: {}", phone, code, key);
        } catch (Exception e) {
            log.error("验证码发送失败，手机号：{}，错误信息：{}", phone, e.getMessage(), e);
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 打印数据库密码（调试用）
        String password = environment.getProperty("spring.datasource.password");
        log.info("当前配置的数据库密码是: {}", password);

        String key = "sms:code:" + request.getPhone();
        String redisCode = redisUtil.get(key);
        if (redisCode == null) {
            throw new RuntimeException("验证码已过期");
        }
        if (!redisCode.equals(request.getCode())) {
            throw new RuntimeException("验证码错误");
        }
        redisUtil.delete(key);

        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("phone", request.getPhone())
        );
        if (user == null) {
            user = new User();
            user.setPhone(request.getPhone());
            userMapper.insert(user);
            log.info("新用户注册，手机号：{}", request.getPhone());
        }

        String token = jwtUtil.generateToken(user.getId());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        return response;
    }
}