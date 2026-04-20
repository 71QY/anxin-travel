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
        
        // 【关键】判断是否为代叫车订单
        boolean isProxyOrder = request.getElderId() != null && request.getElderId() > 0;
        Long elderUserId = isProxyOrder ? request.getElderId() : userId;  // 乘车人ID
        Long proxyUserId = isProxyOrder ? userId : null;  // 代叫人ID
        
        // 【关键】确定起点位置：代叫车使用前端传的 startLat/startLng，普通订单从 Redis 获取
        double startLat, startLng;
        if (isProxyOrder && request.getStartLat() != null && request.getStartLng() != null) {
            // 代叫车：使用长辈当前位置作为起点
            startLat = request.getStartLat();
            startLng = request.getStartLng();
            log.info("🚗 代叫车订单：起点=长辈位置({},{})", startLat, startLng);
        } else {
            // 普通订单：从 Redis 获取用户位置
            double[] userLocation = getUserCurrentLocation(userId);
            startLat = userLocation[0];
            startLng = userLocation[1];
        }
        
        // 基于真实起点和终点计算价格
        BigDecimal estimatePrice = calculateEstimatePrice(startLat, startLng, request.getDestLat(), request.getDestLng());
        
        // ⭐ 校验起点和终点是否相同
        double latDiff = Math.abs(startLat - request.getDestLat());
        double lngDiff = Math.abs(startLng - request.getDestLng());
        if (latDiff < 0.0001 && lngDiff < 0.0001) {
            log.warn("⚠️ 起点和终点坐标相同：start=({},{}), dest=({},{})", 
                startLat, startLng, request.getDestLat(), request.getDestLng());
            throw new RuntimeException("起点和终点不能相同，请重新选择目的地");
        }
        
        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setUserId(elderUserId);  // 乘车人（长辈）
        order.setProxyUserId(proxyUserId);  // 代叫人（亲友）
        order.setElderUserId(elderUserId);  // 冗余字段，方便查询
        order.setStartLat(startLat);  // 记录起点
        order.setStartLng(startLng);  // 记录起点
        order.setDestLat(request.getDestLat());
        order.setDestLng(request.getDestLng());
        order.setDestAddress(request.getDestName().trim());
        
        // 【关键】设置订单状态：代叫车为 0-待确认，普通订单为 2-等待司机接单
        if (isProxyOrder) {
            order.setStatus(0);  // 待长辈确认
            log.info("📝 创建代叫车订单：orderId={}, elderId={}, proxyId={}", order.getId(), elderUserId, proxyUserId);
        } else {
            order.setStatus(2);  // 等待司机接单
            log.info("📝 创建普通订单：orderId={}, userId={}", order.getId(), userId);
        }
        
        order.setPlatformUsed("gaode");
        order.setEstimatePrice(estimatePrice);
        order.setCreateTime(LocalDateTime.now());
        
        int rows = orderMapper.insert(order);
        log.info("订单插入数据库，影响行数：{}, 订单号：{}, ID: {}", rows, orderNo, order.getId());
        
        // ⭐ 如果是代叫车订单，推送 NEW_ORDER 消息给长辈
        if (isProxyOrder) {
            try {
                pushNewOrderMessage(order, proxyUserId, elderUserId);
                log.info("✅ 已向长辈 userId={} 推送 NEW_ORDER 消息", elderUserId);
            } catch (Exception e) {
                log.error("❌ 推送 NEW_ORDER 消息失败", e);
            }
        } else {
            // 普通订单：触发司机分配
            try {
                driverAssignmentService.assignDriverAndStartSimulation(
                    order.getId(), userId, startLat, startLng, 
                    request.getDestLat(), request.getDestLng()
                );
                log.info("✅ 已触发司机分配任务，orderId={}", order.getId());
            } catch (Exception e) {
                log.error("❌ 触发司机分配失败，但订单已创建成功", e);
            }
            
            // ⭐ 删除自动接单逻辑，让用户手动确认司机
            // assignDriverAndStartSimulation 会推送 DRIVER_REQUEST 给用户
            // 用户需要调用 /api/order/driver/confirm 接口确认或拒绝司机
        }
        
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setPoiName(vo.getDestAddress());  // 【关键】前端期望 poiName 字段
        
        // ⭐ 亲情守护：设置 guardianUserId（代叫人ID）
        if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
            vo.setGuardianUserId(order.getProxyUserId());
        }
        
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
        
        // ⭐ 亲情守护：设置 guardianUserId（代叫人ID）
        if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
            vo.setGuardianUserId(order.getProxyUserId());
        }
        
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
        
        // ⭐ 修复：只有代叫人（亲友）可以取消订单，乘车人（长辈）不能取消
        if (order.getProxyUserId() == null || !order.getProxyUserId().equals(userId)) {
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
        // ⭐ 亲情守护：允许长辈查看自己作为乘车人的订单 + 作为被代叫人的订单
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .and(w -> w.eq(OrderInfo::getUserId, userId)
                        .or()
                        .eq(OrderInfo::getElderUserId, userId))
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getCreateTime);
        
        Page<OrderInfo> orderPage = orderMapper.selectPage(pageParam, wrapper);
        log.info("数据库查询完成，总记录数：{}, 当前页数据量：{}", orderPage.getTotal(), orderPage.getRecords().size());

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = orderPage.getRecords().stream().map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(order, vo);
            vo.setPoiName(order.getDestAddress());  // ✅ 设置 poiName 字段（前端期望字段）
            
            // ⭐ 亲情守护：设置 guardianUserId（代叫人ID）
            if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
                vo.setGuardianUserId(order.getProxyUserId());
            }
            
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
        
        // ⭐ 亲情守护：设置 guardianUserId（代叫人ID）
        if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
            vo.setGuardianUserId(order.getProxyUserId());
        }
        
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
        
        // ⭐ 6. 根据实际距离动态计算行驶时间，模拟司机到达目的地
        final Long finalOrderId = orderId;
        final Double finalDestLat = order.getDestLat();
        final Double finalDestLng = order.getDestLng();
        final Double startLat = order.getStartLat();
        final Double startLng = order.getStartLng();
        
        new Thread(() -> {
            try {
                // 1. 计算起点到终点的直线距离（公里）
                double distanceKm = calculateDistance(startLat, startLng, finalDestLat, finalDestLng);
                
                // 2. 根据距离动态计算行驶时间（假设平均时速 30km/h）
                // 最短 30 秒，最长 180 秒（3分钟）
                int estimatedDurationSeconds = (int) (distanceKm / 30.0 * 3600);  // 距离/速度=时间（小时）-> 秒
                estimatedDurationSeconds = Math.max(30, Math.min(180, estimatedDurationSeconds));  // 限制在 30-180 秒
                
                log.info("⏰ 开始模拟行程：距离={}km, 预计耗时={}秒, orderId={}", 
                    String.format("%.2f", distanceKm), estimatedDurationSeconds, finalOrderId);
                
                // 3. 分步模拟行驶过程（每 3 秒更新一次位置）
                int totalSteps = Math.max(5, estimatedDurationSeconds / 3);  // 至少 5 步
                int stepIntervalMs = (estimatedDurationSeconds * 1000) / totalSteps;  // 每步间隔（毫秒）
                
                double currentLat = order.getDriverLat() != null ? order.getDriverLat() : startLat;
                double currentLng = order.getDriverLng() != null ? order.getDriverLng() : startLng;
                double latStep = (finalDestLat - currentLat) / totalSteps;
                double lngStep = (finalDestLng - currentLng) / totalSteps;
                
                for (int i = 1; i <= totalSteps; i++) {
                    Thread.sleep(stepIntervalMs);  // 动态间隔
                    
                    // ⭐ 每次更新前检查订单状态，如果状态不再是 5（行程中），立即退出
                    OrderInfo checkingOrder = orderMapper.selectById(finalOrderId);
                    if (checkingOrder == null) {
                        log.warn("⚠️ 订单不存在，终止模拟行驶，orderId={}", finalOrderId);
                        return;
                    }
                    if (checkingOrder.getStatus() != 5) {
                        log.info("⚠️ 订单状态已变更为 {}（{}），终止模拟行驶，orderId={}", 
                            checkingOrder.getStatus(), getStatusText(checkingOrder.getStatus()), finalOrderId);
                        return;
                    }
                    
                    double newLat = currentLat + latStep * i;
                    double newLng = currentLng + lngStep * i;
                    
                    // 更新数据库中的司机位置
                    OrderInfo updatingOrder = orderMapper.selectById(finalOrderId);
                    if (updatingOrder != null && updatingOrder.getStatus() == 5) {
                        updatingOrder.setDriverLat(newLat);
                        updatingOrder.setDriverLng(newLng);
                        orderMapper.updateById(updatingOrder);
                        
                        // 推送位置更新
                        int remainingSeconds = (totalSteps - i) * stepIntervalMs / 1000;
                        Map<String, Object> locationMsg = new HashMap<>();
                        locationMsg.put("type", "DRIVER_LOCATION");
                        locationMsg.put("orderId", finalOrderId);
                        locationMsg.put("driverLat", newLat);
                        locationMsg.put("driverLng", newLng);
                        locationMsg.put("etaMinutes", remainingSeconds / 60);  // 剩余分钟数
                        
                        String messageJson = JSON.toJSONString(locationMsg);
                        nativeWebSocket.sendMessageToUser(updatingOrder.getUserId(), messageJson);
                        
                        // 如果是代叫车订单，也推送给代叫人
                        if (updatingOrder.getProxyUserId() != null && updatingOrder.getProxyUserId() > 0) {
                            nativeWebSocket.sendMessageToUser(updatingOrder.getProxyUserId(), messageJson);
                        }
                        
                        log.debug("📍 行程中位置更新：step={}/{}, lat={}, lng={}, eta={}s", 
                            i, totalSteps, newLat, newLng, remainingSeconds);
                    } else {
                        log.info("⚠️ 订单状态异常，终止位置更新，orderId={}, status={}", 
                            finalOrderId, updatingOrder != null ? updatingOrder.getStatus() : "null");
                        return;
                    }
                }
                
                // ⭐ 最后再次验证状态，确保订单仍然是行程中
                OrderInfo finalCheck = orderMapper.selectById(finalOrderId);
                if (finalCheck == null || finalCheck.getStatus() != 5) {
                    log.info("⚠️ 订单状态已变更，取消自动完成，orderId={}, status={}", 
                        finalOrderId, finalCheck != null ? finalCheck.getStatus() : "null");
                    return;
                }
                
                // 到达目的地，自动完成行程
                log.info("⏰ 行程结束，自动完成订单，orderId={}", finalOrderId);
                completeOrder(finalOrderId, order.getUserId());
                
            } catch (InterruptedException e) {
                log.error("❌ 模拟行驶被中断，orderId={}", finalOrderId, e);
            } catch (Exception e) {
                log.error("❌ 模拟行驶失败，orderId={}", finalOrderId, e);
            }
        }, "trip-simulation-" + orderId).start();
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
    
    /**
     * ⭐ 推送 NEW_ORDER 消息给长辈（代叫车订单创建后）
     */
    private void pushNewOrderMessage(OrderInfo order, Long proxyUserId, Long elderUserId) {
        try {
            // 1. 获取代叫人姓名（从数据库查询）
            String proxyUserName = "亲友";
            try {
                com.anxin.travel.module.user.mapper.UserMapper userMapper = 
                    com.anxin.travel.common.util.SpringContextUtil.getBean(com.anxin.travel.module.user.mapper.UserMapper.class);
                var proxyUser = userMapper.selectById(proxyUserId);
                if (proxyUser != null && proxyUser.getNickname() != null) {
                    proxyUserName = proxyUser.getNickname();
                }
            } catch (Exception e) {
                log.warn("获取代叫人姓名失败，使用默认值", e);
            }
            
            // 2. 构建消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "NEW_ORDER");
            message.put("orderId", order.getId());
            message.put("orderNo", order.getOrderNo());  // ⭐ 新增：订单号
            message.put("proxyUserId", proxyUserId);
            message.put("proxyUserName", proxyUserName);
            message.put("destAddress", order.getDestAddress());
            message.put("poiName", order.getDestAddress());  // ⭐ 新增：目的地名称（与destAddress相同）
            message.put("destLat", order.getDestLat());
            message.put("destLng", order.getDestLng());
            message.put("startLat", order.getStartLat());
            message.put("startLng", order.getStartLng());
            message.put("estimatePrice", order.getEstimatePrice());
            message.put("elderUserId", elderUserId);  // ⭐ 新增：长辈用户ID
            message.put("timestamp", System.currentTimeMillis());
            
            String messageJson = JSON.toJSONString(message);
            
            // 3. 推送给长辈
            nativeWebSocket.sendMessageToUser(elderUserId, messageJson);
            log.info("✅ 已向长辈 userId={} 推送 NEW_ORDER：orderId={}, proxyUser={}", 
                elderUserId, order.getId(), proxyUserName);
            
        } catch (Exception e) {
            log.error("❌ 推送 NEW_ORDER 消息失败", e);
        }
    }
    
    /**
     * 【测试接口】模拟司机接单
     * 将订单状态改为5（行程中），并设置司机信息
     */
    @Override
    @Transactional
    public void mockDriverAccept(Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        mockDriverAcceptInternal(orderId, order.getStartLat(), order.getStartLng());
    }
    
    /**
     * 内部方法：执行司机接单逻辑
     */
    private void mockDriverAcceptInternal(Long orderId, double startLat, double startLng) {
        try {
            log.info("【自动接单】开始处理，orderId={}", orderId);
            
            // 1. 查询订单
            OrderInfo order = orderMapper.selectById(orderId);
            if (order == null) {
                log.error("订单不存在，orderId={}", orderId);
                return;
            }
            
            // 2. 生成随机司机信息
            Map<String, Object> driverInfo = driverAssignmentService.generateRandomDriverInfo();
            
            // 3. 更新订单状态为3（司机已接单），并设置司机信息
            order.setStatus(3);  // ⭐ 修正：先设置为3（司机已接单）
            order.setDriverName((String) driverInfo.get("driverName"));
            order.setDriverPhone((String) driverInfo.get("driverPhone"));
            order.setCarNo((String) driverInfo.get("carNo"));
            order.setCarType((String) driverInfo.get("carType"));
            order.setCarColor((String) driverInfo.get("carColor"));
            Object ratingObj = driverInfo.get("rating");
            if (ratingObj != null) {
                order.setRating(ratingObj instanceof BigDecimal ? (BigDecimal) ratingObj : BigDecimal.valueOf((Double) ratingObj));
            }
            
            // 4. 设置司机位置（起点附近）
            Random random = new Random();
            double offsetLat = (random.nextDouble() - 0.5) * 0.01;
            double offsetLng = (random.nextDouble() - 0.5) * 0.01;
            double driverLat = startLat + offsetLat;
            double driverLng = startLng + offsetLng;
            order.setDriverLat(driverLat);
            order.setDriverLng(driverLng);
            
            // 5. 获取一个真实司机ID
            try {
                com.anxin.travel.module.order.mapper.DriverMapper driverMapper = 
                    com.anxin.travel.common.util.SpringContextUtil.getBean(com.anxin.travel.module.order.mapper.DriverMapper.class);
                var driver = driverMapper.selectOne(new LambdaQueryWrapper<com.anxin.travel.module.order.entity.Driver>().last("LIMIT 1"));
                if (driver != null) {
                    order.setDriverId(driver.getId());
                }
            } catch (Exception e) {
                log.warn("获取司机ID失败，使用null", e);
            }
            
            orderMapper.updateById(order);
            log.info("✅ 订单{}已自动接单，status=3, driver={}", orderId, order.getDriverName());
            
            // 6. ⭐ 推送 ORDER_ACCEPTED 消息给前端
            Map<String, Object> acceptedMsg = new HashMap<>();
            acceptedMsg.put("type", "ORDER_ACCEPTED");
            acceptedMsg.put("orderId", orderId);
            acceptedMsg.put("success", true);
            acceptedMsg.put("message", "司机已接单，正在赶来");
            acceptedMsg.put("driverName", order.getDriverName());
            acceptedMsg.put("driverPhone", order.getDriverPhone());
            acceptedMsg.put("driverAvatar", "https://api.dicebear.com/7.x/avataaars/svg?seed=" + order.getDriverName());
            acceptedMsg.put("carNo", order.getCarNo());
            acceptedMsg.put("carType", order.getCarType());
            acceptedMsg.put("carColor", order.getCarColor());
            acceptedMsg.put("rating", order.getRating() != null ? order.getRating().doubleValue() : 4.9);
            acceptedMsg.put("driverLat", driverLat);
            acceptedMsg.put("driverLng", driverLng);
            acceptedMsg.put("startLat", order.getStartLat());
            acceptedMsg.put("startLng", order.getStartLng());
            acceptedMsg.put("destLat", order.getDestLat());
            acceptedMsg.put("destLng", order.getDestLng());
            acceptedMsg.put("etaMinutes", 5);  // ⭐ 新增：预计到达时间（分钟）
            
            String messageJson = JSON.toJSONString(acceptedMsg);
            
            // 推送给乘车人（长辈）
            nativeWebSocket.sendMessageToUser(order.getUserId(), messageJson);
            log.info("✅ 已向乘车人 userId={} 推送 ORDER_ACCEPTED", order.getUserId());
            
            // 如果是代叫车订单，也推送给代叫人（亲友）
            if (order.getProxyUserId() != null && order.getProxyUserId() > 0) {
                nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
                log.info("✅ 已向代叫人 proxyUserId={} 推送 ORDER_ACCEPTED", order.getProxyUserId());
            }
            
            // 7. ⭐ 启动异步任务：模拟司机驶向起点 -> 到达 -> 乘客上车 -> 行程中
            final Long finalOrderId = orderId;
            final double finalDriverLat = driverLat;
            final double finalDriverLng = driverLng;
            new Thread(() -> {
                try {
                    // 阶段1：模拟司机驶向起点（分5步，每3秒一次）
                    int steps = 5;
                    double latStep = (startLat - finalDriverLat) / steps;
                    double lngStep = (startLng - finalDriverLng) / steps;
                    
                    for (int i = 1; i <= steps; i++) {
                        Thread.sleep(3000);  // 每3秒更新一次
                        
                        double newLat = finalDriverLat + latStep * i;
                        double newLng = finalDriverLng + lngStep * i;
                        
                        // 推送 DRIVER_LOCATION
                        Map<String, Object> locationMsg = new HashMap<>();
                        locationMsg.put("type", "DRIVER_LOCATION");
                        locationMsg.put("orderId", finalOrderId);
                        locationMsg.put("driverLat", newLat);
                        locationMsg.put("driverLng", newLng);
                        locationMsg.put("etaMinutes", steps - i);
                        
                        String locationJson = JSON.toJSONString(locationMsg);
                        
                        OrderInfo currentOrder = orderMapper.selectById(finalOrderId);
                        if (currentOrder != null) {
                            nativeWebSocket.sendMessageToUser(currentOrder.getUserId(), locationJson);
                            if (currentOrder.getProxyUserId() != null && currentOrder.getProxyUserId() > 0) {
                                nativeWebSocket.sendMessageToUser(currentOrder.getProxyUserId(), locationJson);
                            }
                        }
                        
                        log.debug("📍 推送司机位置：step={}/{}, lat={}, lng={}", i, steps, newLat, newLng);
                    }
                    
                    // 阶段2：司机到达起点，推送 DRIVER_ARRIVED
                    Thread.sleep(1000);
                    Map<String, Object> arrivedMsg = new HashMap<>();
                    arrivedMsg.put("type", "DRIVER_ARRIVED");
                    arrivedMsg.put("orderId", finalOrderId);
                    arrivedMsg.put("message", "司机已到达上车点，请上车");
                    arrivedMsg.put("driverLat", startLat);
                    arrivedMsg.put("driverLng", startLng);
                    arrivedMsg.put("etaMinutes", 0);  // ⭐ 新增：已到达，ETA为0
                    
                    String arrivedJson = JSON.toJSONString(arrivedMsg);
                    
                    OrderInfo orderAfterArrival = orderMapper.selectById(finalOrderId);
                    if (orderAfterArrival != null) {
                        nativeWebSocket.sendMessageToUser(orderAfterArrival.getUserId(), arrivedJson);
                        if (orderAfterArrival.getProxyUserId() != null && orderAfterArrival.getProxyUserId() > 0) {
                            nativeWebSocket.sendMessageToUser(orderAfterArrival.getProxyUserId(), arrivedJson);
                        }
                        
                        // 更新订单状态为4（司机已到达）
                        orderAfterArrival.setStatus(4);
                        orderAfterArrival.setDriverLat(startLat);
                        orderAfterArrival.setDriverLng(startLng);
                        orderMapper.updateById(orderAfterArrival);
                        log.info("✅ 订单{}状态更新为司机已到达（status=4）", finalOrderId);
                    }
                    
                    log.info("✅ 司机已到达上车点，orderId={}", finalOrderId);
                    
                } catch (Exception e) {
                    log.error("❌ 自动接单后续流程失败，orderId={}", finalOrderId, e);
                }
            }, "auto-driver-movement-" + orderId).start();
            
        } catch (Exception e) {
            log.error("❌ 自动接单失败，orderId={}", orderId, e);
        }
    }
}