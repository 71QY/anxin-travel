package com.anxin.travel.module.auth.service.impl;

import com.anxin.travel.common.util.JwtUtil;
import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.auth.dto.LoginRequest;
import com.anxin.travel.module.auth.dto.LoginResponse;
import com.anxin.travel.module.auth.dto.RegisterRequest;
import com.anxin.travel.module.auth.service.AuthService;
import com.anxin.travel.module.auth.service.SmsService;
import com.anxin.travel.module.guard.entity.FamilyGuard;
import com.anxin.travel.module.guard.mapper.FamilyGuardMapper;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RedisUtil redisUtil;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final SmsService smsService;
    
    @Autowired
    private FamilyGuardMapper familyGuardMapper;
    
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
            
            // 【优化】检查频繁发送：使用独立的频率限制 key，记录最近 60 秒内的发送次数
            String rateLimitKey = "sms:rate:" + phone;
            
            // 使用原子递增操作，避免并发问题
            Long sendCount = redisUtil.increment(rateLimitKey);
            
            // 如果是第一次发送（计数为1），设置过期时间 60 秒
            if (sendCount == 1) {
                redisUtil.expire(rateLimitKey, 60);
            }
            
            // 如果 60 秒内发送次数 >= 10 次，则拒绝
            if (sendCount >= 10) {
                log.warn("⚠️ 验证码发送过于频繁，手机号：{}，60秒内已发送 {} 次", phone, sendCount);
                throw new IllegalStateException("操作过于频繁，请稍后再试");
            }
            
            // 保存验证码（5 分钟有效）
            redisUtil.set(key, code, 300);
            log.info("✅ 验证码生成成功，手机号：{}，验证码：{}，60秒内第 {} 次发送", phone, code, sendCount);
            
            // 调用短信服务发送验证码
            boolean sent = smsService.sendVerificationCode(phone, code);
            if (!sent) {
                log.error("❌ 短信发送失败，手机号：{}", phone);
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
        log.info("📱 验证码登录请求，phone={}", request.getPhone());
        
        // 1. 验证手机号格式
        if (request.getPhone() == null || request.getPhone().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        
        if (!request.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new RuntimeException("手机号格式不正确");
        }
        
        // 2. 验证验证码
        String key = "sms:code:" + request.getPhone();
        String redisCode = redisUtil.get(key);
        
        if (redisCode == null) {
            // 允许已设置密码的老用户直接登录
            User existingUser = userMapper.selectOne(new QueryWrapper<User>().eq("phone", request.getPhone()));
            if (existingUser != null && existingUser.getPassword() != null) {
                log.info("✅ 老用户使用密码登录，phone={}, userId={}", request.getPhone(), existingUser.getId());
                String token = jwtUtil.generateToken(existingUser.getId());
                LoginResponse response = new LoginResponse();
                response.setToken(token);
                response.setUserId(existingUser.getId());
                response.setIsGuarded(existingUser.getIsGuarded());
                response.setGuardMode(existingUser.getGuardMode());
                response.setIsCompleted(1);  // 老用户已完善
                return response;
            }
            throw new RuntimeException("验证码已失效，请重新获取");
        }
        
        if (!redisCode.equals(request.getCode())) {
            throw new RuntimeException("验证码错误，请重新输入");
        }
        
        // 删除验证码（一次性使用）
        redisUtil.deleteSingle(key);
        log.info("✅ 验证码验证通过");

        // 3. 查询或创建用户
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("phone", request.getPhone())
        );
        
        boolean isNewUser = false;
        if (user == null) {
            // 新用户：创建账号但未完善
            user = new User();
            user.setPhone(request.getPhone());
            user.setIsCompleted(0);  // 标记为未完善
            userMapper.insert(user);
            isNewUser = true;
            log.info("🆕 新用户创建成功，手机号：{}, userId: {}", request.getPhone(), user.getId());
        } else {
            log.info("👤 已有用户登录，userId: {}", user.getId());
        }

        // 4. 生成 Token
        String token = jwtUtil.generateToken(user.getId());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        
        // 5. 【亲情守护】批量激活待激活的绑定
        activateGuardiansIfNeeded(user);
        
        // 6. 刷新用户信息（可能已被激活）
        user = userMapper.selectById(user.getId());
        response.setIsGuarded(user.getIsGuarded());
        response.setGuardMode(user.getGuardMode());
        
        // 7. 判断是否需要完善信息
        boolean needSetup = (user.getPassword() == null || user.getPassword().isEmpty()) 
                         && (user.getNickname() == null || user.getNickname().isEmpty());
        response.setIsCompleted(needSetup ? 0 : 1);
        
        // 8. 更新 is_completed 字段
        if (needSetup && user.getIsCompleted() != 0) {
            user.setIsCompleted(0);
            userMapper.updateById(user);
        } else if (!needSetup && user.getIsCompleted() != 1) {
            user.setIsCompleted(1);
            userMapper.updateById(user);
        }
        
        // 9. 返回提示信息
        if (isNewUser) {
            log.info("✅ 新用户注册成功，需要完善账号信息");
        } else if (needSetup) {
            log.info("✅ 登录成功，但账号未完善，请先设置密码和昵称");
        } else {
            log.info("✅ 登录成功");
        }
        
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
        
        // 【亲情守护】批量激活待激活的绑定
        activateGuardiansIfNeeded(user);
        
        // 刷新用户信息（可能已被激活）
        user = userMapper.selectById(user.getId());
        response.setIsGuarded(user.getIsGuarded());
        response.setGuardMode(user.getGuardMode());
        
        // 密码登录的老用户不需要完善信息
        response.setIsCompleted(1);
        
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
        
        // 注册时已设置密码和昵称，不需要再完善
        response.setIsCompleted(1);
        
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
        if (password == null || password.length() != 10) {
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
    
    /**
     * 【亲情守护】批量激活待激活的绑定
     */
    private void activateGuardiansIfNeeded(User user) {
        // 1. 查询该手机号所有待激活绑定
        List<FamilyGuard> pendingGuards = familyGuardMapper.selectPendingByElderPhone(user.getPhone());
        if (pendingGuards.isEmpty()) {
            return;  // 没有待激活的绑定
        }
        
        // 2. 【关键修复】检查总人数不超4人
        if (pendingGuards.size() > 4) {
            log.warn("⚠️ 长辈{}待激活绑定数{}超过上限4人，拒绝激活", user.getId(), pendingGuards.size());
            return;
        }
        
        // 3. 【新增】检查已激活的绑定数量 + 待激活数量 <= 4
        int activatedCount = familyGuardMapper.countByElderId(user.getId());
        int totalCount = activatedCount + pendingGuards.size();
        
        if (totalCount > 4) {
            log.warn("⚠️ 长辈{}已有{}个激活绑定，加上{}个待激活，总数{}超过上限4人，拒绝激活", 
                user.getId(), activatedCount, pendingGuards.size(), totalCount);
            return;
        }
        
        // 4. 批量激活所有待激活记录
        for (FamilyGuard guard : pendingGuards) {
            guard.setElderId(user.getId());
            guard.setStatus(1);
            guard.setActivateTime(LocalDateTime.now());
            familyGuardMapper.updateById(guard);
        }
        
        // 5. 设置长辈模式标识
        user.setIsGuarded(1);
        user.setGuardMode(1);
        userMapper.updateById(user);
        
        log.info("✅ 长辈{}登录，批量激活{}个亲友绑定，当前总绑定数：{}", 
            user.getId(), pendingGuards.size(), totalCount);
    }
    
    @Override
    public void logout(Long userId) {
        if (userId == null) {
            log.warn("⚠️ 退出登录失败：userId 为空");
            return;
        }
        
        log.info("👋 用户{}退出登录，开始清除缓存...", userId);
        
        try {
            // 1. 清除该用户的所有 Agent 会话缓存（Redis key: agent:session:*）
            Set<String> sessionKeys = redisUtil.keys("agent:session:" + userId + "*");
            if (sessionKeys != null && !sessionKeys.isEmpty()) {
                redisUtil.delete(sessionKeys);
                log.info("✅ 已清除 {} 个 Agent 会话缓存", sessionKeys.size());
            }
            
            // 2. 清除该用户的 POI 缓存（Redis key: poi:*）
            Set<String> poiKeys = redisUtil.keys("poi:*" + userId + "*");
            if (poiKeys != null && !poiKeys.isEmpty()) {
                redisUtil.delete(poiKeys);
                log.info("✅ 已清除 {} 个 POI 缓存", poiKeys.size());
            }
            
            // 3. 清除可能的验证码缓存（虽然通常与手机号相关，但为了保险也清理）
            // 这里不需要特别处理，因为验证码有过期时间
            
            // 4. 清除 ThreadLocal 中的用户上下文（由拦截器自动处理）
            UserContext.clear();
            
            log.info("✅ 用户{}退出登录成功，所有缓存已清除", userId);
        } catch (Exception e) {
            log.error("❌ 用户{}退出登录时清除缓存失败", userId, e);
            // 不抛出异常，避免影响前端体验
        }
    }
}
