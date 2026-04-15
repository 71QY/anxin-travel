package com.anxin.travel.module.auth.service;

import com.anxin.travel.module.auth.dto.LoginRequest;
import com.anxin.travel.module.auth.dto.LoginResponse;
import com.anxin.travel.module.auth.dto.RegisterRequest;

public interface AuthService {
    void sendVerificationCode(String phone);
    LoginResponse login(LoginRequest request);
    LoginResponse passwordLogin(LoginRequest request);
    LoginResponse register(RegisterRequest request);
    void forgotPassword(String phone, String code, String newPassword);
    void logout(Long userId);  // 退出登录，清除用户缓存
}