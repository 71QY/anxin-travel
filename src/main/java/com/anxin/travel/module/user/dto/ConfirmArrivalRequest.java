package com.anxin.travel.module.user.dto;

import lombok.Data;

/**
 * 确认到达目的地请求
 */
@Data
public class ConfirmArrivalRequest {
    private Long orderId; // 订单ID
    private Long favoriteId; // 收藏地点ID（可选）
}
