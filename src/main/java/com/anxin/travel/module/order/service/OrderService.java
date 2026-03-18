package com.anxin.travel.module.order.service;

import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.dto.OrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface OrderService {
    OrderVO createOrder(Long userId, CreateOrderRequest request);
    OrderVO getOrder(Long orderId);
    void cancelOrder(Long orderId, Long userId);
    Page<OrderVO> listOrders(Long userId, Integer status, Integer page, Integer size);
}