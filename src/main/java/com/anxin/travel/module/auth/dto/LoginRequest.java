package com.anxin.travel.module.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String phone;
    private String code;
    private String password;
    private String loginType; // "code" - 验证码登录，"password" - 密码登录
}