package com.anxin.travel.agent.controller;

import com.anxin.travel.agent.model.UserMessage;
import com.anxin.travel.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AgentWebSocketController {

    private final AgentService agentService;

    @MessageMapping("/agent/chat")
    public void handleChatMessage(@Payload UserMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("未认证的用户消息");
            return;
        }
        log.info("收到用户消息: sessionId={}, content={}", message.getSessionId(), message.getContent());
        agentService.processMessage(message.getSessionId(), userId, message.getContent());
    }
}