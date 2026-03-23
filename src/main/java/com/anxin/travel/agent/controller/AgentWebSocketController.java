package com.anxin.travel.agent.controller;

import com.anxin.travel.agent.service.AgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MessageMapping("/agent/chat")
    public void handleChatMessage(@Payload String payload, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("未认证的用户消息");
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.has("type") ? root.get("type").asText() : "user_message";
            String sessionId = root.has("sessionId") ? root.get("sessionId").asText() : null;
            String content = root.has("content") ? root.get("content").asText() : "";
            if (sessionId == null) {
                log.warn("消息缺少 sessionId");
                return;
            }
            log.info("收到消息: sessionId={}, type={}, content={}", sessionId, type, content);

            if ("user_message".equals(type)) {
                agentService.processIntention(sessionId, userId, content, "user_message");
            } else if ("confirm".equals(type)) {
                agentService.processIntention(sessionId, userId, content, type);
            } else {
                log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("解析消息失败", e);
        }
    }
}