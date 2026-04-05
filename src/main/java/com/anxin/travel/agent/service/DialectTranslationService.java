package com.anxin.travel.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 方言翻译服务
 * 将方言文本转换为标准普通话
 */
@Slf4j
@Service
public class DialectTranslationService {

    @Value("${tongyi.api.key:}")
    private String apiKey;

    @Value("${tongyi.api.url:https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 将方言文本转换为普通话
     * 
     * @param dialectText 方言文本
     * @param dialectType 方言类型（cantonese/sichuan/henan等）
     * @return 转换后的普通话文本
     */
    public String translateToMandarin(String dialectText, String dialectType) {
        if (dialectText == null || dialectText.trim().isEmpty()) {
            return dialectText;
        }

        // 如果已经是普通话，直接返回
        if ("mandarin".equalsIgnoreCase(dialectType) || "auto".equalsIgnoreCase(dialectType)) {
            log.debug("【方言翻译】跳过翻译，方言类型：{}", dialectType);
            return dialectText;
        }

        try {
            log.info("【方言翻译】开始翻译，方言类型：{}，原文：{}", dialectType, dialectText);

            // 构建系统提示词
            String systemPrompt = buildSystemPrompt(dialectType);

            // 调用通义千问 API
            String mandarinText = callQwenAPI(systemPrompt, dialectText);

            log.info("【方言翻译】翻译完成，结果：{}", mandarinText);
            return mandarinText;

        } catch (Exception e) {
            log.error("【方言翻译】翻译失败：{}", e.getMessage(), e);
            // 翻译失败时返回原文，让后续流程继续处理
            return dialectText;
        }
    }

    /**
     * 根据方言类型构建系统提示词
     */
    private String buildSystemPrompt(String dialectType) {
        String dialectName = getDialectName(dialectType);
        
        return "你是一个专业的方言翻译助手。请将用户输入的" + dialectName + "准确翻译为标准普通话。\n" +
               "【翻译规则】\n" +
               "1. 保持原意不变，仅转换表达方式\n" +
               "2. 地名、人名、专有名词保持不变\n" +
               "3. 去除方言特有的语气词和口头禅\n" +
               "4. 输出简洁的普通话，不要添加解释\n" +
               "5. 如果已经是普通话，直接返回原文\n" +
               "6. 只输出翻译结果，不要输出其他内容\n" +
               "\n" +
               "【示例】\n" +
               "输入（粤语）：\"我想去北京路行下街\"\n" +
               "输出：\"我想去北京路逛街\"\n" +
               "\n" +
               "输入（四川话）：\"我要切春熙路耍哈儿\"\n" +
               "输出：\"我要去春熙路玩一会儿\"\n";
    }

    /**
     * 获取方言中文名称
     */
    private String getDialectName(String dialectType) {
        switch (dialectType.toLowerCase()) {
            case "cantonese":
                return "粤语";
            case "sichuan":
            case "lmz":
                return "四川话";
            case "henan":
                return "河南话";
            case "hunan":
                return "湖南话";
            case "fujian":
            case "minnan":
                return "闽南语";
            case "shaanxi":
                return "陕西话";
            case "dongbei":
                return "东北话";
            default:
                return "方言";
        }
    }

    /**
     * 调用通义千问 API 进行翻译
     */
    private String callQwenAPI(String systemPrompt, String userText) {
        JSONObject body = new JSONObject();
        body.put("model", "qwen-turbo");

        // 构造消息流
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().fluentPut("role", "system").fluentPut("content", systemPrompt));
        messages.add(new JSONObject().fluentPut("role", "user").fluentPut("content", userText));

        JSONObject input = new JSONObject();
        input.put("messages", messages);
        body.put("input", input);
        body.put("parameters", new JSONObject().fluentPut("result_format", "message"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
        String resp = restTemplate.postForObject(apiUrl, entity, String.class);

        // 解析响应
        JSONObject json = JSON.parseObject(resp);
        String content = json.getJSONObject("output")
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        // 清洗 Markdown 标签
        content = content.replace("```json", "").replace("```", "").trim();

        return content;
    }
}
