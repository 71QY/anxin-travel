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
        
        // 限制图片大小（10MB）- 通义千问支持最大 10MB 的图片
        int maxSize = 10 * 1024 * 1024; // 10MB
        if (imageBase64.length() > maxSize) {
            log.warn("图片过大：{} bytes ({} MB), 超过 10MB 限制", imageBase64.length(), imageBase64.length() / 1024 / 1024);
            throw new IllegalArgumentException("图片大小不能超过 10MB，请先压缩后重试");
        }
        
        // 建议大小检查（超过 3MB 给出警告，但不拒绝）
        if (imageBase64.length() > 3 * 1024 * 1024) {
            log.warn("图片较大：{} bytes ({} MB)，建议压缩到 3MB 以内以提高识别速度", 
                imageBase64.length(), imageBase64.length() / 1024 / 1024);
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
            
            // 检查是否有错误信息
            if (json.containsKey("code") || json.containsKey("error")) {
                String errorCode = json.getString("code");
                String errorMsg = json.getString("message") != null ? json.getString("message") : json.getString("error");
                log.error("通义千问 API 返回错误：code={}, message={}", errorCode, errorMsg);
                throw new RuntimeException("图片识别服务异常：" + errorMsg);
            }
            
            // 安全解析响应结构，添加空值检查
            JSONObject output = json.getJSONObject("output");
            if (output == null) {
                log.error("API 响应中缺少 output 字段，完整响应：{}", resp);
                throw new RuntimeException("图片识别失败：API 响应格式异常");
            }
            
            // 注意：choices 是数组，不是对象
            JSONArray choicesArray = output.getJSONArray("choices");
            if (choicesArray == null || choicesArray.isEmpty()) {
                log.error("API 响应中 choices 数组为空，完整响应：{}", resp);
                throw new RuntimeException("图片识别失败：API 响应格式异常");
            }
            
            // 获取第一个 choice 对象
            JSONObject firstChoice = choicesArray.getJSONObject(0);
            if (firstChoice == null) {
                log.error("API 响应中第一个 choice 为空，完整响应：{}", resp);
                throw new RuntimeException("图片识别失败：响应解析异常");
            }
            
            // 获取 message 对象
            JSONObject messageObj = firstChoice.getJSONObject("message");
            if (messageObj == null) {
                log.error("API 响应中缺少 message 字段，完整响应：{}", resp);
                throw new RuntimeException("图片识别失败：响应解析异常");
            }
            
            // 获取 content（注意：content 也是数组）
            Object contentObj = messageObj.get("content");
            String result;
            
            if (contentObj instanceof JSONArray) {
                // content 是数组格式：[{"text": "..."}]
                JSONArray contentArray = (JSONArray) contentObj;
                if (contentArray == null || contentArray.isEmpty()) {
                    log.warn("识别结果为空，可能图片中没有文字信息");
                    return "未识别到地址信息";
                }
                
                // 提取 text 字段
                JSONObject firstContent = contentArray.getJSONObject(0);
                result = firstContent != null ? firstContent.getString("text") : null;
                
            } else if (contentObj instanceof String) {
                // content 是字符串格式："..."
                result = (String) contentObj;
                
            } else {
                log.error("未知的 content 格式：{}", contentObj != null ? contentObj.getClass().getName() : "null");
                throw new RuntimeException("图片识别失败：响应格式异常");
            }
            
            if (result == null || result.trim().isEmpty()) {
                log.warn("识别结果为空，可能图片中没有文字信息");
                return "未识别到地址信息";
            }

            log.info("✅ OCR 识别成功，提取文字：{}", result);
            return result;

        } catch (IllegalArgumentException e) {
            // 参数校验异常，直接抛出
            log.warn("图片参数校验失败：{}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            // 业务异常，保留原始错误信息
            log.error("图片识别业务异常：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // 其他未知异常
            log.error("图片 OCR 识别发生未知异常", e);
            throw new RuntimeException("图片识别失败：" + e.getMessage());
        }
    }

    /**
     * 描述图片内容（用于非地址类图片的上下文记忆）
     * @param imageBase64 Base64 编码的图片
     * @return 图片内容描述
     */
    public String describeImage(String imageBase64) {
        // 复用校验逻辑
        if (imageBase64 == null || imageBase64.isEmpty()) {
            throw new IllegalArgumentException("图片不能为空");
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

            // 添加文本指令：描述图片内容
            JSONObject textObj = new JSONObject();
            textObj.put("text", "请简要描述这张图片的内容。如果图片中包含人物/角色，请说明是谁；如果是场景，请描述场景；如果是文字截图，请提取文字内容。控制在50字以内，只描述看到的内容。\n");
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

            log.info("🖼️ 图片描述结果：{}", resp);

            JSONObject json = JSON.parseObject(resp);
            
            // 检查是否有错误信息
            if (json.containsKey("code") || json.containsKey("error")) {
                String errorCode = json.getString("code");
                String errorMsg = json.getString("message") != null ? json.getString("message") : json.getString("error");
                log.error("通义千问 API 返回错误：code={}, message={}", errorCode, errorMsg);
                return "一张图片";  // 降级返回
            }
            
            // 安全解析响应结构
            JSONObject output = json.getJSONObject("output");
            if (output == null) {
                log.error("API 响应中缺少 output 字段");
                return "一张图片";
            }
            
            JSONArray choicesArray = output.getJSONArray("choices");
            if (choicesArray == null || choicesArray.isEmpty()) {
                return "一张图片";
            }
            
            JSONObject firstChoice = choicesArray.getJSONObject(0);
            if (firstChoice == null) {
                return "一张图片";
            }
            
            JSONObject messageObj = firstChoice.getJSONObject("message");
            if (messageObj == null) {
                return "一张图片";
            }
            
            Object contentObj = messageObj.get("content");
            String result;
            
            if (contentObj instanceof JSONArray) {
                JSONArray contentArray = (JSONArray) contentObj;
                if (contentArray == null || contentArray.isEmpty()) {
                    return "一张图片";
                }
                JSONObject firstContent = contentArray.getJSONObject(0);
                result = firstContent != null ? firstContent.getString("text") : null;
            } else if (contentObj instanceof String) {
                result = (String) contentObj;
            } else {
                return "一张图片";
            }
            
            if (result == null || result.trim().isEmpty()) {
                return "一张图片";
            }

            log.info("✅ 图片描述成功：{}", result);
            return result;

        } catch (IllegalArgumentException e) {
            log.warn("图片参数校验失败：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("图片描述失败", e);
            return "一张图片";  // 降级返回，不阻断流程
        }
    }
}
