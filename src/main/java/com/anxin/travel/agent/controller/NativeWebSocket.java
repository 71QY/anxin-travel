package com.anxin.travel.agent.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.anxin.travel.agent.service.AgentService;
import com.anxin.travel.agent.service.DialectTranslationService;
import com.anxin.travel.agent.service.MemoryService;
import com.anxin.travel.common.util.JwtUtil;
import com.anxin.travel.common.util.SpringContextUtil;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/ws/agent")
public class NativeWebSocket {
    
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<Session, Long> authenticatedUsers = new ConcurrentHashMap<>();
    // ⭐ 记录每个用户的最新 sessionId，用于防止重复连接
    private static final Map<Long, String> userLatestSession = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        log.debug("WebSocket 连接打开，sessionId: {}", session.getId());
        
        try {
            String token = extractTokenFromSession(session);
            log.debug("Token: {}", token != null ? token.substring(0, 20) + "..." : "null");
            
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
            
            // ⭐ 检查该用户是否已有活跃连接，如果有则拒绝新连接
            String latestSessionId = userLatestSession.get(userId);
            if (latestSessionId != null && !latestSessionId.equals(session.getId())) {
                log.warn("⚠️ 用户{}已有活跃连接（sessionId={}），拒绝新连接：sessionId={}", 
                    userId, latestSessionId, session.getId());
                JSONObject rejectMsg = new JSONObject();
                rejectMsg.put("type", "rejected");
                rejectMsg.put("success", false);
                rejectMsg.put("message", "检测到重复连接，请关闭其他窗口");
                rejectMsg.put("code", 409);
                rejectMsg.put("timestamp", System.currentTimeMillis());
                session.getBasicRemote().sendText(rejectMsg.toJSONString());
                session.close();
                return;
            }
            
            // 记录最新 sessionId
            userLatestSession.put(userId, session.getId());
            authenticatedUsers.put(session, userId);
            sessions.put(session.getId(), session);
            
            log.debug("WebSocket 认证成功，userId: {}", userId);
            
            // 发送欢迎消息
            JSONObject welcomeMsg = new JSONObject();
            welcomeMsg.put("type", "connected");
            welcomeMsg.put("success", true);
            welcomeMsg.put("message", "欢迎使用智能出行助手！");
            welcomeMsg.put("timestamp", System.currentTimeMillis());
            session.getBasicRemote().sendText(welcomeMsg.toJSONString());
            log.info("✅ 已发送欢迎消息：userId={}, sessionId={}", userId, session.getId());
            
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
            
            // 【新增】获取方言类型（由前端传递）
            String dialectType = json.getString("dialectType");
            if (dialectType == null || dialectType.isEmpty()) {
                dialectType = "mandarin";  // 默认为普通话
            }

            if (clientSessionId == null) {
                sendError(session, "消息格式错误");
                return;
            }
            
            // 【性能优化】心跳消息最早期返回，避免任何不必要的处理
            if ("ping".equals(type)) {
                log.debug("💓 收到心跳：sessionId={}", clientSessionId);
                JSONObject pongResponse = new JSONObject();
                pongResponse.put("type", "pong");
                pongResponse.put("sessionId", clientSessionId);
                pongResponse.put("timestamp", System.currentTimeMillis());
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(pongResponse.toJSONString());
                }
                return;  // 立即返回，不执行后续任何逻辑
            }
            
            log.info("收到消息：sessionId={}, userId={}, type={}, hasImage={}, dialect={}, lat={}, lng={}", 
                    clientSessionId, userId, type, imageBase64 != null, dialectType, lat, lng);
            
            AgentService agentService = SpringContextUtil.getBean(AgentService.class);
            Object result;
            
            // 【新增】方言翻译处理
            String processedContent = content;
            if (content != null && !content.trim().isEmpty() && !"mandarin".equalsIgnoreCase(dialectType)) {
                try {
                    DialectTranslationService dialectService = SpringContextUtil.getBean(DialectTranslationService.class);
                    processedContent = dialectService.translateToMandarin(content, dialectType);
                    log.info("✅ 方言翻译完成：原文=[{}] -> 译文=[{}]", content, processedContent);
                } catch (Exception e) {
                    log.error("❌ 方言翻译失败，使用原文：{}", e.getMessage());
                    processedContent = content;  // 翻译失败时使用原文
                }
            }
            
