package com.anxin.travel.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String password;
    private String nickname;
    private String avatar;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String realName;
    private String idCard;
    private Integer verified;
}