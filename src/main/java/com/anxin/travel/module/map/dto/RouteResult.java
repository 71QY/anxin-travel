package com.anxin.travel.module.map.dto;

import lombok.Data;

@Data
public class RouteResult {
    private String mode;      // 出行方式 (driving, transit, walking)
    private int duration;     // 改为 int（基本类型）
    private double distance;  // 改为 double
    private double price;     // 改为 double
}