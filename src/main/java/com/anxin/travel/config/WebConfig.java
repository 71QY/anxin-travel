package com.anxin.travel.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final GuardModeInterceptor guardModeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 认证拦截器:校验Token有效性
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/auth/**",           // 认证相关接口
                    "/api/map/poi/detail",    // POI 详情（点击地点时调用，不需要认证）
                    "/api/map/route"          // 路线规划（不需要认证）
                );
        
        // 2. 长辈模式权限拦截器:白名单控制(必须在auth之后执行)
        registry.addInterceptor(guardModeInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}