package com.anxin.travel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
public class WebSocketNativeConfig {
    
    /**
     * 配置 WebSocket 容器，支持更大的消息和更长的超时
     */
    @Bean
    public ServletServerContainerFactoryBean servletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean factory = new ServletServerContainerFactoryBean();
        // 设置最大文本消息缓冲区为 10MB（支持大图片 Base64）
        factory.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        // 设置最大二进制消息缓冲区为 10MB
        factory.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        // 设置异步发送超时时间为 60 秒
        factory.setAsyncSendTimeout(60000L);
        return factory;
    }
}