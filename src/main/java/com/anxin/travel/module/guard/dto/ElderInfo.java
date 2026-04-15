package com.anxin.travel.module.guard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 长辈信息DTO（适配前端字段名）
 */
@Data
public class ElderInfo {
    
    @JsonProperty("userId")
    private Long elderId;  // 长辈用户ID
    
    @JsonProperty("guardId")
    private Long guardId;  // 绑定记录ID
    
    @JsonProperty("name")
    private String elderName;  // 长辈姓名
    
    @JsonProperty("phone")
    private String elderPhone;  // 长辈手机号
    
    @JsonProperty("idCard")
    private String elderIdCard;  // 长辈身份证号
    
    @JsonProperty("status")
    private Integer status;  // 0待激活 1已绑定
    
    @JsonProperty("bindTime")
    private LocalDateTime bindTime;  // 绑定时间
    
    @JsonProperty("activateTime")
    private LocalDateTime activateTime;  // 激活时间
}
