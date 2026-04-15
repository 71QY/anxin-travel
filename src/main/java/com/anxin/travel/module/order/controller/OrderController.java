package com.anxin.travel.module.order.controller;

import com.anxin.travel.common.result.PageResult;
import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.dto.OrderVO;
import com.anxin.travel.module.order.service.OrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<OrderVO> createOrder(@RequestBody CreateOrderRequest request) {
        Long userId = UserContext.getUserId();
        log.info("创建订单，userId: {}, 请求：{}", userId, request);
        OrderVO order = orderService.createOrder(userId, request);
        log.info("订单创建成功，orderId: {}", order.getId());
        return Result.success(order);
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("查询订单，userId: {}, orderId: {}", userId, id);
        return Result.success(orderService.getOrder(id, userId));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("取消订单，userId: {}, orderId: {}", userId, id);
        orderService.cancelOrder(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirmOrder(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("确认订单，userId: {}, orderId: {}", userId, id);
        orderService.confirmOrder(id, userId);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<PageResult<OrderVO>> listOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContext.getUserId();
        log.info("查询订单列表，userId: {}, status: {}, page: {}, size: {}", userId, status, page, size);
        Page<OrderVO> orderPage = orderService.listOrders(userId, status, page, size);
        log.info("查询到 {} 条订单", orderPage.getTotal());
        
        PageResult<OrderVO> result = new PageResult<>();
        result.setList(orderPage.getRecords());
        result.setTotal(orderPage.getTotal());
        result.setPage(orderPage.getCurrent());
        result.setSize(orderPage.getSize());
        
        return Result.success(result);
    }
    
    /**
     * 【亲情守护】查询当前进行中的订单
     * 支持：长辈(user_id) 或 亲友(proxy_user_id) 查询
     */
    @GetMapping("/current")
    public Result<OrderVO> getCurrentOrder() {
        Long userId = UserContext.getUserId();
        log.info("【亲情守护】查询当前订单，userId: {}", userId);
        OrderVO order = orderService.getCurrentOrder(userId);
        return Result.success(order);
    }
}