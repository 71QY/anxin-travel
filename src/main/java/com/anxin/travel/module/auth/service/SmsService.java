package com.anxin.travel.module.auth.service;

/**
 * 短信服务接口
 */
public interface SmsService {
    /**
     * 发送验证码
     * @param phone 手机号
     * @param code 验证码
     * @return 是否成功
     */
    boolean sendVerificationCode(String phone, String code);
}
