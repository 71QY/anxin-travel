package com.anxin.travel.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorite_locations")
public class UserFavoriteLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private String type; // HOME, COMPANY, HOSPITAL, CUSTOM
    private String phone; // 联系电话
    private String description; // 地点简介说明
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
