package com.anxin.travel.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private Integer isGuarded;  // 是否被守护 0否 1是
    private Integer guardMode;  // 0普通模式 1长辈精简模式
    
    @JsonProperty("isCompleted")
    private Integer isCompleted;  // 账号是否完善：0-未完善 1-已完善
}