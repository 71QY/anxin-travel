package com.anxin.travel.module.guard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 亲友信息DTO（适配前端字段名）
 */
@Data
public class GuardianInfo {
    
    @JsonProperty("userId")
    private Long guardianId;  // 亲友用户ID
    
    @JsonProperty("name")
    private String guardianName;  // 亲友姓名
    
    @JsonProperty("phone")
    private String guardianPhone;  // 亲友手机号
    
    @JsonProperty("realName")
    private String realName;  // 真实姓名（与name相同）
    
    @JsonProperty("bindTime")
    private LocalDateTime bindTime;  // 绑定时间
}
