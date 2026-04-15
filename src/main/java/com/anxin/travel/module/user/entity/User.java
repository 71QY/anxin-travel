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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private String realName;
    private String idCard;
    private Integer verified;
    private Integer isGuarded;  // 是否被守护 0否 1是
    private Integer guardMode;  // 0普通模式 1长辈精简模式
    private Integer isCompleted;  // 账号是否完善：0-未完善 1-已完善
}