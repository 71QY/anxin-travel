package com.anxin.travel.module.guard.dto;

import lombok.Data;

/**
 * 绑定已有长辈账号请求
 */
@Data
public class BindExistingElderRequest {
    private String elderPhone;
    private String elderName;
    private String elderIdCard;
}
