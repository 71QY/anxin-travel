package com.anxin.travel.module.user.dto;

import lombok.Data;

/**
 * 分享收藏给长辈请求
 */
@Data
public class ShareToElderRequest {
    private Long favoriteId; // 分享者自己的收藏ID
    private Long elderUserId; // 目标长辈用户ID
    private Boolean saveAsNew = true; // 是否保存为新收藏（默认true）
}
