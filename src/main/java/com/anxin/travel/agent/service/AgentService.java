package com.anxin.travel.agent.service;

import com.anxin.travel.agent.controller.NativeWebSocket;
import com.anxin.travel.agent.model.AgentState;
import com.anxin.travel.agent.model.CandidateDestination;
import com.anxin.travel.agent.model.ChatMessage;
import com.anxin.travel.module.map.client.AmapClient;
import com.anxin.travel.module.map.dto.RouteResult;
import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.dto.OrderVO;
import com.anxin.travel.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AmapClient amapClient;
    private final OrderService orderService;

    // 会话状态
    private final Map<String, AgentState> sessionStates = new ConcurrentHashMap<>();
    private final Map<String, CandidateDestination> pendingDestinations = new ConcurrentHashMap<>();

    public void processIntention(String sessionId, Long userId, String content, String type) {
        AgentState state = sessionStates.getOrDefault(sessionId, AgentState.INIT);
        log.info("处理意图消息, sessionId={}, state={}, type={}, content={}", sessionId, state, type, content);

        if ("user_message".equals(type)) {
            handleNormalMessage(sessionId, userId, content);
        } else if ("confirm".equals(type)) {
            handleConfirm(sessionId, userId);
        } else {
            sendText(sessionId, "不支持的消息类型");
        }
    }

    private void handleNormalMessage(String sessionId, Long userId, String content) {
        AgentState state = sessionStates.getOrDefault(sessionId, AgentState.INIT);

        switch (state) {
            case INIT:
                if (isTaxiIntent(content)) {
                    sessionStates.put(sessionId, AgentState.INTENT_RECOGNIZED);
                    searchDestination(sessionId, userId, content);
                } else {
                    sendText(sessionId, "您好，请输入目的地，例如：我要去机场");
                }
                break;

            case INTENT_RECOGNIZED:
                searchDestination(sessionId, userId, content);
                break;

            case DEST_PARSED:
                sendText(sessionId, "已为您找到目的地，请确认是否叫车");
                break;

            case WAIT_CONFIRM:
                sendText(sessionId, "请先确认是否叫车");
                break;

            default:
                break;
        }
    }

    private void handleConfirm(String sessionId, Long userId) {
        AgentState state = sessionStates.get(sessionId);

        if (state == AgentState.DEST_PARSED || state == AgentState.ROUTE_READY) {
            CandidateDestination dest = pendingDestinations.get(sessionId);
            if (dest == null) {
                sendText(sessionId, "未找到目的地信息，请重新输入");
                sessionStates.put(sessionId, AgentState.INIT);
                return;
            }

            CreateOrderRequest request = new CreateOrderRequest();
            request.setDestName(dest.getName());
            request.setDestLat(dest.getLat());
            request.setDestLng(dest.getLng());

            OrderVO order = orderService.createOrder(userId, request);

            sessionStates.put(sessionId, AgentState.ORDER_CREATED);
            pendingDestinations.remove(sessionId);

            Map<String, Object> result = new HashMap<>();
            result.put("type", "orderCreated");
            result.put("orderId", order.getId());

            NativeWebSocket.sendToSession(sessionId, toJson(result));

        } else {
            sendText(sessionId, "无法确认，请重新输入目的地");
            sessionStates.put(sessionId, AgentState.INIT);
        }
    }

    private boolean isTaxiIntent(String text) {
        return text.contains("叫车") || text.contains("打车") || text.contains("去");
    }

    private void searchDestination(String sessionId, Long userId, String keyword) {
        double lat = 30.0;
        double lng = 120.0;

        List<CandidateDestination> candidates = amapClient.searchPoi(keyword, lat, lng);

        if (candidates.isEmpty()) {
            sendText(sessionId, "未找到相关地点，请重新输入");
            sessionStates.put(sessionId, AgentState.INIT);
            return;
        }

        if (candidates.size() > 1) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "candidates");
            msg.put("candidates", candidates);

            NativeWebSocket.sendToSession(sessionId, toJson(msg));

            sessionStates.put(sessionId, AgentState.DEST_PARSED);
            pendingDestinations.put(sessionId, candidates.get(0));

        } else {
            CandidateDestination dest = candidates.get(0);
            pendingDestinations.put(sessionId, dest);

            RouteResult route = amapClient.getRoute("用户当前位置", dest.getAddress(), "driving");

            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "route");
            msg.put("dest", dest);
            msg.put("price", route.getPrice());
            msg.put("duration", route.getDuration());

            NativeWebSocket.sendToSession(sessionId, toJson(msg));

            sessionStates.put(sessionId, AgentState.ROUTE_READY);
        }
    }

    private void sendText(String sessionId, String text) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "text");
        msg.put("content", text);
        NativeWebSocket.sendToSession(sessionId, toJson(msg));
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"type\":\"text\",\"content\":\"JSON错误\"}";
        }
    }
}