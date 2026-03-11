package com.anxin.travel.module.auth.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.module.auth.dto.LoginRequest;
import com.anxin.travel.module.auth.dto.LoginResponse;
import com.anxin.travel.module.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/code")
    public Result<Void> sendCode(@RequestParam String phone) {
        authService.sendVerificationCode(phone);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}