package com.anxin.travel.agent.model;

import lombok.Data;

@Data
public class UserMessage {
    private String type;        // 固定为 "user_message"
    private String sessionId;   // 会话ID，由客户端生成
    private String content;      // 用户输入的文本
}