package com.anxin.travel.agent.service;

import com.anxin.travel.agent.model.ChatMessage;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemoryService {

    private final Map<String, List<ChatMessage>> sessionMessages = new ConcurrentHashMap<>();

    public void saveMessage(String sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(new Date());
        sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return sessionMessages.getOrDefault(sessionId, new ArrayList<>());
    }

    public void clearSession(String sessionId) {
        sessionMessages.remove(sessionId);
    }
}