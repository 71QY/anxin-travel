package com.anxin.travel.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    public void set(String key, String value, long expireSeconds) {
        redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 新增：单独设置过期时间
    public boolean expire(String key, long seconds) {
        return redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    // 新增：删除多个 key（可变参数）
    public void delete(String... keys) {
        if (keys != null && keys.length > 0) {
            redisTemplate.delete(java.util.Arrays.asList(keys));
        }
    }
    
    // 保留原有：删除集合
    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }
    
    // 废弃：删除单个 key（已统一到 delete 方法）
    @Deprecated
    public void deleteSingle(String key) {
        redisTemplate.delete(key);
    }

    // 新增：判断 key 是否存在
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
    
    // 新增：获取所有匹配的 key（支持通配符）
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    // 新增：原子递增（用于计数器）
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }
}