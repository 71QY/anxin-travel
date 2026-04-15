package com.anxin.travel.module.chat.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.chat.dto.ChatMessageRequest;
import com.anxin.travel.module.chat.service.PrivateChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 私聊控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/private")
public class PrivateChatController {

    @Autowired
    private PrivateChatService privateChatService;

    /**
     * API-P1: 获取私聊历史记录
     * @param targetUserId 对方用户ID（亲友或长辈）
     */
    @GetMapping("/history/{targetUserId}")
    public Result<?> getChatHistory(@PathVariable Long targetUserId) {
        Long userId = UserContext.getUserId();
        log.info("💬 查询私聊历史：userId={}, targetUserId={}", userId, targetUserId);
        return privateChatService.getChatHistory(userId, targetUserId);
    }

    /**
     * API-P2: 发送私聊消息
     * @param receiverId 接收者用户ID
     */
    @PostMapping("/send/{receiverId}")
    public Result<?> sendPrivateMessage(@PathVariable Long receiverId, @RequestBody ChatMessageRequest request) {
        Long senderId = UserContext.getUserId();
        log.info("💬 发送私聊消息：senderId={}, receiverId={}, messageType={}", senderId, receiverId, request.getMessageType());
        return privateChatService.sendPrivateMessage(senderId, receiverId, request);
    }

    /**
     * API-P3: 标记消息为已读
     * @param senderId 发送者用户ID
     */
    @PostMapping("/read/{senderId}")
    public Result<?> markAsRead(@PathVariable Long senderId) {
        Long userId = UserContext.getUserId();
        log.info("✅ 标记消息已读：userId={}, senderId={}", userId, senderId);
        return privateChatService.markAsRead(userId, senderId);
    }

    /**
     * API-P4: 查询未读消息数量
     */
    @GetMapping("/unread")
    public Result<?> getUnreadCount() {
        Long userId = UserContext.getUserId();
        log.info("🔔 查询未读消息：userId={}", userId);
        return privateChatService.getUnreadCount(userId);
    }
}
