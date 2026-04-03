package com.anxin.travel.module.auth.service.impl;

import com.anxin.travel.common.util.JwtUtil;
import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.auth.dto.LoginRequest;
import com.anxin.travel.module.auth.dto.LoginResponse;
import com.anxin.travel.module.auth.dto.RegisterRequest;
import com.anxin.travel.module.auth.service.AuthService;
import com.anxin.travel.module.auth.service.SmsService;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RedisUtil redisUtil;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final SmsService smsService;
    
    @Value("${anxin.sms.provider:mock}")
    private String smsProvider;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void sendVerificationCode(String phone) {
        try {
            // 验证手机号格式
            if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
                throw new IllegalArgumentException("手机号格式不正确");
            }
            
            String code = String.valueOf(new Random().nextInt(900000) + 100000);
            String key = "sms:code:" + phone;
            
            // 检查是否频繁发送（60 秒内只能发送一次）
            Boolean exists = redisUtil.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                log.warn("频繁请求验证码，手机号：{}", phone);
                throw new IllegalStateException("操作过于频繁，请稍后再试");
            }
            
            redisUtil.set(key, code, 300);
            log.info("验证码生成成功，手机号：{}，验证码：{}，过期时间：300 秒", phone, code);
            
            // 调用短信服务发送验证码
            boolean sent = smsService.sendVerificationCode(phone, code);
            if (!sent) {
                log.error("短信发送失败，手机号：{}", phone);
                throw new RuntimeException("短信发送失败，请稍后重试");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("验证码发送失败，手机号：{}，错误：{}", phone, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("验证码发送异常，手机号：{}，错误信息：{}", phone, e.getMessage(), e);
            throw new RuntimeException("验证码发送失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        // 根据登录类型分发
        if ("password".equals(request.getLoginType())) {
            return passwordLogin(request);
        } else {
            return codeLogin(request);
        }
    }
    
    /**
     * 验证码登录
     */
    private LoginResponse codeLogin(LoginRequest request) {
        String key = "sms:code:" + request.getPhone();
        String redisCode = redisUtil.get(key);
        
        if (redisCode == null) {
            // 允许已设置密码的老用户直接登录
            User existingUser = userMapper.selectOne(new QueryWrapper<User>().eq("phone", request.getPhone()));
            if (existingUser != null && existingUser.getPassword() != null) {
                log.info("老用户使用密码登录，phone={}, userId={}", request.getPhone(), existingUser.getId());
                String token = jwtUtil.generateToken(existingUser.getId());
                LoginResponse response = new LoginResponse();
                response.setToken(token);
                response.setUserId(existingUser.getId());
                return response;
            }
            throw new RuntimeException("验证码已失效，请重新获取");
        }
        
        if (!redisCode.equals(request.getCode())) {
            throw new RuntimeException("验证码错误");
        }
        redisUtil.deleteSingle(key);

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
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse passwordLogin(LoginRequest request) {
        log.info("密码登录请求，phone={}", request.getPhone());
        
        if (request.getPhone() == null || request.getPhone().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        
        // 查询用户
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("phone", request.getPhone())
        );
        
        if (user == null) {
            throw new RuntimeException("用户不存在，请先注册");
        }
        
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new RuntimeException("该账号未设置密码，请使用验证码登录");
        }
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        
        // 生成 Token
        String token = jwtUtil.generateToken(user.getId());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        
        log.info("密码登录成功，phone={}, userId={}", request.getPhone(), user.getId());
        return response;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        log.info("注册请求，phone={}", request.getPhone());
        
        if (request.getPhone() == null || request.getPhone().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        
        if (!request.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        
        if (!isValidPassword(request.getPassword())) {
            throw new RuntimeException("密码必须为 8 位，且包含字母和特殊符号");
        }
        
        // 验证验证码
        String codeKey = "sms:code:" + request.getPhone();
        String redisCode = redisUtil.get(codeKey);
        if (redisCode == null || !redisCode.equals(request.getCode())) {
            throw new RuntimeException("验证码错误");
        }
        
        // 检查用户是否已存在
        User existingUser = userMapper.selectOne(
                new QueryWrapper<User>().eq("phone", request.getPhone())
        );
        
        if (existingUser != null) {
            if (existingUser.getPassword() != null && !existingUser.getPassword().isEmpty()) {
                throw new RuntimeException("该手机号已注册，请直接登录");
            }
            // 如果已存在但未设置密码，更新密码
            log.info("用户已存在但未设置密码，更新密码，phone={}", request.getPhone());
        }
        
        // 加密密码
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        User user;
        if (existingUser != null) {
            user = existingUser;
            user.setPassword(encodedPassword);
            if (request.getNickname() != null && !request.getNickname().isEmpty()) {
                user.setNickname(request.getNickname());
            }
            userMapper.updateById(user);
        } else {
            user = new User();
            user.setPhone(request.getPhone());
            user.setPassword(encodedPassword);
            if (request.getNickname() != null && !request.getNickname().isEmpty()) {
                user.setNickname(request.getNickname());
            }
            userMapper.insert(user);
            log.info("新用户注册成功，phone={}, userId={}", request.getPhone(), user.getId());
        }
        
        // 删除验证码
        redisUtil.deleteSingle(codeKey);
        
        // 生成 Token
        String token = jwtUtil.generateToken(user.getId());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        
        return response;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forgotPassword(String phone, String code, String newPassword) {
        log.info("忘记密码请求，phone={}", phone);
        
        if (phone == null || phone.isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        
        if (newPassword == null || newPassword.isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        
        if (!isValidPassword(newPassword)) {
            throw new RuntimeException("密码必须为 8 位，且包含字母和特殊符号");
        }
        
        String codeKey = "sms:code:" + phone;
        String redisCode = redisUtil.get(codeKey);
        if (redisCode == null || !redisCode.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("phone", phone)
        );
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userMapper.updateById(user);
        
        redisUtil.deleteSingle(codeKey);
        log.info("密码重置成功，phone={}", phone);
    }
    
    private boolean isValidPassword(String password) {
        if (password == null || password.length() != 8) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasSymbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (!Character.isDigit(c)) {
                hasSymbol = true;
            }
        }
        return hasLetter && hasSymbol;
    }
}