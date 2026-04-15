package com.anxin.travel.module.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long driverId;
    private Double destLat;
    private Double destLng;
    private Double startLat;  // 起点纬度
    private Double startLng;  // 起点经度
    private String destAddress;  // 后端标准字段
    private String poiName;      // 【新增】前端期望字段，与 destAddress 相同
    private Integer status;
    private String platformUsed;
    private String platformOrderId;
    private BigDecimal estimatePrice;
    private BigDecimal actualPrice;
    private LocalDateTime createTime;
    
    // ========== 司机信息字段（用于行程页面展示） ==========
    private String driverName;       // 司机姓名（如：李师傅）
    private String driverPhone;      // 司机手机号
    private String driverAvatar;     // 司机头像URL
    private String carNo;            // 车牌号（如：京A 8D231）
    private String carType;          // 车型（如：大众朗逸）
    private String carColor;         // 车辆颜色（如：白色）
    private Double driverLat;        // 司机当前纬度
    private Double driverLng;        // 司机当前经度
    private Double rating;           // 司机评分（4.8~5.0）
}