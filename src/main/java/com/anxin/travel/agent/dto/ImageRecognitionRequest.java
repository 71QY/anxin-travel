package com.anxin.travel.agent.dto;

import lombok.Data;

/**
 * 图片识别请求（预留接口）
 */
@Data
public class ImageRecognitionRequest {

    /**
     * 图片 Base64 编码
     */
    private String imageBase64;

    /**
     * 图片 URL（可选）
     */
    private String imageUrl;

    /**
     * 识别类型：OCR / SCENE
     */
    private String recognitionType;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 用户位置（可选）
     */
    private Double lat;

    /**
     * 用户经度（可选）
     */
    private Double lng;
}
