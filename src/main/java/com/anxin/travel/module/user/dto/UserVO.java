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
}