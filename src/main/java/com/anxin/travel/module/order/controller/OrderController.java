package com.anxin.travel.module.order.controller;

import com.anxin.travel.common.result.PageResult;
import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.dto.OrderVO;
import com.anxin.travel.module.order.service.OrderService;
import com.anxin.travel.module.order.service.DriverAssignmentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final DriverAssignmentService driverAssignmentService;

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
    
    /**
     * 用户确认/拒绝司机接单
     */
    @PostMapping("/driver/confirm")
    public Result<Void> confirmDriver(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long orderId = Long.valueOf(request.get("orderId").toString());
        Boolean accepted = (Boolean) request.get("accepted");
        
        log.info("🔍 【重要】用户{}手动调用确认接口，orderId={}, accepted={}, request={}", userId, orderId, accepted, request);
        driverAssignmentService.confirmDriverAcceptance(orderId, userId, accepted);
        return Result.success();
    }
    
    /**
     * 乘客上车/开始行程
     * 将订单状态从 4（司机已到达）更新为 5（行程中）
     */
    @PostMapping("/{id}/board")
    public Result<Void> boardOrder(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("【乘客上车】userId={}, orderId={}", userId, id);
        orderService.boardOrder(id, userId);
        return Result.success();
    }
    
    /**
     * 到达目的地/完成行程
     * 将订单状态从 5（行程中）更新为 6（已完成）
     */
    @PostMapping("/{id}/complete")
    public Result<Void> completeOrder(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("【完成行程】userId={}, orderId={}", userId, id);
        orderService.completeOrder(id, userId);
        return Result.success();
    }
    
    /**
     * 【测试接口】模拟司机接单
     * ⚠️ 已禁用：此接口会绕过用户确认直接接单，生产环境不应使用
     */
    @PostMapping("/test/mock-driver-accept/{orderId}")
    public Result<Void> mockDriverAccept(@PathVariable Long orderId) {
        log.error("❌❌❌ 【禁止调用】测试接口被调用！orderId={}, userId={} - 此接口已禁用，请使用正常流程确认司机", 
            orderId, UserContext.getUserId());
        return Result.error("此接口已禁用，请通过正常流程确认司机");
        // orderService.mockDriverAccept(orderId);
        // return Result.success();
    }
}