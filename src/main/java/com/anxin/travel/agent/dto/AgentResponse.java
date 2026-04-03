package com.anxin.travel.agent.dto;

import com.anxin.travel.module.map.dto.PoiDTO;
import com.anxin.travel.module.map.dto.RouteResult;
import lombok.Data;

import java.util.List;

/**
 * 智能体统一响应对象
 */
@Data
public class AgentResponse {

    /**
     * 响应类型：CHAT / SEARCH / ROUTE / ORDER
     */
    private String type;

    /**
     * 回复消息
     */
    private String message;

    /**
     * POI 列表（搜索时使用）
     */
    private List<PoiDTO> places;

    /**
     * 路线信息（路线规划时使用）
     */
    private RouteResult route;

    /**
     * 附加数据（可选）
     */
    private Object data;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息（失败时使用）
     */
    private String error;

    /**
     * 构建成功的搜索响应
     */
    public static AgentResponse successSearch(List<PoiDTO> places, String message) {
        AgentResponse response = new AgentResponse();
        response.setType("SEARCH");
        response.setSuccess(true);
        response.setMessage(message);
        response.setPlaces(places);
        return response;
    }

    /**
     * 构建成功的聊天响应
     */
    public static AgentResponse successChat(String message) {
        AgentResponse response = new AgentResponse();
        response.setType("CHAT");
        response.setSuccess(true);
        response.setMessage(message);
        return response;
    }

    /**
     * 构建成功的订单响应
     */
    public static AgentResponse successOrder(Object orderData, String message) {
        AgentResponse response = new AgentResponse();
        response.setType("ORDER");
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(orderData);
        return response;
    }

    /**
     * 构建成功的路线响应
     */
    public static AgentResponse successRoute(RouteResult route, String message) {
        AgentResponse response = new AgentResponse();
        response.setType("ROUTE");
        response.setSuccess(true);
        response.setMessage(message != null ? message : "路线规划成功");
        response.setRoute(route);
        return response;
    }

    /**
     * 构建错误响应
     */
    public static AgentResponse error(String errorMessage) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(false);
        response.setError(errorMessage);
        response.setMessage(errorMessage);
        return response;
    }
}
