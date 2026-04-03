package com.anxin.travel.agent.service;

import com.alibaba.fastjson.JSON;
import com.anxin.travel.agent.model.ChatMessage;
import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.map.dto.PoiDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final String POI_PREFIX = "agent:poi:";
    private static final long CANDIDATES_EXPIRE_SECONDS = 1800; // 30 分钟
    
    private final RedisUtil redisUtil;

    private final Map<String, List<ChatMessage>> sessionMessages = new ConcurrentHashMap<>();
    private final Map<String, double[]> sessionLocations = new ConcurrentHashMap<>();
    // 修复：移除内存中的 candidates，改用 Redis 缓存
    // private final Map<String, List<PoiDTO>> sessionCandidates = new ConcurrentHashMap<>();

    public void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(new Date());

        List<ChatMessage> messages = sessionMessages
                .computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        
        // 新增：限制每个会话最多保存 20 条消息，防止内存泄漏
        if (messages.size() >= 20) {
            messages.remove(0); // 移除最早的消息
        }
        messages.add(message);
    }

    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> messages = sessionMessages.getOrDefault(sessionId, Collections.emptyList());
        return Collections.unmodifiableList(messages);
    }

    public void clearSession(String sessionId) {
        sessionMessages.remove(sessionId);
        sessionLocations.remove(sessionId);
        // 新增：清理 Redis 中的候选 POI
        try {
            String key = POI_PREFIX + sessionId;
            redisUtil.delete(key);  // 使用可变参数版本
        } catch (Exception e) {
            log.warn("清理 Redis 候选 POI 失败：{}", e.getMessage());
        }
    }

    public void saveLocation(String sessionId, Double lat, Double lng) {
        if (lat != null && lng != null) {
            sessionLocations.put(sessionId, new double[]{lat, lng});
        }
    }

    public double[] getLocation(String sessionId) {
        return sessionLocations.get(sessionId);
    }

    /**
     * 保存单个 POI（用于地图点击场景）
     */
    public void saveSinglePoi(String sessionId, PoiDTO poi) {
        try {
            String key = POI_PREFIX + sessionId;
            List<PoiDTO> singleList = new ArrayList<>();
            singleList.add(poi);
            redisUtil.set(key, JSON.toJSONString(singleList), 1800); // 30 分钟
            log.debug("已保存单个 POI 到 Redis: key={}, name={}", key, poi.getName());
        } catch (Exception e) {
            log.warn("保存单个 POI 失败：{}", e.getMessage());
        }
    }

    /**
     * 获取缓存的 POI 列表（优先从 Redis 获取）
     */
    public List<PoiDTO> getCandidates(String sessionId) {
        try {
            String key = POI_PREFIX + sessionId;
            String json = redisUtil.get(key);
            if (json != null && !json.isEmpty()) {
                log.debug("从 Redis 获取候选 POI：key={}, size={}", key, JSON.parseArray(json).size());
                return JSON.parseArray(json, PoiDTO.class);
            }
        } catch (Exception e) {
            log.warn("获取候选 POI 失败：{}", e.getMessage());
        }
        return null;
    }

    public void saveCandidates(String sessionId, List<PoiDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        
        try {
            String key = POI_PREFIX + sessionId;
            redisUtil.set(key, JSON.toJSONString(candidates), CANDIDATES_EXPIRE_SECONDS);
            log.debug("保存候选 POI 到 Redis: key={}, size={}, expire={}s", key, candidates.size(), CANDIDATES_EXPIRE_SECONDS);
        } catch (Exception e) {
            log.warn("保存候选 POI 失败：{}", e.getMessage());
        }
    }
}