            // 根据消息类型分发（ping已在上面处理并返回）
            if ("image".equals(type) && imageBase64 != null && !imageBase64.isEmpty()) {
                // ⭐ 图片识别（支持批量）
                com.alibaba.fastjson.JSONArray additionalImages = json.getJSONArray("additionalImages");
                Integer imageCount = json.getInteger("imageCount");
                
                // 收集所有图片
                java.util.List<String> allImages = new java.util.ArrayList<>();
                allImages.add(imageBase64);  // 添加主图
                
                if (additionalImages != null && !additionalImages.isEmpty()) {
                    for (int i = 0; i < additionalImages.size(); i++) {
                        String img = additionalImages.getString(i);
                        if (img != null && !img.isEmpty()) {
                            allImages.add(img);
                        }
                    }
                }
                
                log.info("🖼️ 收到图片消息：sessionId={}, 图片数量={}, imageCount={}", 
                    clientSessionId, allImages.size(), imageCount);
                
                // ⭐ 批量图片统一处理（让 AI 理解多张图片的关系）
                if (allImages.size() == 1) {
                    // 单张图片，使用原有逻辑
                    result = agentService.processImage(clientSessionId, userId, allImages.get(0), lat, lng);
                } else {
                    // 多张图片，批量处理
                    result = agentService.processBatchImages(clientSessionId, userId, allImages, lat, lng);
                }
                
            } else if ("confirm".equals(type)) {
                // 用户确认选择（传递位置信息）
                result = agentService.confirmSelection(clientSessionId, userId, processedContent, lat, lng);
            } else if (processedContent != null && !processedContent.trim().isEmpty()) {
                // 普通文本消息（必须有内容，使用翻译后的内容）
                result = agentService.processIntention(clientSessionId, userId, processedContent, lat, lng);
            } else {
                // 无效消息（既不是 ping，也没有内容）
                log.warn("⚠️ 收到无效消息：type={}, content={}", type, content);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("type", "error");
                errorResponse.put("message", "无效的消息格式");
                errorResponse.put("sessionId", clientSessionId);
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(errorResponse.toJSONString());
                }
                return;
            }
            
            // 标准化响应格式，增加 type 字段
            JSONObject response = buildStandardResponse(result, clientSessionId, type);
            
            if (response != null && session.isOpen()) {
                String responseJson = response.toJSONString();
                log.info("✅ WebSocket 发送响应：sessionId={}, type={}, length={}", 
                    clientSessionId, response.getString("type"), responseJson.length());
                log.debug("响应内容：{}", responseJson);
                session.getBasicRemote().sendText(responseJson);
            } else {
                log.warn("⚠️ WebSocket 未发送响应：response={}, sessionOpen={}", 
                    response != null, session.isOpen());
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
        Long userId = authenticatedUsers.get(session);
        
        // ⭐ 清理 userLatestSession（只有当关闭的是最新连接时才清理）
        if (userId != null) {
            String latestSessionId = userLatestSession.get(userId);
            if (sessionId.equals(latestSessionId)) {
                userLatestSession.remove(userId);
                log.info("🔓 用户{}的最新连接已关闭：sessionId={}", userId, sessionId);
            }
        }
        
        // 安全移除会话数据（避免重复移除）
        sessions.remove(sessionId);
        authenticatedUsers.remove(session);
        
        try {
            AgentService agentService = SpringContextUtil.getBean(AgentService.class);
            if (agentService != null) {
                agentService.cleanupSession(sessionId);
            }
        } catch (Exception e) {
            log.debug("清理会话失败：{}", e.getMessage());
        }
        
        String reason = closeReason != null ? 
            closeReason.getReasonPhrase() + " (Code: " + closeReason.getCloseCode() + ")" : 
            "客户端主动断开";
        log.debug("WebSocket 连接关闭，sessionId: {}, 原因：{}", sessionId, reason);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        // 忽略常见的网络异常（这些是正常的连接断开）
        if (error instanceof java.io.EOFException || 
            error instanceof java.net.SocketException ||
            (error instanceof java.io.IOException && error.getMessage() != null && 
             (error.getMessage().contains("远程主机") || 
              error.getMessage().contains("existing connection") ||
              error.getMessage().toLowerCase().contains("connection reset") ||
              error.getMessage().toLowerCase().contains("broken pipe")))) {
            log.debug("WebSocket 网络异常（通常是客户端突然关闭）: sessionId: {}, 错误: {}", 
                session.getId(), error.getMessage());
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
        
        // 关键修复：处理 AgentResponse 类型
        if (result instanceof com.anxin.travel.agent.dto.AgentResponse) {
            com.anxin.travel.agent.dto.AgentResponse agentResponse = (com.anxin.travel.agent.dto.AgentResponse) result;
            
            // 设置 type（转为小写）
            String responseType = agentResponse.getType() != null ? agentResponse.getType().toLowerCase() : "chat_reply";
            response.put("type", responseType);
            response.put("success", agentResponse.getSuccess() != null ? agentResponse.getSuccess() : true);
            response.put("message", agentResponse.getMessage() != null ? agentResponse.getMessage() : "");
            
            // 根据类型构建不同的响应结构
            if ("image_result".equals(responseType)) {
                // ✅ 图片识别响应：包含 ocrText、places/order/message
                response.put("data", agentResponse.getData());
                
                // 如果 data 中有 places，也放到顶层方便前端访问
                if (agentResponse.getData() instanceof Map) {
                    Map<?, ?> dataMap = (Map<?, ?>) agentResponse.getData();
                    if (dataMap.containsKey("places")) {
                        response.put("places", dataMap.get("places"));
                    }
                    if (dataMap.containsKey("ocrText")) {
                        response.put("ocrText", dataMap.get("ocrText"));
                    }
                }
                
                log.info("✅ WebSocket 返回图片识别结果：type={}, hasPlaces={}", 
                    responseType, agentResponse.getPlaces() != null && !agentResponse.getPlaces().isEmpty());
                    
            } else if ("search".equals(responseType) && agentResponse.getPlaces() != null) {
                // 搜索响应：包含 POI 列表
                response.put("data", agentResponse.getPlaces());
                response.put("places", agentResponse.getPlaces());
                response.put("needConfirm", true);
                
            } else if ("order".equals(responseType) && agentResponse.getData() != null) {
                // 订单响应：包含订单数据
                response.put("data", agentResponse.getData());
                
            } else if ("chat".equals(responseType)) {
                // 聊天响应：仅消息
                response.put("content", agentResponse.getMessage());
            }
            
        } else if (result instanceof java.util.List) {
            // POI 列表（旧版兼容）
            response.put("type", "poi_list");
            response.put("data", result);
        } else if (result instanceof java.util.Map) {
            Map<?, ?> mapResult = (Map<?, ?>) result;
            // 检查是否是 confirm 返回的订单数据
            if ("ORDER".equals(mapResult.get("type"))) {
                // 确认选择成功，返回完整的订单数据
                response.put("type", "order");
                response.put("success", true);
                response.put("message", mapResult.get("message"));
                response.put("data", mapResult);  // 包含 poi 和 route
                log.info("✅ WebSocket 返回订单确认：{}", mapResult.get("message"));
            } else if ("ERROR".equals(mapResult.get("type"))) {
                // 确认失败，返回错误信息
                response.put("type", "error");
                response.put("success", false);
                response.put("message", mapResult.get("message"));
                log.warn("⚠️ WebSocket 确认失败：{}", mapResult.get("message"));
            } else if (mapResult.containsKey("orderNo") || mapResult.containsKey("id")) {
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
    
    /**
     * 【亲情守护】向指定用户推送WebSocket消息
     * @param userId 用户ID
     * @param message 消息内容（JSON字符串）
     */
    public void sendMessageToUser(Long userId, String message) {
        if (userId == null || message == null) {
            log.warn("⚠️ 推送消息失败：userId或message为null");
            return;
        }
        
        // 查找该用户的所有会话
        for (Map.Entry<Session, Long> entry : authenticatedUsers.entrySet()) {
            if (userId.equals(entry.getValue())) {
                Session session = entry.getKey();
                if (session != null && session.isOpen()) {
                    try {
                        session.getBasicRemote().sendText(message);
                        // log.debug("✅ WebSocket推送成功：userId={}, messageLength={}", userId, message.length());
                    } catch (Exception e) {
                        log.error("❌ WebSocket推送失败：userId={}", userId, e);
                    }
                }
            }
        }
    }
}