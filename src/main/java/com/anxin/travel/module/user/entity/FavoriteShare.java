package com.anxin.travel.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏地点分享记录
 */
@Data
@TableName("favorite_shares")
public class FavoriteShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long favoriteId; // 收藏地点ID
    private Long elderUserId; // 长辈用户ID(分享者)
    private Long guardianUserId; // 亲友用户ID(接收者)
    private Integer status; // 0-待处理, 1-已使用, 2-已过期
    private LocalDateTime sharedAt; // 分享时间
    private LocalDateTime usedAt; // 使用时间
}
