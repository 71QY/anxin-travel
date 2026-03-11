package com.anxin.travel.module.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_info")
public class OrderInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long driverId;
    private Double startLat;
    private Double startLng;
    private Double destLat;
    private Double destLng;
    private String destAddress;
    private Integer status;
    private String platformUsed;
    private String platformOrderId;
    private BigDecimal estimatePrice;
    private BigDecimal actualPrice;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}