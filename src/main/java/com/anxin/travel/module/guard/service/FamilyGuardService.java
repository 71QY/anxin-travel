package com.anxin.travel.module.guard.service;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.module.guard.dto.AddGuardianRequest;
import com.anxin.travel.module.guard.dto.BindExistingElderRequest;
import com.anxin.travel.module.guard.dto.ConfirmProxyOrderRequest;
import com.anxin.travel.module.guard.dto.ProxyOrderRequest;
import com.anxin.travel.module.guard.dto.RegisterElderRequest;

/**
 * 亲情守护服务接口
 */
public interface FamilyGuardService {
    
    /**
     * 添加长辈绑定（亲友端）
     */
    Result<?> addGuardian(Long guardianId, AddGuardianRequest request);
    
    /**
     * 亲友帮长辈注册账号（新接口）
     */
    Result<?> registerElder(Long guardianId, RegisterElderRequest request);
    
    /**
     * 绑定已有的长辈账号（新接口）
     */
    Result<?> bindExistingElder(Long guardianId, BindExistingElderRequest request);
    
    /**
     * 查询我的长辈列表（亲友端）
     */
    Result<?> getMyElders(Long guardianId);
    
    /**
     * 批量激活绑定（长辈登录时调用）
     */
    Result<?> batchActivateGuardians(Long elderId, String elderPhone);
    
    /**
     * 查询我的亲友列表（长辈端）
     */
    Result<?> getMyGuardians(Long elderId);
    
    /**
     * 一键解绑所有亲友（长辈端）
     */
    Result<?> unbindAll(Long elderId);
    
    /**
     * 单条解绑（亲友端）
     */
    Result<?> unbindOne(Long guardId, Long guardianId);
    
    /**
     * 代叫车下单（亲友端）
     */
    Result<?> proxyOrder(Long guardianId, ProxyOrderRequest request);
    
    /**
     * 长辈确认/拒绝代叫车请求
     */
    Result<?> confirmProxyOrder(Long elderId, Long orderId, ConfirmProxyOrderRequest request);
    
    /**
     * 查询代叫订单列表（亲友端）
     */
    Result<?> getProxyOrders(Long guardianId);
    
    /**
     * 呼叫司机（返回司机手机号）
     */
    Result<?> callDriver(Long orderId);
    
    /**
     * 呼叫亲友（返回第一个亲友手机号）
     */
    Result<?> callGuardian(Long elderId);
}
