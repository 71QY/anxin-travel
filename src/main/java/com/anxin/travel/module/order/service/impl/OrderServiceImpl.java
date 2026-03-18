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
        String orderNo = generateOrderNo();
        BigDecimal estimatePrice = BigDecimal.valueOf(new Random().nextInt(30) + 20);
        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setDestLat(request.getDestLat());
        order.setDestLng(request.getDestLng());
        order.setDestAddress(request.getDestName());
        order.setStatus(0);
        order.setPlatformUsed("gaode");
        order.setEstimatePrice(estimatePrice);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
        log.info("订单创建成功，订单号：{}", orderNo);
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    private String generateOrderNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(9000) + 1000;
        return "AX" + time + random;
    }

    @Override
    public OrderVO getOrder(Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
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
    public Page<OrderVO> listOrders(Long userId, Integer status, Integer page, Integer size) {
        Page<OrderInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getCreateTime);
        Page<OrderInfo> orderPage = orderMapper.selectPage(pageParam, wrapper);

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = orderPage.getRecords().stream().map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(order, vo);
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }
}