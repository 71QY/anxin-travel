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
    private String destAddress;
    private Integer status;
    private String platformUsed;
    private String platformOrderId;
    private BigDecimal estimatePrice;
    private BigDecimal actualPrice;
    private LocalDateTime createTime;
}