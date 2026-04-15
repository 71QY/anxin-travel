package com.anxin.travel.module.chat.service;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.module.chat.dto.ChatMessageRequest;

/**
 * 订单群聊服务接口
 */
public interface OrderChatService {
    
    /**
     * 获取订单群聊历史
     */
    Result<?> getOrderChatHistory(Long orderId, Long userId);
    
    /**
     * 发送聊天消息
     */
    Result<?> sendChatMessage(Long orderId, Long userId, ChatMessageRequest request);
    
    /**
     * 语音转文字
     */
    Result<?> voiceToText(String audioBase64);
    
    /**
     * 文字转语音
     */
    Result<?> textToSpeech(String text);
}
