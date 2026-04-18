package com.anxin.travel.module.user.dto;

import lombok.Data;

/**
 * 分享收藏地点请求
 */
@Data
public class ShareFavoriteRequest {
    private Long favoriteId; // 收藏地点ID
    private Long guardianUserId; // 亲友用户ID
}
