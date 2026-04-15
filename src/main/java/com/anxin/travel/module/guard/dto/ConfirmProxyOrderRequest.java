package com.anxin.travel.module.guard.dto;

import lombok.Data;

/**
 * 长辈确认代叫车请求
 */
@Data
public class ConfirmProxyOrderRequest {
    private Boolean confirmed;      // true-同意，false-拒绝
    private String rejectReason;    // 拒绝原因（可选）
}
