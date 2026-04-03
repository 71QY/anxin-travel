package com.anxin.travel.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 性能监控切面（记录方法执行时间）
 */
@Slf4j
@Aspect
@Component
public class PerformanceMonitorAspect {

    /**
     * 环绕通知：记录方法执行耗时
     */
    @Around("execution(* com.anxin.travel.agent.service..*(..)) || " +
            "execution(* com.anxin.travel.module.map.client..*(..))")
    public Object recordExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 超过 1 秒的方法记录警告日志
            if (duration > 1000) {
                log.warn("⚠️ 慢方法：{}.{}() 耗时 {}ms", 
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    duration);
            } else {
                log.debug("✅ 方法：{}.{}() 耗时 {}ms", 
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    duration);
            }
            
            return result;
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            log.error("❌ 方法：{}.{}() 执行失败，耗时 {}ms, 错误：{}", 
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                duration,
                e.getMessage());
            
            throw e;
        }
    }
}
