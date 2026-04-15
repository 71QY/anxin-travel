package com.anxin.travel.module.guard.dto;

import lombok.Data;

@Data
public class ProxyOrderRequest {
    private Long elderId;           // 长辈ID
    private Double startLat;        // 起点纬度
    private Double startLng;        // 起点经度
    private Double destLat;         // 终点纬度
    private Double destLng;         // 终点经度
    private String destAddress;     // 终点地址
    private Boolean needConfirm;    // 是否需要长辈确认（默认true）
}
