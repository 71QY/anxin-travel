package com.anxin.travel.module.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 私聊消息实体
 */
@Data
@TableName("private_chat")
public class PrivateChat {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long senderId;        // 发送者用户ID
    private Long receiverId;      // 接收者用户ID
    private Integer messageType;  // 1文字 2语音 3图片
    private String content;       // 消息内容
    private Integer isRead;       // 0未读 1已读
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
