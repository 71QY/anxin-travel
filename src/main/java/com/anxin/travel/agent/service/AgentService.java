package com.anxin.travel.agent.service;

import com.anxin.travel.agent.model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ToolExecutorService toolExecutor;
    private final MemoryService memoryService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String model;

    // 原有的 STOMP 消息处理方法
    public void processMessage(String sessionId, Long userId, String content) {
        memoryService.saveMessage(sessionId, "user", content);
        List<ChatMessage> history = memoryService.getHistory(sessionId);

        // 调用通义千问 API
        String aiResponse = callQwen(history);

        // 简单工具调用模拟（可根据需要扩展）
        if (content.contains("路线") || content.contains("怎么走")) {
            Map<String, Object> params = new HashMap<>();
            params.put("origin", "当前地点");
            params.put("destination", "目的地");
            Map<String, Object> route = toolExecutor.executeTool("query_route", params);
            aiResponse = "为您找到路线：" + route;
        } else if (content.contains("天气")) {
            Map<String, Object> params = new HashMap<>();
            params.put("city", "杭州");
            Map<String, Object> weather = toolExecutor.executeTool("query_weather", params);
            aiResponse = "天气信息：" + weather;
        }

        memoryService.saveMessage(sessionId, "assistant", aiResponse);
        sendAgentMessage(sessionId, aiResponse);
    }

    /**
     * 新增方法：为原生 WebSocket 提供的同步处理方法，直接返回回复字符串
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param content 用户输入内容
     * @return 智能体回复
     */
    public String processNativeMessage(String sessionId, Long userId, String content) {
        memoryService.saveMessage(sessionId, "user", content);
        List<ChatMessage> history = memoryService.getHistory(sessionId);

        // 调用通义千问 API
        String aiResponse = callQwen(history);

        // 简单工具调用模拟（可根据需要扩展）
        if (content.contains("路线") || content.contains("怎么走")) {
            Map<String, Object> params = new HashMap<>();
            params.put("origin", "当前地点");
            params.put("destination", "目的地");
            Map<String, Object> route = toolExecutor.executeTool("query_route", params);
            aiResponse = "为您找到路线：" + route;
        } else if (content.contains("天气")) {
            Map<String, Object> params = new HashMap<>();
            params.put("city", "杭州");
            Map<String, Object> weather = toolExecutor.executeTool("query_weather", params);
            aiResponse = "天气信息：" + weather;
        }

        memoryService.saveMessage(sessionId, "assistant", aiResponse);
        return aiResponse;  // 直接返回结果，不通过 STOMP 发送
    }

    private String callQwen(List<ChatMessage> history) {
        String url = baseUrl + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 构建消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            Map<String, String> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            messages.add(map);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("调用通义千问失败", e);
            return "抱歉，我现在无法回答，请稍后再试。";
        }
    }

    private void sendAgentMessage(String sessionId, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "agent_message");
        message.put("sessionId", sessionId);
        message.put("content", content);
        messagingTemplate.convertAndSend("/queue/reply-" + sessionId, message);
    }
}