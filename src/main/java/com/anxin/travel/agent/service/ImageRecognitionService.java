package com.anxin.travel.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class ImageRecognitionService {

    @Value("${tongyi.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 从图片中提取文字（OCR）
     * @param imageBase64 Base64 编码的图片
     * @return 提取的文字内容
     */
    public String extractTextFromImage(String imageBase64) {
        // 新增：图片大小和格式校验
        if (imageBase64 == null || imageBase64.isEmpty()) {
            throw new IllegalArgumentException("图片不能为空");
        }
        
        // 校验图片格式（支持 JPEG, PNG, BMP, WEBP）
        String lowerCase = imageBase64.toLowerCase();
        boolean isSupportedFormat = lowerCase.startsWith("data:image/jpeg") || 
                                   lowerCase.startsWith("data:image/png") || 
                                   lowerCase.startsWith("data:image/bmp") ||
                                   lowerCase.contains("base64");  // 兼容不带 data URI 的情况
        
        if (!isSupportedFormat) {
            throw new IllegalArgumentException("不支持的图片格式，仅支持 JPEG/PNG/BMP/WEBP");
        }
        
        // 限制图片大小（5MB）
        int maxSize = 5 * 1024 * 1024; // 5MB
        if (imageBase64.length() > maxSize) {
            log.warn("图片过大：{} bytes, 尝试自动压缩...", imageBase64.length());
            // TODO: 这里可以添加图片压缩逻辑，暂时直接拒绝
            throw new IllegalArgumentException("图片大小不能超过 5MB，请先压缩后重试");
        }
        
        // 建议大小检查（超过 1MB 给出警告）
        if (imageBase64.length() > 1024 * 1024) {
            log.warn("图片较大：{} bytes，建议压缩到 1MB 以内以提高识别速度", imageBase64.length());
        }
        
        try {
            String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

            JSONObject body = new JSONObject();
            body.put("model", "qwen-vl-plus");

            JSONObject input = new JSONObject();
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");

            JSONArray content = new JSONArray();

            // 添加图片
            JSONObject imageObj = new JSONObject();
            if (imageBase64.startsWith("data:image")) {
                imageObj.put("image", imageBase64);
            } else {
                imageObj.put("image", "data:image/jpeg;base64," + imageBase64);
            }
            content.add(imageObj);

            // 添加文本指令
            JSONObject textObj = new JSONObject();
            textObj.put("text", "请提取图片中的地址信息、地点名称。如果是聊天截图，请提取对话中提到的地址。只返回地址文字，不要其他内容。");
            content.add(textObj);

            message.put("content", content);
            messages.add(message);
            input.put("messages", messages);
            body.put("input", input);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);

            String resp = restTemplate.postForObject(url, entity, String.class);

            log.info("通义 VL 识别结果：{}", resp);

            JSONObject json = JSON.parseObject(resp);
            String result = json.getJSONObject("output")
                    .getJSONObject("choices")
                    .getJSONArray("message")
                    .getJSONObject(0)
                    .getString("content");

            return result;

        } catch (Exception e) {
            log.error("图片 OCR 识别失败", e);
            throw new RuntimeException("图片识别失败：" + e.getMessage());
        }
    }
}
