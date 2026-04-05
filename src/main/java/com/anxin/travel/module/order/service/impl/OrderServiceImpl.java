package com.anxin.travel.module.order.service.impl;

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
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final com.anxin.travel.module.map.client.AmapClient amapClient;

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
        
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setPoiName(vo.getDestAddress());  // 【关键】前端期望 poiName 字段
        log.info("✅ 订单创建成功：orderNo={}, poiName={}, destAddress={}, price={}", 
            vo.getOrderNo(), vo.getPoiName(), vo.getDestAddress(), vo.getEstimatePrice());
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
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看他人订单");
        }
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setPoiName(order.getDestAddress());  // 【修复】设置 poiName 字段，与创建订单时保持一致
        log.info("✅ 查询订单详情：orderId={}, poiName={}, destAddress={}", 
            orderId, vo.getPoiName(), vo.getDestAddress());
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
        order.setStatus(3);
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
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        log.info("订单列表转换完成，返回 {} 条记录", voList.size());
        return voPage;
    }
}