package com.anxin.travel.module.order.service;

import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.dto.OrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface OrderService {
    OrderVO createOrder(Long userId, CreateOrderRequest request);
    OrderVO getOrder(Long orderId, Long userId);
    void cancelOrder(Long orderId, Long userId);
    void confirmOrder(Long orderId, Long userId);
    Page<OrderVO> listOrders(Long userId, Integer status, Integer page, Integer size);
    
    /**
     * 【亲情守护】查询当前进行中的订单
     * 支持：长辈(user_id) 或 亲友(proxy_user_id) 查询
     */
    OrderVO getCurrentOrder(Long userId);
    
    /**
     * 乘客上车/开始行程
     * @param orderId 订单ID
     * @param userId 用户ID（乘车人或代叫人）
     */
    void boardOrder(Long orderId, Long userId);
    
    /**
     * 到达目的地/完成行程
     * @param orderId 订单ID
     * @param userId 用户ID（乘车人或代叫人）
     */
    void completeOrder(Long orderId, Long userId);
    
    /**
     * 【测试接口】模拟司机接单
     * @param orderId 订单ID
     */
    void mockDriverAccept(Long orderId);
}