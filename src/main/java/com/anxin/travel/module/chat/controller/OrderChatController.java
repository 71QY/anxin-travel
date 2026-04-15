package com.anxin.travel.module.chat.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.chat.dto.ChatMessageRequest;
import com.anxin.travel.module.chat.service.OrderChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class OrderChatController {

    @Autowired
    private OrderChatService orderChatService;

    /**
     * API-C1: 获取订单群聊历史
     */
    @GetMapping("/order/{orderId}")
    public Result<?> getOrderChatHistory(@PathVariable Long orderId) {
        Long userId = UserContext.getUserId();
        log.info("💬 获取订单{}群聊历史，用户{}", orderId, userId);
        return orderChatService.getOrderChatHistory(orderId, userId);
    }

    /**
     * API-C2: 发送聊天消息
     */
    @PostMapping("/order/{orderId}")
    public Result<?> sendChatMessage(@PathVariable Long orderId, @RequestBody ChatMessageRequest request) {
        Long userId = UserContext.getUserId();
        log.info("💬 用户{}发送订单{}消息，类型: {}", userId, orderId, request.getMessageType());
        return orderChatService.sendChatMessage(orderId, userId, request);
    }

    /**
     * API-C3: 语音转文字
     */
    @PostMapping("/voiceToText")
    public Result<?> voiceToText(@RequestBody String audioBase64) {
        log.info("🎤 语音转文字");
        return orderChatService.voiceToText(audioBase64);
    }

    /**
     * API-C4: 文字转语音
     */
    @GetMapping("/textToSpeech")
    public Result<?> textToSpeech(@RequestParam String text) {
        log.info("🔊 文字转语音: {}", text);
        return orderChatService.textToSpeech(text);
    }
}
