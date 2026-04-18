package com.anxin.travel.module.chat.service.impl;

import com.anxin.travel.agent.controller.NativeWebSocket;
import com.anxin.travel.agent.service.VoiceService;
import com.anxin.travel.common.result.Result;
import com.anxin.travel.module.chat.dto.ChatMessageRequest;
import com.anxin.travel.module.chat.entity.OrderChat;
import com.anxin.travel.module.chat.mapper.OrderChatMapper;
import com.anxin.travel.module.chat.service.OrderChatService;
import com.anxin.travel.module.guard.entity.FamilyGuard;
import com.anxin.travel.module.guard.mapper.FamilyGuardMapper;
import com.anxin.travel.module.order.entity.OrderInfo;
import com.anxin.travel.module.order.mapper.OrderMapper;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class OrderChatServiceImpl implements OrderChatService {

    @Autowired
    private OrderChatMapper orderChatMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private FamilyGuardMapper familyGuardMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NativeWebSocket nativeWebSocket;
    @Autowired
    private VoiceService voiceService;

    @Override
    public Result<?> getOrderChatHistory(Long orderId, Long userId) {
        // 1. 查询订单
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }

        // 2. 校验是否是订单参与者
        if (!isOrderParticipant(orderId, userId)) {
            return Result.error("您不是该订单参与者");
        }

        // 3. 查询聊天记录
        List<OrderChat> messages = orderChatMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderChat>()
                .eq("order_id", orderId)
                .orderByAsc("created_at")
        );

        return Result.success(messages);
    }

    @Override
    public Result<?> sendChatMessage(Long orderId, Long userId, ChatMessageRequest request) {
        // 1. 查询订单状态
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }

        // 强制校验订单状态：只有已确认或进行中的订单才能发消息
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            return Result.error("订单未确认或已结束，无法发送消息");
        }

        // 2. 校验是否是订单参与者
        if (!isOrderParticipant(orderId, userId)) {
            return Result.error("您不是该订单参与者");
        }

        // 3. 判断发送者类型
        Integer senderType = getSenderType(orderId, userId);

        // 5. 插入消息
        OrderChat chat = new OrderChat();
        chat.setOrderId(orderId);
        chat.setSenderId(userId);
        chat.setSenderType(senderType);
        chat.setMessageType(request.getMessageType());
        chat.setContent(request.getContent());
        orderChatMapper.insert(chat);

        // 6. WebSocket推送给其他参与者
        pushToOrderParticipants(orderId, chat);

        log.info("✅ 订单{}群聊消息发送成功，发送者类型: {}, 消息类型: {}", orderId, senderType, request.getMessageType());
        return Result.success("发送成功");
    }

    @Override
    public Result<?> voiceToText(String audioBase64) {
        try {
            // 调用通义千问语音识别 API
            Map<String, Object> data = voiceService.voiceToText(audioBase64, "mandarin");
            return Result.success(data);
        } catch (Exception e) {
            log.error("❌ 语音转文字失败", e);
            return Result.error("语音识别失败：" + e.getMessage());
        }
    }

    @Override
    public Result<?> textToSpeech(String text) {
        try {
            // 调用通义千问 TTS API
            Map<String, Object> data = voiceService.textToSpeech(text, null, 50, 50);
            return Result.success(data);
        } catch (Exception e) {
            log.error("❌ 文字转语音失败", e);
            return Result.error("文字转语音失败：" + e.getMessage());
        }
    }

    /**
     * 判断用户是否是订单参与者
     */
    private boolean isOrderParticipant(Long orderId, Long userId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) return false;

        // 长辈（乘车人）
        if (order.getUserId().equals(userId)) return true;
        
        // 代叫亲友
        if (order.getProxyUserId() != null && order.getProxyUserId().equals(userId)) return true;
        
        // 司机
        if (order.getDriverId() != null && order.getDriverId().equals(userId)) return true;

        return false;
    }

    /**
     * 获取发送者类型
     */
    private Integer getSenderType(Long orderId, Long userId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) return 0;

        // 1长辈
        if (order.getUserId().equals(userId)) return 1;
        
        // 2亲友
        if (order.getProxyUserId() != null && order.getProxyUserId().equals(userId)) return 2;
        
        // 3司机
        if (order.getDriverId() != null && order.getDriverId().equals(userId)) return 3;

        return 0;
    }

    /**
     * 推送消息给订单所有参与者
     */
    private void pushToOrderParticipants(Long orderId, OrderChat chat) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) return;

        Map<String, Object> pushData = new HashMap<>();
        pushData.put("type", "CHAT_MESSAGE");
        pushData.put("orderId", orderId);
        pushData.put("senderId", chat.getSenderId());
        pushData.put("senderType", chat.getSenderType());
        pushData.put("messageType", chat.getMessageType());
        pushData.put("content", chat.getContent());
        pushData.put("createdAt", chat.getCreatedAt());

        String jsonMessage = com.alibaba.fastjson.JSON.toJSONString(pushData);

        // 推送给长辈
        if (order.getUserId() != null && !order.getUserId().equals(chat.getSenderId())) {
            nativeWebSocket.sendMessageToUser(order.getUserId(), jsonMessage);
        }

        // 推送给代叫亲友
        if (order.getProxyUserId() != null && !order.getProxyUserId().equals(chat.getSenderId())) {
            nativeWebSocket.sendMessageToUser(order.getProxyUserId(), jsonMessage);
        }

        // 推送给司机
        if (order.getDriverId() != null && !order.getDriverId().equals(chat.getSenderId())) {
            nativeWebSocket.sendMessageToUser(order.getDriverId(), jsonMessage);
        }
    }
}
