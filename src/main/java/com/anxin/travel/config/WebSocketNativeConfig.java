package com.anxin.travel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketNativeConfig {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        System.out.println("====== ServerEndpointExporter 已注册 ======");
        return new ServerEndpointExporter();
    }
}

