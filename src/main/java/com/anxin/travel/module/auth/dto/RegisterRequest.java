package com.anxin.travel.module.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String phone;
    private String password;
    private String code;
    private String nickname;
}
