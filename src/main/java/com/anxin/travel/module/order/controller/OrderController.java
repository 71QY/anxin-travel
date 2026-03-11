package com.anxin.travel.module.order.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.dto.OrderVO;
import com.anxin.travel.module.order.service.OrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<OrderVO> createOrder(@RequestBody CreateOrderRequest request) {
        Long userId = UserContext.getUserId();
        return Result.success(orderService.createOrder(userId, request));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        return Result.success(orderService.getOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        orderService.cancelOrder(id, userId);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<Page<OrderVO>> listOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContext.getUserId();
        return Result.success(orderService.listOrders(userId, status, page, size));
    }
}