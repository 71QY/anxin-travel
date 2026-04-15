package com.anxin.travel.module.user.dto;

import lombok.Data;

/**
 * 完善账号信息请求
 */
@Data
public class CompleteProfileRequest {
    private String password;   // 密码（10位，包含字母和特殊符号）
    private String nickname;   // 昵称（1-20字符）
}
