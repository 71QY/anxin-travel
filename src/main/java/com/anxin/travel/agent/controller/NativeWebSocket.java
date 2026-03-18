package com.anxin.travel.agent.controller;

import com.anxin.travel.agent.service.AgentService;
import com.anxin.travel.common.util.JwtUtil;
import com.anxin.travel.common.util.SpringContextUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/native")
public class NativeWebSocket {

    private static final Logger log = LoggerFactory.getLogger(NativeWebSocket.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private JwtUtil jwtUtil;
    private AgentService agentService;

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        String queryString = session.getQueryString();
        String token = null;
        if (queryString != null && queryString.contains("token=")) {
            String[] params = queryString.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    token = param.substring(6);
                    break;
                }
            }
        }

        if (token == null || !getJwtUtil().validateToken(token)) {
            log.warn("无效的 token，关闭连接");
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "无效的 token"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
            return;
        }

        Long userId = getJwtUtil().getUserIdFromToken(token);
        session.getUserProperties().put("userId", userId);
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket 连接打开，sessionId: {}, userId: {}", sessionId, userId);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        Long userId = (Long) session.getUserProperties().get("userId");
        log.info("收到消息，userId: {}, 消息内容: {}", userId, message);
        try {
            JsonNode root = objectMapper.readTree(message);
            log.info("解析JSON成功，root: {}", root);
            String type = root.path("type").asText();
            if (!"user_message".equals(type)) {
                log.warn("消息类型错误: {}", type);
                sendError(session, "消息类型不支持");
                return;
            }
            String sessionId = root.path("sessionId").asText();
            String content = root.path("content").asText();
            log.info("调用AgentService，sessionId: {}, content: {}", sessionId, content);

            String reply = getAgentService().processNativeMessage(sessionId, userId, content);
            log.info("AgentService返回: {}", reply);

            String replyJson = objectMapper.writeValueAsString(Map.of(
                    "type", "agent_message",
                    "sessionId", sessionId,
                    "content", reply
            ));
            log.info("发送回复: {}", replyJson);
            session.getBasicRemote().sendText(replyJson);

        } catch (Exception e) {
            log.error("处理消息异常", e);
            sendError(session, "服务器内部错误");
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("WebSocket 连接关闭，sessionId: {}, 原因: {}", sessionId, reason.getReasonPhrase());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 错误，sessionId: {}", session.getId(), error);
    }

    private void sendError(Session session, String errorMsg) {
        try {
            String errorJson = objectMapper.writeValueAsString(Map.of("type", "error", "content", errorMsg));
            session.getBasicRemote().sendText(errorJson);
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }

    private JwtUtil getJwtUtil() {
        if (jwtUtil == null) {
            jwtUtil = SpringContextUtil.getBean(JwtUtil.class);
        }
        return jwtUtil;
    }

    private AgentService getAgentService() {
        if (agentService == null) {
            agentService = SpringContextUtil.getBean(AgentService.class);
        }
        return agentService;
    }
}