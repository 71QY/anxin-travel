package com.anxin.travel.agent.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.anxin.travel.agent.service.AgentService;
import com.anxin.travel.common.util.JwtUtil;
import com.anxin.travel.common.util.SpringContextUtil;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/ws/agent")
public class NativeWebSocket {
    
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<Session, Long> authenticatedUsers = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        log.info("WebSocket 连接打开，sessionId: {}", session.getId());
        
        try {
            String token = extractTokenFromSession(session);
            log.info("Token: {}", token != null ? token.substring(0, 20) + "..." : "null");
            
            JwtUtil jwtUtil = SpringContextUtil.getBean(JwtUtil.class);
            boolean valid = jwtUtil.validateToken(token);
            
            if (!valid) {
                log.warn("WebSocket 认证失败，token 已过期或无效");
                JSONObject errorMsg = new JSONObject();
                errorMsg.put("type", "auth_failed");
                errorMsg.put("success", false);
                errorMsg.put("message", "认证已过期，请重新登录");
                errorMsg.put("code", 401);
                errorMsg.put("timestamp", System.currentTimeMillis());
                session.getBasicRemote().sendText(errorMsg.toJSONString());
                return;
            }
            
            Long userId = jwtUtil.getUserIdFromToken(token);
            authenticatedUsers.put(session, userId);
            sessions.put(session.getId(), session);
            
            log.info("WebSocket 认证成功，userId: {}", userId);
            
            JSONObject welcomeMsg = new JSONObject();
            welcomeMsg.put("message", "欢迎使用智能出行助手！");
            session.getBasicRemote().sendText(welcomeMsg.toJSONString());
            
        } catch (Exception e) {
            log.error("WebSocket 握手失败", e);
            try {
                sendError(session, "请先登录");
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
            }
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        Long userId = authenticatedUsers.get(session);
        if (userId == null) {
            log.error("未认证的用户消息");
            try {
                sendError(session, "请先登录");
            } catch (Exception e) {
                log.error("发送错误消息失败", e);
            }
            return;
        }

        try {
            JSONObject json = JSON.parseObject(message);
            String clientSessionId = json.getString("sessionId");
            String content = json.getString("content");
            
            if (content == null || content.isEmpty()) {
                content = json.getString("message");
            }
            
            Double lat = json.getDouble("lat");
            Double lng = json.getDouble("lng");
            String type = json.getString("type");
            String imageBase64 = json.getString("imageBase64");

            if (clientSessionId == null) {
                sendError(session, "消息格式错误");
                return;
            }

            log.info("收到消息：sessionId={}, userId={}, type={}, hasImage={}, lat={}, lng={}", 
                    clientSessionId, userId, type, imageBase64 != null, lat, lng);
            
            AgentService agentService = SpringContextUtil.getBean(AgentService.class);
            Object result;
            
            // 根据消息类型分发
            if ("image".equals(type) && imageBase64 != null && !imageBase64.isEmpty()) {
                // 图片识别
                result = agentService.processImage(clientSessionId, userId, imageBase64, lat, lng);
            } else if ("confirm".equals(type)) {
                // 用户确认选择
                result = agentService.confirmSelection(clientSessionId, userId, content);
            } else {
                // 普通文本消息
                result = agentService.processIntention(clientSessionId, userId, content != null ? content : "", lat, lng);
            }
            
            // 标准化响应格式，增加 type 字段
            JSONObject response = buildStandardResponse(result, clientSessionId, type);
            
            if (response != null && session.isOpen()) {
                session.getBasicRemote().sendText(response.toJSONString());
            }

        } catch (Exception e) {
            log.error("处理消息失败", e);
            try {
                if (session.isOpen()) {
                    sendError(session, "消息处理失败");
                }
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
            }
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        String sessionId = session.getId();
        
        // 安全移除会话数据（避免重复移除）
        sessions.remove(sessionId);
        authenticatedUsers.remove(session);
        
        try {
            AgentService agentService = SpringContextUtil.getBean(AgentService.class);
            if (agentService != null) {
                agentService.cleanupSession(sessionId);
            }
        } catch (Exception e) {
            log.warn("清理会话失败：{}", e.getMessage());
        }
        
        String reason = closeReason != null ? 
            closeReason.getReasonPhrase() + " (Code: " + closeReason.getCloseCode() + ")" : 
            "客户端主动断开";
        log.info("WebSocket 连接关闭，sessionId: {}, 原因：{}", sessionId, reason);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        // 忽略常见的网络异常（这些是正常的连接断开）
        if (error instanceof java.io.EOFException || 
            (error instanceof java.io.IOException && error.getMessage() != null && 
             (error.getMessage().contains("远程主机") || 
              error.getMessage().contains("existing connection") ||
              error.getMessage().contains("connection reset")))) {
            log.debug("WebSocket 网络异常（通常是客户端突然关闭）: sessionId: {}", session.getId());
        } else {
            // 其他错误才记录详细日志
            log.error("WebSocket 错误，sessionId: {}, 错误类型：{}, 错误信息：{}", 
                session.getId(), 
                error.getClass().getSimpleName(), 
                error.getMessage());
            log.debug("错误堆栈：", error);
        }
    }
    
    private void sendError(Session session, String errorMsg) throws Exception {
        JSONObject errorJson = new JSONObject();
        errorJson.put("type", "error");
        errorJson.put("success", false);
        errorJson.put("message", errorMsg);
        errorJson.put("code", 500);
        errorJson.put("timestamp", System.currentTimeMillis());
        session.getBasicRemote().sendText(errorJson.toJSONString());
    }
    
    private JSONObject buildStandardResponse(Object result, String sessionId, String requestType) {
        JSONObject response = new JSONObject();
        response.put("sessionId", sessionId);
        response.put("timestamp", System.currentTimeMillis());
        
        if (result instanceof java.util.List) {
            // POI 列表
            response.put("type", "poi_list");
            response.put("data", result);
        } else if (result instanceof java.util.Map) {
            Map<?, ?> mapResult = (Map<?, ?>) result;
            if (mapResult.containsKey("orderNo") || mapResult.containsKey("id")) {
                // 订单创建
                response.put("type", "order_created");
                response.put("data", mapResult);
            } else if (mapResult.containsKey("message")) {
                // 聊天回复
                response.put("type", "chat_reply");
                response.put("message", mapResult.get("message"));
            } else {
                response.put("type", "chat_reply");
                response.put("message", "处理完成");
            }
        } else {
            response.put("type", "chat_reply");
            response.put("message", "处理完成");
        }
        
        return response;
    }
    
    private String extractTokenFromSession(Session session) {
        try {
            java.net.URI uri = session.getRequestURI();
            if (uri == null) {
                return null;
            }
            String queryString = uri.getQuery();
            if (queryString != null) {
                for (String param : queryString.split("&")) {
                    String[] parts = param.split("=");
                    if (parts.length == 2 && "token".equals(parts[0])) {
                        return parts[1];
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析 URL 参数失败", e);
        }
        return null;
    }
}