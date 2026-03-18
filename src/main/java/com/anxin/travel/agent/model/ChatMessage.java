package com.anxin.travel.agent.model;

import lombok.Data;
import java.util.Date;

@Data
public class ChatMessage {
    private String role;      // "user" 或 "assistant"
    private String content;
    private Date createTime;
}