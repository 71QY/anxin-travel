package com.anxin.travel.module.user.dto;

import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String phone;
    private String code;
    private String newPassword;
}
