package com.anxin.travel.module.auth.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.auth.dto.LoginRequest;
import com.anxin.travel.module.auth.dto.LoginResponse;
import com.anxin.travel.module.auth.dto.RegisterRequest;
import com.anxin.travel.module.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/code")
    public Result<Void> sendCode(@RequestParam String phone) {
        log.info("📱 收到发送验证码请求，phone={}", phone);
        try {
            authService.sendVerificationCode(phone);
            log.info("✅ 验证码发送成功，phone={}", phone);
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("⚠️ 验证码请求异常：{}", e.getMessage(), e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("❌ 验证码发送失败，完整异常信息：", e);
            log.error("异常类型：{}", e.getClass().getName());
            log.error("异常消息：{}", e.getMessage());
            if (e.getCause() != null) {
                log.error("根本原因：{} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            }
            return Result.error("验证码发送失败：" + e.getMessage());
        }
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            String loginType = request.getLoginType() != null ? request.getLoginType() : "code";
            log.info("用户登录成功，phone={}, userId={}, 登录方式：{}", request.getPhone(), response.getUserId(), loginType);
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("登录失败：{}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("登录异常", e);
            return Result.error(500, "登录失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest request) {
        try {
            log.info("注册请求，phone={}", request.getPhone());
            LoginResponse response = authService.register(request);
            log.info("用户注册成功，phone={}, userId={}", request.getPhone(), response.getUserId());
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("注册失败：{}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("注册异常", e);
            return Result.error(500, "注册失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phone");
            String code = request.get("code");
            String newPassword = request.get("newPassword");
            
            authService.forgotPassword(phone, code, newPassword);
            return Result.success();
        } catch (IllegalArgumentException e) {
            log.warn("重置密码失败：{}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("重置密码异常", e);
            return Result.error(500, "重置密码失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/logout")
    public Result<Void> logout() {
        try {
            Long userId = UserContext.getUserId();
            if (userId == null) {
                log.warn("⚠️ 退出登录：用户未登录");
                return Result.error(401, "用户未登录");
            }
            
            log.info("👋 用户{}请求退出登录", userId);
            authService.logout(userId);
            
            log.info("✅ 用户{}退出登录成功", userId);
            return Result.success();
        } catch (Exception e) {
            log.error("❌ 退出登录异常", e);
            return Result.error(500, "退出登录失败：" + e.getMessage());
        }
    }
}