package com.anxin.travel.module.user.dto;

import lombok.Data;

@Data
public class UserVO {
    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String realName;
    private String idCard;
    private Integer verified;
    
    // 【亲情守护】长辈模式标识
    private Integer isGuarded;  // 是否被守护 0否 1是
    private Integer guardMode;  // 0普通模式 1长辈精简模式
}