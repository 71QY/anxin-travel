package com.anxin.travel.module.guard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("family_guard")
public class FamilyGuard {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long guardianId;           // 亲友(守护者)用户ID
    private Long elderId;              // 长辈(被守护)用户ID(激活后)
    private String elderPhone;         // 长辈手机号(绑定前填写)
    private String elderName;          // 长辈姓名
    private String elderIdCard;        // 长辈身份证号
    private String guardianName;       // 亲友姓名(模拟实名)
    private String guardianIdCard;     // 亲友身份证号(模拟)
    private String guardianPhone;      // 亲友手机号(用于呼叫)
    private Integer status;            // 0待激活 1已绑定 2已解绑
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime bindTime;
    private LocalDateTime activateTime;
    private LocalDateTime unbindTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
