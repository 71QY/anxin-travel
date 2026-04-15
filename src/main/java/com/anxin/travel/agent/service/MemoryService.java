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
    private static final String PENDING_ORDER_PREFIX = "agent:pending_order:"; // 【新增】待确认订单前缀
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
        
        // 【优化】限制每个会话最多保存 30 条消息（15轮对话），防止内存泄漏
        // 保留足够的上下文用于多轮对话理解
        if (messages.size() >= 30) {
            messages.remove(0); // 移除最早的消息
            log.debug("会话消息超过 30 条，移除最早的一条以保持上下文窗口");
        }
        messages.add(message);
        log.debug("💾 保存消息到记忆：sessionId={}, role={}, content={}", sessionId, role, content.length() > 50 ? content.substring(0, 50) + "..." : content);
    }

    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> messages = sessionMessages.getOrDefault(sessionId, Collections.emptyList());
        return Collections.unmodifiableList(messages);
    }
    
    /**
     * 【新增】获取会话消息列表（用于 AI 对话上下文）
     * @param sessionId 会话 ID
     * @return 消息列表，如果不存在返回空列表
     */
    public List<ChatMessage> getMessages(String sessionId) {
        List<ChatMessage> messages = sessionMessages.get(sessionId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        // 返回不可变副本，防止外部修改
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }
    
    /**
     * 【新增】保存待确认订单信息（用于 CONFIRM -> ORDER 流程）
     * @param sessionId 会话 ID
     * @param confirmData 包含 selectedPoi 和 message 的 Map
     */
    public void savePendingOrder(String sessionId, Map<String, Object> confirmData) {
        try {
            String key = PENDING_ORDER_PREFIX + sessionId;
            redisUtil.set(key, JSON.toJSONString(confirmData), 1800); // 30分钟过期
            log.debug("✅ 保存待确认订单：sessionId={}, poi={}", sessionId, 
                confirmData.containsKey("selectedPoi") ? ((PoiDTO)confirmData.get("selectedPoi")).getName() : "unknown");
        } catch (Exception e) {
            log.warn("保存待确认订单失败：{}", e.getMessage());
        }
    }
    
    /**
     * 【新增】获取待确认订单信息
     * @param sessionId 会话 ID
     * @return 包含 selectedPoi 的 Map，如果不存在返回 null
     */
    public Map<String, Object> getPendingOrder(String sessionId) {
        try {
            String key = PENDING_ORDER_PREFIX + sessionId;
            String json = redisUtil.get(key);
            if (json != null && !json.isEmpty()) {
                log.debug("✅ 获取待确认订单：sessionId={}", sessionId);
                return JSON.parseObject(json, Map.class);
            }
        } catch (Exception e) {
            log.warn("获取待确认订单失败：{}", e.getMessage());
        }
        return null;
    }

    public void clearSession(String sessionId) {
        sessionMessages.remove(sessionId);
        sessionLocations.remove(sessionId);
        // 新增：清理 Redis 中的候选 POI
        try {
            String key = POI_PREFIX + sessionId;
            redisUtil.delete(key);  // 使用可变参数版本
            
            // 【新增】清理待确认订单
            String pendingKey = PENDING_ORDER_PREFIX + sessionId;
            redisUtil.delete(pendingKey);
            log.debug("✅ 清理会话数据：sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("清理 Redis 数据失败：{}", e.getMessage());
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