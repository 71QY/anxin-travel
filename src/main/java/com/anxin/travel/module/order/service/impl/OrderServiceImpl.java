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
        BigDecimal estimatePrice = calculateEstimatePrice(request.getDestLat(), request.getDestLng());
        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
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
        log.info("✅ 订单创建成功：orderNo={}, poiName={}, destAddress={}, lat={}, lng={}", 
            vo.getOrderNo(), vo.getPoiName(), vo.getDestAddress(), vo.getDestLat(), vo.getDestLng());
        return vo;
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

    private BigDecimal calculateEstimatePrice(Double destLat, Double destLng) {
        double startLat = 30.0;
        double startLng = 120.0;
        double distance = Math.sqrt(Math.pow(destLat - startLat, 2) + Math.pow(destLng - startLng, 2)) * 111;
        double basePrice = 10.0;
        double pricePerKm = 2.5;
        double total = basePrice + distance * pricePerKm;
        return BigDecimal.valueOf(Math.max(total, 15.0));
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
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        log.info("订单列表转换完成，返回 {} 条记录", voList.size());
        return voPage;
    }
}