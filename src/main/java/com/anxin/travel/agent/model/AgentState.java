package com.anxin.travel.agent.model;

public enum AgentState {
    INIT,               // 初始状态
    INTENT_RECOGNIZED,  // 已识别叫车意图
    DEST_PARSED,        // 已解析目的地（多个候选）
    ROUTE_READY,        // 路线已就绪，等待确认
    WAIT_CONFIRM,       // 等待用户确认
    ORDER_CREATED,      // 订单已创建
    IMAGE_RECOGNIZED,   // 新增：图片识别成功
    ERROR               // 新增：异常状态
}