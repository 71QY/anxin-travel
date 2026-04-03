package com.anxin.travel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置（用于并行路线计算等异步任务）
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    /**
     * 通用业务线程池
     */
    @Bean(name = "businessExecutor")
    public Executor businessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);           // 核心线程数
        executor.setMaxPoolSize(20);            // 最大线程数
        executor.setQueueCapacity(100);         // 队列容量
        executor.setKeepAliveSeconds(60);       // 空闲线程存活时间
        executor.setThreadNamePrefix("business-"); // 线程名前缀
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略
        executor.setWaitForTasksToCompleteOnShutdown(true); // 优雅关闭
        executor.setAwaitTerminationSeconds(60);
        
        log.info("✅ 业务线程池初始化完成：core=10, max=20, queue=100");
        return executor;
    }
}
