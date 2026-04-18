package com.anxin.travel.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出行记录（行程凭证）
 */
@Data
@TableName("travel_records")
public class TravelRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId; // 用户ID(长辈)
    private Long orderId; // 订单ID
    private Long favoriteId; // 关联的收藏地点ID
    private String destinationName; // 目的地名称
    private String destinationAddress; // 目的地地址
    private Double destinationLat; // 目的地纬度
    private Double destinationLng; // 目的地经度
    private LocalDateTime startTime; // 出发时间
    private LocalDateTime arriveTime; // 到达时间
    private Integer durationMinutes; // 行程时长(分钟)
    private Integer distanceMeters; // 行程距离(米)
    private Integer status; // 0-进行中, 1-已完成, 2-已取消
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
}
