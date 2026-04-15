package com.anxin.travel.module.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("order_chat")
public class OrderChat {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long orderId;              // 订单ID(群聊维度)
    private Long senderId;             // 发送者用户ID
    private Integer senderType;        // 1长辈 2亲友 3司机
    private Integer messageType;       // 1文字 2语音 3快捷短语
    private String content;            // 消息内容(文字或语音URL)
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
