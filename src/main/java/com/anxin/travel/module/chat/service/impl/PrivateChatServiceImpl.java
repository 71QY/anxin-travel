package com.anxin.travel.module.chat.service.impl;

import com.anxin.travel.agent.controller.NativeWebSocket;
import com.anxin.travel.common.result.Result;
import com.anxin.travel.module.chat.dto.ChatMessageRequest;
import com.anxin.travel.module.chat.entity.PrivateChat;
import com.anxin.travel.module.chat.mapper.PrivateChatMapper;
import com.anxin.travel.module.chat.service.PrivateChatService;
import com.anxin.travel.module.guard.entity.FamilyGuard;
import com.anxin.travel.module.guard.mapper.FamilyGuardMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私聊服务实现
 */
@Slf4j
@Service
public class PrivateChatServiceImpl implements PrivateChatService {

    @Autowired
    private PrivateChatMapper privateChatMapper;
    
    @Autowired
    private FamilyGuardMapper familyGuardMapper;
    
    @Autowired
    private com.anxin.travel.module.user.mapper.UserMapper userMapper;
    
    @Autowired
    private NativeWebSocket nativeWebSocket;

    @Override
    public Result<?> getChatHistory(Long userId, Long targetUserId) {
        // 1. 校验是否有绑定关系（亲友或长辈）
        if (!hasGuardRelationship(userId, targetUserId)) {
            return Result.error("您与该用户没有绑定关系，无法查看聊天记录");
        }
        
        // 2. 查询聊天记录
        List<PrivateChat> messages = privateChatMapper.selectChatHistory(userId, targetUserId);
        
        log.info("✅ 查询私聊历史：userId={}, targetUserId={}, count={}", userId, targetUserId, messages.size());
        return Result.success(messages);
    }

    @Override
    public Result<?> sendPrivateMessage(Long senderId, Long receiverId, ChatMessageRequest request) {
        // 0. 参数校验
        if (request == null || request.getMessageType() == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.error("消息内容不能为空");
        }
        
        // 2. 校验是否有绑定关系
        if (!hasGuardRelationship(senderId, receiverId)) {
            return Result.error("您与该用户没有绑定关系，无法发送消息");
        }
        
        // 3. 插入消息
        PrivateChat chat = new PrivateChat();
        chat.setSenderId(senderId);
        chat.setReceiverId(receiverId);
        chat.setMessageType(request.getMessageType());
        chat.setContent(request.getContent());
        chat.setIsRead(0);  // 未读
        privateChatMapper.insert(chat);
        
        // 4. WebSocket推送给接收者
        Map<String, Object> pushData = new HashMap<>();
        pushData.put("type", "PRIVATE_MESSAGE");
        pushData.put("senderId", senderId);
        pushData.put("messageType", chat.getMessageType());
        pushData.put("content", chat.getContent());
        pushData.put("createdAt", chat.getCreatedAt());
        
        String jsonMessage = com.alibaba.fastjson.JSON.toJSONString(pushData);
        nativeWebSocket.sendMessageToUser(receiverId, jsonMessage);
        
        log.info("✅ 私聊消息发送成功：senderId={}, receiverId={}, messageType={}", 
            senderId, receiverId, request.getMessageType());
        return Result.success("发送成功");
    }

    @Override
    public Result<?> markAsRead(Long userId, Long senderId) {
        // 校验绑定关系
        if (!hasGuardRelationship(userId, senderId)) {
            return Result.error("无权操作");
        }
        
        int count = privateChatMapper.markAsRead(userId, senderId);
        log.info("✅ 标记消息已读：userId={}, senderId={}, count={}", userId, senderId, count);
        return Result.success(count);
    }

    @Override
    public Result<?> getUnreadCount(Long userId) {
        int count = privateChatMapper.countUnreadMessages(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("unreadCount", count);
        return Result.success(data);
    }
    
    /**
     * 校验两个用户是否有亲情守护绑定关系
     */
    private boolean hasGuardRelationship(Long userId1, Long userId2) {
        if (userId1 == null || userId2 == null) {
            return false;
        }
        
        // 检查 userId1 是否是 userId2 的亲友
        List<FamilyGuard> guards1 = familyGuardMapper.selectActiveGuardsByElderId(userId2);
        boolean isGuardian1 = guards1 != null && guards1.stream().anyMatch(g -> g.getGuardianId() != null && g.getGuardianId().equals(userId1));
        
        // 检查 userId2 是否是 userId1 的亲友
        List<FamilyGuard> guards2 = familyGuardMapper.selectActiveGuardsByElderId(userId1);
        boolean isGuardian2 = guards2 != null && guards2.stream().anyMatch(g -> g.getGuardianId() != null && g.getGuardianId().equals(userId2));
        
        return isGuardian1 || isGuardian2;
    }
}
