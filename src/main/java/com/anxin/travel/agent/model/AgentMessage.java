package com.anxin.travel.agent.model;

import lombok.Data;
import java.util.List;

@Data
public class AgentMessage {
    private String type;           // "agent_message" 或 "tool_call"
    private String sessionId;
    private String content;
    private List<String> suggestions;  // 可选建议
    private String toolName;        // 工具名称（当 type = "tool_call" 时）
    private String status;          // 工具调用状态
}