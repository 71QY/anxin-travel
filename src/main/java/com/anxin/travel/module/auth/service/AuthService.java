package com.anxin.travel.module.auth.service;

import com.anxin.travel.module.auth.dto.LoginRequest;
import com.anxin.travel.module.auth.dto.LoginResponse;

public interface AuthService {
    void sendVerificationCode(String phone);
    LoginResponse login(LoginRequest request);
}