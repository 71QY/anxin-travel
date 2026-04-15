package com.anxin.travel.module.guard.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.guard.dto.AddGuardianRequest;
import com.anxin.travel.module.guard.dto.BindExistingElderRequest;
import com.anxin.travel.module.guard.dto.ConfirmProxyOrderRequest;
import com.anxin.travel.module.guard.dto.ProxyOrderRequest;
import com.anxin.travel.module.guard.dto.RegisterElderRequest;
import com.anxin.travel.module.guard.service.FamilyGuardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/guard")
public class FamilyGuardController {

    @Autowired
    private FamilyGuardService familyGuardService;

    /**
     * API-01: 添加长辈绑定（亲友端）
     */
    @PostMapping("/add")
    public Result<?> addGuardian(@RequestBody AddGuardianRequest request) {
        Long guardianId = UserContext.getUserId();
        log.info("👨‍👩‍ 亲友{}添加长辈绑定", guardianId);
        return familyGuardService.addGuardian(guardianId, request);
    }

    /**
     * API-01-NEW: 亲友帮长辈注册账号（新接口）
     */
    @PostMapping("/register-elder")
    public Result<?> registerElder(@RequestBody RegisterElderRequest request) {
        Long guardianId = UserContext.getUserId();
        log.info("👨‍‍👧 亲友{}帮长辈注册账号", guardianId);
        return familyGuardService.registerElder(guardianId, request);
    }

    /**
     * API-01-BIND: 绑定已有长辈账号（新接口）
     */
    @PostMapping("/bind-existing-elder")
    public Result<?> bindExistingElder(@RequestBody BindExistingElderRequest request) {
        Long guardianId = UserContext.getUserId();
        log.info("👨‍‍👧 亲友{}绑定已有长辈账号", guardianId);
        return familyGuardService.bindExistingElder(guardianId, request);
    }

    /**
     * API-02: 查询我的长辈列表（亲友端）
     */
    @GetMapping("/myElders")
    public Result<?> getMyElders() {
        Long guardianId = UserContext.getUserId();
        log.info("👨‍👧 亲友{}查询长辈列表", guardianId);
        return familyGuardService.getMyElders(guardianId);
    }

    /**
     * API-04: 查询我的亲友列表（长辈端）
     */
    @GetMapping("/myGuardians")
    public Result<?> getMyGuardians() {
        Long elderId = UserContext.getUserId();
        log.info("👴 长辈{}查询亲友列表", elderId);
        return familyGuardService.getMyGuardians(elderId);
    }

    /**
     * API-05: 一键解绑所有亲友（长辈端）
     */
    @PostMapping("/unbindAll")
    public Result<?> unbindAll() {
        Long elderId = UserContext.getUserId();
        log.info("👴 长辈{}一键解绑所有亲友", elderId);
        return familyGuardService.unbindAll(elderId);
    }

    /**
     * API-06: 单条解绑（亲友端）
     */
    @PostMapping("/unbindOne/{guardId}")
    public Result<?> unbindOne(@PathVariable Long guardId) {
        Long guardianId = UserContext.getUserId();
        log.info("👨‍👩‍👧 亲友{}解绑绑定记录{}", guardianId, guardId);
        return familyGuardService.unbindOne(guardId, guardianId);
    }

    /**
     * API-B1: 代叫车下单（亲友端）
     */
    @PostMapping("/proxyOrder")
    public Result<?> proxyOrder(@RequestBody ProxyOrderRequest request) {
        Long guardianId = UserContext.getUserId();
        log.info("👨‍‍👧 亲友{}代叫车，长辈ID: {}", guardianId, request.getElderId());
        return familyGuardService.proxyOrder(guardianId, request);
    }

    /**
     * API-B2: 长辈确认/拒绝代叫车请求
     */
    @PostMapping("/confirmProxyOrder/{orderId}")
    public Result<?> confirmProxyOrder(@PathVariable Long orderId, @RequestBody ConfirmProxyOrderRequest request) {
        Long elderId = UserContext.getUserId();
        log.info("👴 长辈{}确认代叫车订单{}, confirmed={}", elderId, orderId, request.getConfirmed());
        return familyGuardService.confirmProxyOrder(elderId, orderId, request);
    }

    /**
     * API-B3: 查询代叫订单列表（亲友端）
     */
    @GetMapping("/proxyOrders")
    public Result<?> getProxyOrders() {
        Long guardianId = UserContext.getUserId();
        log.info("👨‍👩‍ 亲友{}查询代叫订单列表", guardianId);
        return familyGuardService.getProxyOrders(guardianId);
    }

    /**
     * API-D1: 呼叫司机
     */
    @GetMapping("/callDriver/{orderId}")
    public Result<?> callDriver(@PathVariable Long orderId) {
        log.info("📞 呼叫司机，订单ID: {}", orderId);
        return familyGuardService.callDriver(orderId);
    }

    /**
     * API-D2: 呼叫亲友
     */
    @GetMapping("/callGuardian")
    public Result<?> callGuardian() {
        Long elderId = UserContext.getUserId();
        log.info("📞 长辈{}呼叫亲友", elderId);
        return familyGuardService.callGuardian(elderId);
    }
}
