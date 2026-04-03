package com.anxin.travel.agent.config;

import com.anxin.travel.common.util.JwtUtil;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {
    
    private final JwtUtil jwtUtil;
    
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
    
    @Bean
    public ServerEndpointConfig.Configurator authenticatingConfigurator() {
        return new ServerEndpointConfig.Configurator() {
            @Override
            public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, jakarta.websocket.HandshakeResponse response) {
                log.info("=== WebSocket 握手开始 ===");
                log.info("Request URI: {}", request.getRequestURI());
                try {
                    String queryString = request.getRequestURI().getQuery();
                    log.info("Query String: {}", queryString);
                    if (queryString != null) {
                        for (String param : queryString.split("&")) {
                            String[] parts = param.split("=");
                            log.info("参数解析：{} = {}", parts[0], parts.length > 1 ? parts[1].substring(0, Math.min(20, parts[1].length())) + "..." : "null");
                            if (parts.length == 2 && "token".equals(parts[0])) {
                                String token = parts[1];
                                log.info("提取到 Token: {}", token);
                                try {
                                    boolean valid = jwtUtil.validateToken(token);
                                    log.info("Token 验证结果：{}", valid);
                                    if (valid) {
                                        Long userId = jwtUtil.getUserIdFromToken(token);
                                        log.info("获取到 userId: {}", userId);
                                        sec.getUserProperties().put("userId", userId);
                                        sec.getUserProperties().put("token", token);
                                        log.info("WebSocket 认证成功，userId: {}", userId);
                                        return;
                                    } else {
                                        log.error("Token 验证失败：validateToken 返回 false");
                                    }
                                } catch (Exception e) {
                                    log.error("Token 验证异常", e);
                                }
                            }
                        }
                    }
                    log.warn("WebSocket 认证失败 - 未找到 token 参数");
                } catch (Exception e) {
                    log.error("WebSocket 握手认证异常", e);
                }
            }
        };
    }
}
