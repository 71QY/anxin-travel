package com.anxin.travel.module.auth.service.impl;

import com.anxin.travel.module.auth.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("mockSmsService")
public class MockSmsServiceImpl implements SmsService {

    @Override
    public boolean sendVerificationCode(String phone, String code) {
        // 模拟模式：仅打印日志，不实际发送
        log.info("【模拟短信】验证码已生成，手机号：{}, 验证码：{}", phone, code);
        log.warn("【开发环境】未实际发送短信，请从日志中查看验证码");
        return true;
    }
}
