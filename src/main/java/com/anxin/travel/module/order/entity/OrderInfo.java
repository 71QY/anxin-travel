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
    private Long userId;  // 实际乘车人(长辈)
    private Long driverId;
    private Long proxyUserId;  // 代叫车人(亲友)
    private Long elderUserId;  // 长辈用户ID(冗余字段,方便查询)
    private Double startLat;
    private Double startLng;
    private Double destLat;
    private Double destLng;
    private String destAddress;
    private Integer status;  // 0-待确认 1-已确认/待接单 2-司机已接单 3-行程中 4-已完成 5-已取消 6-已拒绝
    private String platformUsed;
    private String platformOrderId;
    private BigDecimal estimatePrice;
    private BigDecimal actualPrice;
    private LocalDateTime confirmTime;  // 确认时间
    private String rejectReason;  // 拒绝原因
    
    // ========== 司机信息字段（持久化） ==========
    private String driverName;      // 司机姓名
    private String driverPhone;     // 司机电话
    private String carNo;           // 车牌号
    private String carType;         // 车型
    private String carColor;        // 车辆颜色
    private BigDecimal rating;      // 司机评分
    private Double driverLat;       // 司机当前纬度
    private Double driverLng;       // 司机当前经度
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
