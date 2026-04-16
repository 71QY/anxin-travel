package com.anxin.travel.module.order.service;

import java.util.Map;

/**
 * 司机分配服务接口
 */
public interface DriverAssignmentService {
    
    /**
     * 为订单分配模拟司机并启动位置推送
     * @param orderId 订单ID
     * @param userId 用户ID（用于WebSocket推送）
     * @param startLat 起点纬度
     * @param startLng 起点经度
     * @param destLat 终点纬度
     * @param destLng 终点经度
     */
    void assignDriverAndStartSimulation(Long orderId, Long userId, 
                                        Double startLat, Double startLng,
                                        Double destLat, Double destLng);
    
    /**
     * 生成随机司机信息（不保存到数据库，仅用于返回）
     * @return 司机信息Map
     */
    Map<String, Object> generateRandomDriverInfo();
    
    /**
     * 用户确认/拒绝司机接单
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param accepted true=同意，false=拒绝
     */
    void confirmDriverAcceptance(Long orderId, Long userId, boolean accepted);
}
