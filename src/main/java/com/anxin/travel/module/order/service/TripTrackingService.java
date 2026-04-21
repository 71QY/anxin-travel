package com.anxin.travel.module.order.service;

import com.alibaba.fastjson.JSON;
import com.anxin.travel.agent.controller.NativeWebSocket;
import com.anxin.travel.module.guard.entity.FamilyGuard;
import com.anxin.travel.module.guard.mapper.FamilyGuardMapper;
import com.anxin.travel.module.order.entity.OrderInfo;
import com.anxin.travel.module.order.mapper.OrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 行程追踪服务 - 定时推送司机位置和 ETA
 */
@Slf4j
@Service
public class TripTrackingService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private FamilyGuardMapper familyGuardMapper;

    @Autowired
    private NativeWebSocket nativeWebSocket;

    // 平均速度：30 km/h = 500 m/min（城市道路）
    private static final double AVERAGE_SPEED_M_PER_MIN = 500.0;
    
    // 到达阈值：50 米（更严格）
    private static final double ARRIVAL_THRESHOLD = 50.0;
    
    // 最小 ETA：当距离很近时，直接显示 1 分钟，避免一直显示 0
    private static final int MIN_ETA_WHEN_CLOSE = 1;

    /**
     * 每 5 秒推送一次行程中订单的司机位置
     * ⚠️ 注意：此定时任务仅作为备用机制，主要依赖 OrderServiceImpl.boardOrder 中的异步模拟
     */
    @Scheduled(fixedRate = 5000)
    public void pushDriverLocationDuringTrip() {
        try {
            // ⭐ 查询最近10分钟内创建的行程中订单
            java.time.LocalDateTime tenMinutesAgo = java.time.LocalDateTime.now().minusMinutes(10);
            
            List<OrderInfo> activeOrders = orderMapper.selectList(
                new LambdaQueryWrapper<OrderInfo>()
                    .eq(OrderInfo::getStatus, 5)
                    .ge(OrderInfo::getCreateTime, tenMinutesAgo)  // ⭐ 只查询最近10分钟的订单
                    .orderByDesc(OrderInfo::getCreateTime)
            );

            if (activeOrders.isEmpty()) {
                return;
            }

            log.debug("📍 [定时任务] 检查 {} 个行程中订单（最近10分钟）", activeOrders.size());

            for (OrderInfo order : activeOrders) {
                try {
                    // ⭐ 只推送，不更新位置（位置由 boardOrder 的异步线程负责更新）
                    pushSingleOrderLocation(order);
                } catch (Exception e) {
                    log.error("❌ 推送订单{}位置失败", order.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("❌ 行程追踪定时任务执行失败", e);
        }
    }

    /**
     * 推送单个订单的位置信息
     */
    private void pushSingleOrderLocation(OrderInfo order) {
        // 验证必要字段
        if (order.getDriverLat() == null || order.getDriverLng() == null) {
            log.warn("⚠️ 订单{}缺少司机位置信息，跳过推送", order.getId());
            return;
        }

        if (order.getDestLat() == null || order.getDestLng() == null) {
            log.warn("⚠️ 订单{}缺少目的地位置信息，跳过推送", order.getId());
            return;
        }

        // 计算距离（米）
        double distance = calculateDistance(
            order.getDriverLat(), order.getDriverLng(),
            order.getDestLat(), order.getDestLng()
        );

        // 计算 ETA（分钟）
        int etaMinutes = calculateETA(distance);

        // 构建 WebSocket 消息（严格按照前端字段要求）
        Map<String, Object> message = new HashMap<>();
        message.put("type", "DRIVER_LOCATION");
        message.put("userId", order.getUserId());  // ⭐ 新增：顶层 userId 字段（前端期望）
        message.put("orderId", order.getId());
        message.put("driverLat", order.getDriverLat());
        message.put("driverLng", order.getDriverLng());
        message.put("etaMinutes", etaMinutes);

        String messageJson = JSON.toJSONString(message);

        // 推送给乘客
        nativeWebSocket.sendMessageToUser(order.getUserId(), messageJson);

        // 推送给代叫人（如果有）
        if (order.getProxyUserId() != null) {
            nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
        }

        // 推送给所有亲友
        List<FamilyGuard> guardians = familyGuardMapper.selectActiveGuardsByElderId(order.getUserId());
        for (FamilyGuard guardian : guardians) {
            nativeWebSocket.sendMessageToUser(guardian.getGuardianId(), messageJson);
        }

        log.debug("✅ 订单{}位置推送成功: 距离={}m, ETA={}min", 
            order.getId(), (int) distance, etaMinutes);
    }

    /**
     * 计算两点之间的距离（Haversine 公式）
     * 
     * @param lat1 起点纬度
     * @param lng1 起点经度
     * @param lat2 终点纬度
     * @param lng2 终点经度
     * @return 距离（米）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS = 6371000; // 地球半径（米）

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * 计算预计到达时间（分钟）
     * 
     * @param distance 距离（米）
     * @return ETA（分钟），如果距离 < 50 米则返回 0
     */
    private int calculateETA(double distance) {
        // 如果距离小于阈值，直接返回 0（已到达）
        if (distance <= ARRIVAL_THRESHOLD) {
            return 0;
        }
        
        // 如果距离很近（50-200米），至少显示 1 分钟，避免一直是 0
        if (distance <= 200) {
            return MIN_ETA_WHEN_CLOSE;
        }

        // 根据平均速度计算时间
        double minutes = distance / AVERAGE_SPEED_M_PER_MIN;
        int eta = (int) Math.ceil(minutes);
        
        // 确保 ETA 至少为 1 分钟（除非已到达）
        return Math.max(eta, 1);
    }
}
