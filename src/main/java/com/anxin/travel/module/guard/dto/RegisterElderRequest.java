package com.anxin.travel.module.guard.dto;

import lombok.Data;

/**
 * 亲友帮长辈注册账号请求
 */
@Data
public class RegisterElderRequest {
    private String elderName;
    private String elderIdCard;
    private String elderPhone;
    private String relationship;
}
