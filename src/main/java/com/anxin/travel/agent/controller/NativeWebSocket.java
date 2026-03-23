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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/native")
public class NativeWebSocket {

    private static final Logger log = LoggerFactory.getLogger(NativeWebSocket.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 存储所有在线会话（WebSocket sessionId -> Session）
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    // 存储 Android 客户端的 sessionId 与 WebSocket sessionId 的映射
    private static final Map<String, String> clientSessionMap = new ConcurrentHashMap<>();

    private JwtUtil jwtUtil;
    private AgentService agentService;

    /**
     * 静态方法，向指定 Android 客户端发送消息
     */
    public static void sendToSession(String clientSessionId, String message) {
        String wsSessionId = clientSessionMap.get(clientSessionId);
        if (wsSessionId == null) {
            log.warn("找不到 WebSocket session, clientSessionId={}", clientSessionId);
            return;
        }
        Session session = sessions.get(wsSessionId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                log.info("发送消息到 clientSession {}: {}", clientSessionId, message);
            } catch (IOException e) {
                log.error("发送消息失败", e);
            }
        } else {
            log.warn("WebSocket session 不存在或已关闭: {}", wsSessionId);
        }
    }

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

    /**
     * 接收文本消息（Android 发送的是文本帧）
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到文本消息: {}", message);

        Long userId = (Long) session.getUserProperties().get("userId");

        try {
            JsonNode root = objectMapper.readTree(message);

            String type = root.path("type").asText();
            String clientSessionId = root.path("sessionId").asText();
            String content = root.path("content").asText();

            // 建立 Android sessionId -> WebSocket sessionId 映射
            clientSessionMap.put(clientSessionId, session.getId());

            log.info("调用AgentService，clientSessionId: {}, content: {}", clientSessionId, content);

            if (agentService == null) {
                agentService = SpringContextUtil.getBean(AgentService.class);
            }

            agentService.processIntention(clientSessionId, userId, content, type);

        } catch (Exception e) {
            log.error("处理消息异常", e);
            sendError(session, "服务器内部错误");
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        // 清理映射关系（需要找到对应的 clientSessionId，简单做法是遍历，但此处简化）
        clientSessionMap.values().remove(sessionId);
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