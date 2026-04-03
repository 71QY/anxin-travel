package com.anxin.travel.agent.model;

import lombok.Data;

@Data
public class AgentIntent {

    /**
     * 类型
     * SEARCH / ORDER / CHAT / UNKNOWN
     */
    private String type;

    /**
     * 关键词（医院/酒店/餐厅）
     */
    private String keyword;

    /**
     * 是否需要搜索
     */
    private boolean needSearch;

    /**
     * 是否自动下单
     */
    private boolean autoOrder;

    /**
     * 纬度（可选）
     */
    private Double lat;

    /**
     * 经度（可选）
     */
    private Double lng;

    /**
     * 原始文本
     */
    private String rawText;

    /**
     * 会话 ID（用于状态管理）
     */
    private String sessionId;

    /**
     * 当前状态（用于流程控制）
     */
    private AgentState currentState;
}