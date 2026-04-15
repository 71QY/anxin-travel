package com.anxin.travel.module.chat.dto;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private Integer messageType;    // 1文字 2语音 3快捷短语
    private String content;         // 消息内容
}
