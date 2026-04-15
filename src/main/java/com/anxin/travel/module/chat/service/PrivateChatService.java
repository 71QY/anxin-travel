package com.anxin.travel.module.chat.service;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.module.chat.dto.ChatMessageRequest;

/**
 * 私聊服务接口
 */
public interface PrivateChatService {
    
    /**
     * 获取私聊历史记录
     */
    Result<?> getChatHistory(Long userId, Long targetUserId);
    
    /**
     * 发送私聊消息
     */
    Result<?> sendPrivateMessage(Long senderId, Long receiverId, ChatMessageRequest request);
    
    /**
     * 标记消息为已读
     */
    Result<?> markAsRead(Long userId, Long senderId);
    
    /**
     * 查询未读消息数量
     */
    Result<?> getUnreadCount(Long userId);
}
