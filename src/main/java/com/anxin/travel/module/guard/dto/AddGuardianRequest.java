package com.anxin.travel.module.guard.dto;

import lombok.Data;

@Data
public class AddGuardianRequest {
    private String elderPhone;      // 长辈手机号
    private String elderName;       // 长辈姓名
    private String guardianName;    // 亲友姓名（模拟实名）
    private String guardianIdCard;  // 亲友身份证号（模拟）
}
