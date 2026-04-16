package com.anxin.travel.module.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.anxin.travel.agent.controller.NativeWebSocket;
import com.anxin.travel.module.order.entity.OrderInfo;
import com.anxin.travel.module.order.mapper.OrderMapper;
import com.anxin.travel.module.order.service.DriverAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 司机分配服务实现（模拟真实司机）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverAssignmentServiceImpl implements DriverAssignmentService {

    private final OrderMapper orderMapper;
    private final NativeWebSocket nativeWebSocket;
    
    // 司机姓名池
    private static final String[] DRIVER_NAMES = {
        "李师傅", "王师傅", "张师傅", "刘师傅", "陈师傅", 
        "杨师傅", "赵师傅", "黄师傅", "周师傅", "吴师傅"
    };
    
    // 车型池
    private static final String[][] CAR_TYPES = {
        {"大众", "朗逸"}, {"丰田", "卡罗拉"}, {"日产", "轩逸"},
        {"比亚迪", "秦"}, {"特斯拉", "Model 3"}, {"吉利", "帝豪"},
        {"本田", "思域"}, {"现代", "伊兰特"}, {"别克", "英朗"}
    };
    
    // 颜色池
    private static final String[] CAR_COLORS = {"白色", "黑色", "灰色", "蓝色", "银色"};
    
    // 车牌前缀池
    private static final String[] LICENSE_PREFIXES = {"京A", "京B", "沪C", "粤A", "粤B", "浙A", "苏A"};

    @Override
    public Map<String, Object> generateRandomDriverInfo() {
        Random random = new Random();
        
        // 随机生成司机信息
        String driverName = DRIVER_NAMES[random.nextInt(DRIVER_NAMES.length)];
        String phonePrefix = "138";
        String phoneSuffix = String.format("%08d", random.nextInt(100000000));
        String driverPhone = phonePrefix + phoneSuffix;
        
        // 随机车型
        String[] carType = CAR_TYPES[random.nextInt(CAR_TYPES.length)];
        String carBrand = carType[0];
        String carModel = carType[1];
        String carColor = CAR_COLORS[random.nextInt(CAR_COLORS.length)];
        
        // 随机车牌
        String prefix = LICENSE_PREFIXES[random.nextInt(LICENSE_PREFIXES.length)];
        String plateNumber = generateRandomPlateNumber(random);
        String carNo = prefix + " " + plateNumber;
        
        // 随机评分 4.8~5.0
        double rating = 4.8 + random.nextDouble() * 0.2;
        rating = Math.round(rating * 10) / 10.0;
        
        Map<String, Object> driverInfo = new HashMap<>();
        driverInfo.put("driverName", driverName);
        driverInfo.put("driverPhone", driverPhone);
        driverInfo.put("driverAvatar", "https://api.dicebear.com/7.x/avataaars/svg?seed=" + driverName);
        driverInfo.put("carNo", carNo);
        driverInfo.put("carType", carBrand + carModel);
        driverInfo.put("carColor", carColor);
        driverInfo.put("rating", rating);
        
        return driverInfo;
    }
    
    /**
     * 生成随机车牌号（5位字母数字组合）
     */
    private String generateRandomPlateNumber(Random random) {
        StringBuilder sb = new StringBuilder();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";  // 排除I和O避免混淆
        for (int i = 0; i < 5; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    @Async
    public void assignDriverAndStartSimulation(Long orderId, Long userId, 
                                               Double startLat, Double startLng,
                                               Double destLat, Double destLng) {
        log.info("🚗 开始为订单{}分配模拟司机，userId={}", orderId, userId);
        
        try {
            // 1. 延迟10秒模拟派单过程
            Thread.sleep(10000);
            
            // 2. 生成随机司机信息
            Map<String, Object> driverInfo = generateRandomDriverInfo();
            
            // 3. 生成司机初始位置（起点附近300-800米）
            Random random = new Random();
            double offsetLat = (random.nextDouble() - 0.5) * 0.01;  // 约±500米
            double offsetLng = (random.nextDouble() - 0.5) * 0.01;
            double driverLat = startLat + offsetLat;
            double driverLng = startLng + offsetLng;
            
            driverInfo.put("driverLat", driverLat);
            driverInfo.put("driverLng", driverLng);
            driverInfo.put("orderId", orderId);
            driverInfo.put("startLat", startLat);
            driverInfo.put("startLng", startLng);
            driverInfo.put("destLat", destLat);
            driverInfo.put("destLng", destLng);
            
            // 4. 更新订单状态为1（已确认，等待用户确认接单）
            OrderInfo order = orderMapper.selectById(orderId);
            if (order != null) {
                order.setStatus(1);
                order.setDriverName((String) driverInfo.get("driverName"));
                order.setDriverPhone((String) driverInfo.get("driverPhone"));
                order.setCarNo((String) driverInfo.get("carNo"));
                order.setCarType((String) driverInfo.get("carType"));
                order.setCarColor((String) driverInfo.get("carColor"));
                order.setRating(new BigDecimal(driverInfo.get("rating").toString()));
                order.setDriverLat(driverLat);
                order.setDriverLng(driverLng);
                orderMapper.updateById(order);
                log.info("✅ 订单{}状态更新为已确认，等待用户确认接单，司机：{}", orderId, order.getDriverName());
            }
            
            // 5. WebSocket推送 DRIVER_REQUEST 弹窗提示（需要用户确认）
            Map<String, Object> requestMsg = new HashMap<>();
            requestMsg.put("type", "DRIVER_REQUEST");
            requestMsg.put("success", true);
            requestMsg.put("message", "有司机接单，是否允许该司机接单？");
            requestMsg.putAll(driverInfo);
            
            String messageJson = JSON.toJSONString(requestMsg);
            
            // ⭐ 推送给乘客（长辈）
            nativeWebSocket.sendMessageToUser(userId, messageJson);
            log.info("✅ 已向乘客 userId={} 推送 DRIVER_REQUEST", userId);
            
            // ⭐ 如果是代叫车订单，也推送给代叫人（亲友）
            if (order != null && order.getProxyUserId() != null && order.getProxyUserId() > 0) {
                nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
                log.info("✅ 已向代叫人 proxyUserId={} 推送 DRIVER_REQUEST", order.getProxyUserId());
            }
            
        } catch (Exception e) {
            log.error("❌ 司机分配失败，orderId={}", orderId, e);
        }
    }
    
    /**
     * 用户确认/拒绝司机接单
     */
    public void confirmDriverAcceptance(Long orderId, Long userId, boolean accepted) {
        log.info("用户{}确认订单{}的司机接单，accepted={}", userId, orderId, accepted);
        
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            log.error("订单不存在，orderId={}", orderId);
            return;
        }
        
        if (!order.getUserId().equals(userId)) {
            log.error("无权操作此订单，userId={}, orderId={}", userId, orderId);
            return;
        }
        
        if (accepted) {
            // 用户同意接单
            order.setStatus(2); // 已接单
            orderMapper.updateById(order);
            log.info("✅ 订单{}已确认接单，司机：{}", orderId, order.getDriverName());
            
            // 构建司机信息
            Map<String, Object> driverInfo = new HashMap<>();
            driverInfo.put("driverName", order.getDriverName());
            driverInfo.put("driverPhone", order.getDriverPhone());
            driverInfo.put("driverAvatar", "https://api.dicebear.com/7.x/avataaars/svg?seed=" + order.getDriverName());
            driverInfo.put("carNo", order.getCarNo());
            driverInfo.put("carType", order.getCarType());
            driverInfo.put("carColor", order.getCarColor());
            driverInfo.put("rating", order.getRating() != null ? order.getRating().doubleValue() : 4.9);
            driverInfo.put("driverLat", order.getDriverLat());
            driverInfo.put("driverLng", order.getDriverLng());
            
            // 推送 ORDER_ACCEPTED
            Map<String, Object> acceptedMsg = new HashMap<>();
            acceptedMsg.put("type", "ORDER_ACCEPTED");
            acceptedMsg.put("orderId", orderId);
            acceptedMsg.put("success", true);
            acceptedMsg.put("message", "司机已接单，正在赶来");
            acceptedMsg.putAll(driverInfo);
            acceptedMsg.put("startLat", order.getStartLat());
            acceptedMsg.put("startLng", order.getStartLng());
            acceptedMsg.put("destLat", order.getDestLat());
            acceptedMsg.put("destLng", order.getDestLng());
            
            String messageJson = JSON.toJSONString(acceptedMsg);
            
            // ⭐ 推送给乘客（长辈）
            nativeWebSocket.sendMessageToUser(userId, messageJson);
            log.info("✅ 已向乘客 userId={} 推送 ORDER_ACCEPTED", userId);
            
            // ⭐ 如果是代叫车订单，也推送给代叫人（亲友）
            if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
                nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
                log.info("✅ 已向代叫人 proxyUserId={} 推送 ORDER_ACCEPTED", order.getProxyUserId());
            }
            
            // 开始模拟车辆行驶
            simulateVehicleMovement(orderId, userId, order.getDriverLat(), order.getDriverLng(), 
                                   order.getStartLat(), order.getStartLng(), driverInfo);
        } else {
            // 用户拒绝接单
            order.setStatus(0); // 回到待确认状态，等待重新派单
            order.setDriverName(null);
            order.setDriverPhone(null);
            order.setCarNo(null);
            order.setCarType(null);
            order.setCarColor(null);
            order.setRating(null);
            order.setDriverLat(null);
            order.setDriverLng(null);
            orderMapper.updateById(order);
            log.info("✅ 订单{}用户拒绝接单，重新派单", orderId);
            
            // 推送拒绝消息
            Map<String, Object> rejectedMsg = new HashMap<>();
            rejectedMsg.put("type", "DRIVER_REJECTED");
            rejectedMsg.put("orderId", orderId);
            rejectedMsg.put("success", true);
            rejectedMsg.put("message", "您已拒绝该司机，正在为您重新派单");
            
            String messageJson = JSON.toJSONString(rejectedMsg);
            
            // ⭐ 推送给乘客（长辈）
            nativeWebSocket.sendMessageToUser(userId, messageJson);
            log.info("✅ 已向乘客 userId={} 推送 DRIVER_REJECTED", userId);
            
            // ⭐ 如果是代叫车订单，也推送给代叫人（亲友）
            if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
                nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
                log.info("✅ 已向代叫人 proxyUserId={} 推送 DRIVER_REJECTED", order.getProxyUserId());
            }
            
            // ⭐ 延迟10秒后重新派单（异步）
            new Thread(() -> {
                try {
                    Thread.sleep(10000);  // 延迟10秒
                    
                    // 检查订单是否仍然有效（未被取消）
                    OrderInfo currentOrder = orderMapper.selectById(orderId);
                    if (currentOrder != null && currentOrder.getStatus() == 0) {
                        log.info("⏰ 10秒已到，开始为订单{}重新派单", orderId);
                        assignDriverAndStartSimulation(orderId, userId, 
                            currentOrder.getStartLat(), currentOrder.getStartLng(),
                            currentOrder.getDestLat(), currentOrder.getDestLng());
                    } else {
                        log.info("⚠️ 订单{}状态已变更（status={}），取消重新派单", 
                            orderId, currentOrder != null ? currentOrder.getStatus() : "null");
                    }
                } catch (Exception e) {
                    log.error("❌ 重新派单失败，orderId={}", orderId, e);
                }
            }).start();
            log.info("✅ 已调度10秒后重新派单，orderId={}", orderId);
        }
    }
    
    /**
     * 模拟车辆向起点行驶
     */
    private void simulateVehicleMovement(Long orderId, Long userId, 
                                         double currentLat, double currentLng,
                                         double targetLat, double targetLng,
                                         Map<String, Object> driverInfo) {
        try {
            int steps = 5;  // 分5步移动到起点
            double latStep = (targetLat - currentLat) / steps;
            double lngStep = (targetLng - currentLng) / steps;
            
            for (int i = 1; i <= steps; i++) {
                // 每3秒更新一次位置
                Thread.sleep(3000);
                
                double newLat = currentLat + latStep * i;
                double newLng = currentLng + lngStep * i;
                
                // 推送位置更新
                Map<String, Object> locationMsg = new HashMap<>();
                locationMsg.put("type", "DRIVER_LOCATION");
                locationMsg.put("orderId", orderId);
                locationMsg.put("driverLat", newLat);
                locationMsg.put("driverLng", newLng);
                locationMsg.put("etaMinutes", steps - i);  // 预计到达分钟数
                
                String messageJson = JSON.toJSONString(locationMsg);
                
                // ⭐ 推送给乘客（长辈）
                nativeWebSocket.sendMessageToUser(userId, messageJson);
                
                // ⭐ 如果是代叫车订单，也推送给代叫人（亲友）
                OrderInfo order = orderMapper.selectById(orderId);
                if (order != null && order.getProxyUserId() != null && order.getProxyUserId() > 0) {
                    nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
                }
                
                log.debug("📍 推送司机位置：step={}/{}, lat={}, lng={}", i, steps, newLat, newLng);
            }
            
            // 7. 到达起点，推送 DRIVER_ARRIVED
            Thread.sleep(1000);
            Map<String, Object> arrivedMsg = new HashMap<>();
            arrivedMsg.put("type", "DRIVER_ARRIVED");
            arrivedMsg.put("orderId", orderId);
            arrivedMsg.put("message", "司机已到达上车点，请上车");
            arrivedMsg.put("driverLat", targetLat);
            arrivedMsg.put("driverLng", targetLng);
            
            String messageJson = JSON.toJSONString(arrivedMsg);
            
            // ⭐ 推送给乘客（长辈）
            nativeWebSocket.sendMessageToUser(userId, messageJson);
            log.info("✅ 已向乘客 userId={} 推送 DRIVER_ARRIVED", userId);
            
            // ⭐ 如果是代叫车订单，也推送给代叫人（亲友）
            OrderInfo order = orderMapper.selectById(orderId);
            if (order != null && order.getProxyUserId() != null && order.getProxyUserId() > 0) {
                nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
                log.info("✅ 已向代叫人 proxyUserId={} 推送 DRIVER_ARRIVED", order.getProxyUserId());
            }
            
            log.info("✅ 司机已到达上车点，orderId={}", orderId);
            
            // 更新订单状态为3（行程中）和司机位置
            if (order != null) {
                order.setStatus(3);
                order.setDriverLat(targetLat);
                order.setDriverLng(targetLng);
                orderMapper.updateById(order);
                log.info("✅ 订单{}状态更新为行程中", orderId);
            }
            
        } catch (Exception e) {
            log.error("❌ 模拟车辆行驶失败，orderId={}", orderId, e);
        }
    }
}
