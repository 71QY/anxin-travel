package com.anxin.travel.agent.config;

import com.anxin.travel.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            
            // 支持两种 token 传递方式：URL 参数 或 Header
            String token = servletRequest.getParameter("token");
            if (token == null || token.isEmpty()) {
                token = servletRequest.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }
            }
            
            log.info("WebSocket 握手，token: {}", token != null ? "已提供" : "为空");
            
            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                log.info("验证成功，userId: {}", userId);
                attributes.put("userId", userId);
                attributes.put("token", token);
                return true;
            } else {
                log.warn("验证失败，token 无效或为空");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }
        log.warn("不是 ServletServerHttpRequest，拒绝连接");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}