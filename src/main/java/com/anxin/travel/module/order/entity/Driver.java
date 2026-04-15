package com.anxin.travel.module.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 司机实体类（模拟数据）
 */
@Data
@TableName("driver")
public class Driver {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String phone;           // 司机手机号
    private String name;            // 司机姓名
    private String licensePlate;    // 车牌号
    private String carBrand;        // 车辆品牌
    private String carModel;        // 车型
    private String carColor;        // 车辆颜色
    private BigDecimal rating;      // 评分（4.8~5.0）
    private Integer status;         // 0休息 1空闲 2接单中 3行程中
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
