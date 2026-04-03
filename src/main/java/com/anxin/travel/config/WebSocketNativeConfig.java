package com.anxin.travel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
public class WebSocketNativeConfig {
    
    /**
     * 配置 WebSocket 容器，支持更大的消息和更长的超时
     */
    public ServletServerContainerFactoryBean servletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean factory = new ServletServerContainerFactoryBean();
        factory.setMaxTextMessageBufferSize(8192 * 10);
        factory.setMaxBinaryMessageBufferSize(8192 * 10);
        factory.setAsyncSendTimeout(60000L);
        return factory;
    }
}