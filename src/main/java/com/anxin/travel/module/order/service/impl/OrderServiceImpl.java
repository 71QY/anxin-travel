package com.anxin.travel.module.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.dto.OrderVO;
import com.anxin.travel.module.order.entity.OrderInfo;
import com.anxin.travel.module.order.mapper.OrderMapper;
import com.anxin.travel.module.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final com.anxin.travel.module.map.client.AmapClient amapClient;
    private final com.anxin.travel.module.order.service.DriverAssignmentService driverAssignmentService;
    private final com.anxin.travel.agent.controller.NativeWebSocket nativeWebSocket;

    @Override
    @Transactional
    public OrderVO createOrder(Long userId, CreateOrderRequest request) {
        // 校验参数
        if (request.getDestLat() == null || request.getDestLng() == null) {
            throw new RuntimeException("目的地坐标不能为空");
        }
        if (request.getDestName() == null || request.getDestName().trim().isEmpty()) {
            throw new RuntimeException("目的地名称不能为空");
        }
        
        String orderNo = generateOrderNo();
        
        // 【关键】尝试从 Redis 获取用户真实起点位置，如果没有则使用默认值
        double[] userLocation = getUserCurrentLocation(userId);
        double startLat = userLocation[0];
        double startLng = userLocation[1];
        
        // 基于真实起点和终点计算价格
        BigDecimal estimatePrice = calculateEstimatePrice(startLat, startLng, request.getDestLat(), request.getDestLng());
        
        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStartLat(startLat);  // 记录起点
        order.setStartLng(startLng);  // 记录起点
        order.setDestLat(request.getDestLat());
        order.setDestLng(request.getDestLng());
        order.setDestAddress(request.getDestName().trim());
        order.setStatus(0);
        order.setPlatformUsed("gaode");
        order.setEstimatePrice(estimatePrice);
        order.setCreateTime(LocalDateTime.now());
        
        int rows = orderMapper.insert(order);
        log.info("订单插入数据库，影响行数：{}, 订单号：{}, ID: {}", rows, orderNo, order.getId());
        
        // ⭐ 异步分配模拟司机并启动位置推送
        try {
            driverAssignmentService.assignDriverAndStartSimulation(
                order.getId(), userId, startLat, startLng, 
                request.getDestLat(), request.getDestLng()
            );
            log.info("✅ 已触发司机分配任务，orderId={}", order.getId());
        } catch (Exception e) {
            log.error("❌ 触发司机分配失败，但订单已创建成功", e);
        }
        
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setPoiName(vo.getDestAddress());  // 【关键】前端期望 poiName 字段
        
        // ⭐ 生成随机司机信息并填充到返回结果（立即返回给前端）
        Map<String, Object> driverInfo = driverAssignmentService.generateRandomDriverInfo();
        vo.setDriverName((String) driverInfo.get("driverName"));
        vo.setDriverPhone((String) driverInfo.get("driverPhone"));
        vo.setDriverAvatar((String) driverInfo.get("driverAvatar"));
        vo.setCarNo((String) driverInfo.get("carNo"));
        vo.setCarType((String) driverInfo.get("carType"));
        vo.setCarColor((String) driverInfo.get("carColor"));
        vo.setRating((Double) driverInfo.get("rating"));
        
        // ⭐ 生成司机初始位置（起点附近300-800米），用于前端显示小车初始位置
        Random random = new Random();
        double offsetLat = (random.nextDouble() - 0.5) * 0.01;  // 约±500米
        double offsetLng = (random.nextDouble() - 0.5) * 0.01;
        vo.setDriverLat(startLat + offsetLat);
        vo.setDriverLng(startLng + offsetLng);
        
        log.info("✅ 订单创建成功：orderNo={}, poiName={}, destAddress={}, price={}, driver={}", 
            vo.getOrderNo(), vo.getPoiName(), vo.getDestAddress(), vo.getEstimatePrice(), vo.getDriverName());
        return vo;
    }

    /**
     * 获取用户当前起点位置（优先从 Redis 缓存获取）
     */
    private double[] getUserCurrentLocation(Long userId) {
        try {
            com.anxin.travel.agent.service.MemoryService memoryService = 
                com.anxin.travel.common.util.SpringContextUtil.getBean(com.anxin.travel.agent.service.MemoryService.class);
            
            // 尝试获取最近一次上报的位置
            double[] location = memoryService.getLocation("user_" + userId);
            if (location != null) {
                log.info("📍 从缓存获取用户起点：lat={}, lng={}", location[0], location[1]);
                return location;
            }
        } catch (Exception e) {
            log.warn("获取用户位置缓存失败", e);
        }
        
        // 默认 fallback：潮州市中心
        log.warn("⚠️ 未找到用户位置，使用默认起点（潮州市中心）");
        return new double[]{23.6533, 116.6772};
    }

    /**
     * 快速创建订单（简化版，用于 AI 对话中直接下单）
     */
    @Transactional
    public OrderVO quickCreateOrder(Long userId, Double destLat, Double destLng, String destName) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setDestLat(destLat);
        request.setDestLng(destLng);
        request.setDestName(destName);
        return createOrder(userId, request);
    }

    /**
     * 计算预估价格（仿滴滴模式：基于真实道路距离）
     */
    private BigDecimal calculateEstimatePrice(Double startLat, Double startLng, Double destLat, Double destLng) {
        try {
            // 1. 获取真实驾车路线信息
            // 【关键修复】高德 API 要求坐标格式：lng,lat（经度，纬度）
            String origin = String.format("%.6f,%.6f", startLng, startLat);      // lng,lat
            String destination = String.format("%.6f,%.6f", destLng, destLat);  // lng,lat
            
            log.info("🚗 正在计算真实路线价格：{} -> {}", origin, destination);
            com.anxin.travel.module.map.dto.RouteResult route = amapClient.getRoute(origin, destination, "driving");
            
            if (route == null || route.getDistance() <= 0) {
                log.warn("⚠️ 路线规划失败，使用备用直线距离计价");
                return calculateBackupPrice(startLat, startLng, destLat, destLng);
            }

            // 2. 提取真实数据（高德返回单位：米、秒）
            double distanceKm = route.getDistance() / 1000.0; // 转为公里
            double durationMin = route.getDuration() / 60.0;  // 转为分钟
            
            log.info("📊 真实路况：距离={}km, 预计耗时={}min", distanceKm, durationMin);

            // 3. 仿滴滴计价模型
            return calculateDidiStylePrice(distanceKm, durationMin);

        } catch (Exception e) {
            log.error("❌ 真实价格计算异常，降级为备用方案", e);
            return calculateBackupPrice(startLat, startLng, destLat, destLng);
        }
    }

    /**
     * 仿滴滴计价核心算法
     * @param distanceKm 真实驾驶距离（公里）
     * @param durationMin 预计驾驶时长（分钟）
     */
    private BigDecimal calculateDidiStylePrice(double distanceKm, double durationMin) {
        // --- 基础参数（可根据城市调整）---
        double basePrice = 10.0;      // 起步价
        double baseDistance = 3.0;    // 起步里程（公里）
        double pricePerKm = 2.5;      // 里程费（元/公里）
        double pricePerMin = 0.5;     // 时长费（元/分钟）- 模拟拥堵成本
        double longDistanceThreshold = 15.0; // 远途费起征点（公里）
        double longDistanceFee = 1.0; // 远途费（超过部分每公里加收）

        // 1. 里程费计算
        double distanceFee = 0;
        if (distanceKm <= baseDistance) {
            distanceFee = 0; // 包含在起步价内
        } else {
            distanceFee = (distanceKm - baseDistance) * pricePerKm;
        }

        // 2. 远途费计算（超过 15 公里部分加收）
        double longDistanceSurcharge = 0;
        if (distanceKm > longDistanceThreshold) {
            longDistanceSurcharge = (distanceKm - longDistanceThreshold) * longDistanceFee;
        }

        // 3. 时长费计算（模拟堵车情况）
        double timeFee = durationMin * pricePerMin;

        // 4. 总价 = 起步价 + 里程费 + 时长费 + 远途费
        double total = basePrice + distanceFee + timeFee + longDistanceSurcharge;

        // 最低消费保护
        total = Math.max(total, 10.0);

        log.info("💰 计价明细：起步={} + 里程={} + 时长={} + 远途={} = {}", 
                basePrice, distanceFee, timeFee, longDistanceSurcharge, total);

        return BigDecimal.valueOf(total).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 备用计价方案（当无法获取路线时使用 Haversine 直线距离）
     */
    private BigDecimal calculateBackupPrice(double startLat, double startLng, double destLat, double destLng) {
        double distance = calculateDistance(startLat, startLng, destLat, destLng);
        // 简单估算：直线距离 * 1.3 系数近似为道路距离
        double estimatedRoadDistance = distance * 1.3;
        return calculateDidiStylePrice(estimatedRoadDistance, estimatedRoadDistance * 3); // 假设平均时速 20km/h
    }
    
    /**
     * 计算两点之间的距离（Haversine 公式）
     * @return 距离（公里）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // 地球半径（公里）
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    private String generateOrderNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(9000) + 1000;
        return "AX" + time + random;
    }

    @Override
    public OrderVO getOrder(Long orderId, Long userId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        // ⭐ 修复：允许乘车人(userId)或代叫人(proxyUserId)查询订单
        if (!order.getUserId().equals(userId) && 
            (order.getProxyUserId() == null || !order.getProxyUserId().equals(userId))) {
            throw new RuntimeException("无权查看他人订单");
        }
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setPoiName(order.getDestAddress());  // 【修复】设置 poiName 字段，与创建订单时保持一致
        
        // ⭐ 如果订单有司机信息，填充到 VO
        if (order.getDriverName() != null) {
            vo.setDriverName(order.getDriverName());
            vo.setDriverPhone(order.getDriverPhone());
            vo.setCarNo(order.getCarNo());
            vo.setCarType(order.getCarType());
            vo.setCarColor(order.getCarColor());
            vo.setRating(order.getRating() != null ? order.getRating().doubleValue() : null);
            vo.setDriverLat(order.getDriverLat());
            vo.setDriverLng(order.getDriverLng());
            // 头像动态生成
            vo.setDriverAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + order.getDriverName());
        }
        
        log.info("✅ 查询订单详情：orderId={}, poiName={}, destAddress={}, hasDriver={}", 
            orderId, vo.getPoiName(), vo.getDestAddress(), vo.getDriverName() != null);
        return vo;
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权取消他人订单");
        }
        if (order.getStatus() != 0) {
            throw new RuntimeException("当前状态不可取消");
        }
        order.setStatus(4);
        orderMapper.updateById(order);
        log.info("订单已取消，订单号：{}", order.getOrderNo());
    }

    @Override
    @Transactional
    public void confirmOrder(Long orderId, Long userId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权确认他人订单");
        }
        if (order.getStatus() != 1) {
            throw new RuntimeException("订单状态不正确，无法确认");
        }
        order.setStatus(3);  // 3-司机已接单（兼容旧逻辑）
        orderMapper.updateById(order);
        log.info("订单已确认完成，订单号：{}", order.getOrderNo());
    }

    @Override
    public Page<OrderVO> listOrders(Long userId, Integer status, Integer page, Integer size) {
        log.info("查询订单列表，userId: {}, status: {}, page: {}, size: {}", userId, status, page, size);
        
        Page<OrderInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getCreateTime);
        
        Page<OrderInfo> orderPage = orderMapper.selectPage(pageParam, wrapper);
        log.info("数据库查询完成，总记录数：{}, 当前页数据量：{}", orderPage.getTotal(), orderPage.getRecords().size());

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = orderPage.getRecords().stream().map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(order, vo);
            vo.setPoiName(order.getDestAddress());  // ✅ 设置 poiName 字段（前端期望字段）
            
            // ⭐ 如果订单有司机信息，填充到 VO
            if (order.getDriverName() != null) {
                vo.setDriverName(order.getDriverName());
                vo.setDriverPhone(order.getDriverPhone());
                vo.setCarNo(order.getCarNo());
                vo.setCarType(order.getCarType());
                vo.setCarColor(order.getCarColor());
                vo.setRating(order.getRating() != null ? order.getRating().doubleValue() : null);
                vo.setDriverLat(order.getDriverLat());
                vo.setDriverLng(order.getDriverLng());
                vo.setDriverAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + order.getDriverName());
            }
            
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        log.info("订单列表转换完成，返回 {} 条记录", voList.size());
        return voPage;
    }
    
    @Override
    public OrderVO getCurrentOrder(Long userId) {
        log.info("【亲情守护】查询当前订单，userId: {}", userId);
        
        // 查询条件：user_id=userId OR proxy_user_id=userId，且状态为进行中(0/1/2/3/4/5)
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .and(w -> w.eq(OrderInfo::getUserId, userId)
                        .or()
                        .eq(OrderInfo::getProxyUserId, userId))
                .in(OrderInfo::getStatus, 0, 1, 2, 3, 4, 5)  // 待确认/已确认/等待司机接单/司机已接单/司机已到达/行程中
                .orderByDesc(OrderInfo::getCreateTime)
                .last("LIMIT 1");
        
        OrderInfo order = orderMapper.selectOne(wrapper);
        if (order == null) {
            log.info("当前无进行中的订单");
            return null;
        }
        
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setPoiName(order.getDestAddress());
        
        // ⭐ 如果订单有司机信息，填充到 VO
        if (order.getDriverName() != null) {
            vo.setDriverName(order.getDriverName());
            vo.setDriverPhone(order.getDriverPhone());
            vo.setCarNo(order.getCarNo());
            vo.setCarType(order.getCarType());
            vo.setCarColor(order.getCarColor());
            vo.setRating(order.getRating() != null ? order.getRating().doubleValue() : null);
            vo.setDriverLat(order.getDriverLat());
            vo.setDriverLng(order.getDriverLng());
            vo.setDriverAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + order.getDriverName());
        }
        
        log.info("✅ 查询到当前订单：orderId={}, status={}, isProxy={}, hasDriver={}", 
            order.getId(), order.getStatus(), 
            order.getProxyUserId() != null && order.getProxyUserId().equals(userId),
            vo.getDriverName() != null);
        
        return vo;
    }
    
    @Override
    @Transactional
    public void boardOrder(Long orderId, Long userId) {
        log.info("【乘客上车】orderId={}, userId={}", orderId, userId);
        
        // 1. 验证订单是否存在
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        
        // 2. 验证权限：允许乘车人或代叫人操作
        if (!order.getUserId().equals(userId) && 
            (order.getProxyUserId() == null || !order.getProxyUserId().equals(userId))) {
            throw new RuntimeException("无权操作此订单");
        }
        
        // 3. 验证状态：必须是 4（司机已到达）
        if (order.getStatus() != 4) {
            String statusText = getStatusText(order.getStatus());
            throw new RuntimeException("订单状态不正确，当前状态：" + statusText);
        }
        
        // 4. 更新订单状态：4 -> 5（行程中）
        order.setStatus(5);
        orderMapper.updateById(order);
        log.info("✅ 订单{}状态更新为行程中（status=5）", orderId);
        
        // 5. 触发 WebSocket 推送：TRIP_STARTED
        try {
            pushTripStartedMessage(order);
        } catch (Exception e) {
            log.error("❌ WebSocket 推送 TRIP_STARTED 失败，但订单状态已更新", e);
        }
    }
    
    @Override
    @Transactional
    public void completeOrder(Long orderId, Long userId) {
        log.info("【完成行程】orderId={}, userId={}", orderId, userId);
        
        // 1. 验证订单是否存在
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        
        // 2. 验证权限：允许乘车人或代叫人操作
        if (!order.getUserId().equals(userId) && 
            (order.getProxyUserId() == null || !order.getProxyUserId().equals(userId))) {
            throw new RuntimeException("无权操作此订单");
        }
        
        // 3. 验证状态：必须是 5（行程中）
        if (order.getStatus() != 5) {
            String statusText = getStatusText(order.getStatus());
            throw new RuntimeException("订单状态不正确，当前状态：" + statusText);
        }
        
        // 4. 更新订单状态：5 -> 6（已完成）
        order.setStatus(6);
        // 计算实际费用（暂时使用预估价格）
        if (order.getActualPrice() == null) {
            order.setActualPrice(order.getEstimatePrice());
        }
        orderMapper.updateById(order);
        log.info("✅ 订单{}状态更新为已完成（status=6），actualPrice={}", orderId, order.getActualPrice());
        
        // 5. 触发 WebSocket 推送：TRIP_COMPLETED
        try {
            pushTripCompletedMessage(order);
        } catch (Exception e) {
            log.error("❌ WebSocket 推送 TRIP_COMPLETED 失败，但订单状态已更新", e);
        }
    }
    
    /**
     * 获取状态文本描述
     */
    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待确认";
            case 1: return "已确认";
            case 2: return "等待司机接单";
            case 3: return "司机已接单";
            case 4: return "司机已到达";
            case 5: return "行程中";
            case 6: return "已完成";
            case 7: return "已取消";
            case 8: return "已拒绝";
            default: return "未知状态(" + status + ")";
        }
    }
    
    /**
     * 推送 TRIP_STARTED 消息
     */
    private void pushTripStartedMessage(OrderInfo order) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "TRIP_STARTED");
        message.put("orderId", order.getId());
        message.put("timestamp", System.currentTimeMillis());
        message.put("message", "乘客已上车，行程开始");
        message.put("status", 5);
        
        String messageJson = JSON.toJSONString(message);
        
        // 推送给乘车人（长辈）
        nativeWebSocket.sendMessageToUser(order.getUserId(), messageJson);
        log.info("✅ 已向乘车人 userId={} 推送 TRIP_STARTED", order.getUserId());
        
        // 如果是代叫车订单，也推送给代叫人（亲友）
        if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
            nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
            log.info("✅ 已向代叫人 proxyUserId={} 推送 TRIP_STARTED", order.getProxyUserId());
        }
    }
    
    /**
     * 推送 TRIP_COMPLETED 消息
     */
    private void pushTripCompletedMessage(OrderInfo order) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "TRIP_COMPLETED");
        message.put("orderId", order.getId());
        message.put("timestamp", System.currentTimeMillis());
        message.put("message", "行程已完成，感谢使用！");
        message.put("status", 6);
        message.put("actualPrice", order.getActualPrice());
        
        String messageJson = JSON.toJSONString(message);
        
        // 推送给乘车人（长辈）
        nativeWebSocket.sendMessageToUser(order.getUserId(), messageJson);
        log.info("✅ 已向乘车人 userId={} 推送 TRIP_COMPLETED", order.getUserId());
        
        // 如果是代叫车订单，也推送给代叫人（亲友）
        if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
            nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
            log.info("✅ 已向代叫人 proxyUserId={} 推送 TRIP_COMPLETED", order.getProxyUserId());
        }
    }
}