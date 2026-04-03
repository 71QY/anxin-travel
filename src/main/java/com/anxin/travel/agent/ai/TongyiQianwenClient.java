package com.anxin.travel.agent.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.anxin.travel.agent.model.AgentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class TongyiQianwenClient {

    @Value("${tongyi.api.key:}")
    private String apiKey;

    @Value("${tongyi.api.url:https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 解析用户意图（返回 AgentIntent 对象）
     * @param text 用户输入
     * @return AgentIntent 对象，如果解析失败返回 null
     */
    public AgentIntent parseIntent(String text) {
        try {
            // 使用更简洁、强制性的 System Prompt（指令驱动模式）
            String systemPrompt = "你是一个出行助手意图提取引擎。请严格按要求输出 JSON。\n" +
                    "【任务】从用户输入中提取目的地或意图类别。\n" +
                    "【输出格式】{\"type\":\"SEARCH|CONFIRM|ORDER|CHAT\",\"keyword\":\"String\",\"needSearch\":boolean,\"autoOrder\":boolean}\n" +
                    "【提取规则】\n" +
                    "1. 核心词提取：只保留地名主体。去除“我想去”、“带我到”、“麻烦搜一下”等口语。\n" +
                    "2. 强力清洗：绝对禁止包含括号 ()、（）、空格、楼层、或“南区/北区”等子区域。示例：'北京大学 (东门)' -> '北京大学'。\n" +
                    "3. 缩写转换：bjdx->北京大学，tsdx->清华大学，hsfsxy->韩山师范学院。\n" +
                    "4. 如果是“找医院”、“附近的药店”，keyword 分别为“医院”、“药店”。\n" +
                    "5. 确认意图：用户说“第一个”、“就这个”、“下单”时，type 为 CONFIRM 或 ORDER，keyword 设为 null 或当前选定值。\n" +
                    "6. 保持原地名：绝对不要修改或替换用户输入的原始地名，保持原样输出。";

            JSONObject body = new JSONObject();
            body.put("model", "qwen-turbo");
            
            // 构造符合 OpenAI/DashScope 规范的消息流
            JSONArray messages = new JSONArray();
            messages.add(new JSONObject().fluentPut("role", "system").fluentPut("content", systemPrompt));
            messages.add(new JSONObject().fluentPut("role", "user").fluentPut("content", text));
            
            JSONObject input = new JSONObject();
            input.put("messages", messages);
            body.put("input", input);
            body.put("parameters", new JSONObject().fluentPut("result_format", "message"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            String resp = restTemplate.postForObject(apiUrl, entity, String.class);

            // 解析逻辑（注意：DashScope 返回结构可能随 result_format 改变）
            JSONObject json = JSON.parseObject(resp);
            String content = json.getJSONObject("output").getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            
            // 简单清洗下可能存在的 Markdown 标签
            content = content.replace("```json", "").replace("```", "").trim();
            
            log.info("AI 清洗后意图：{}", content);
            return JSON.parseObject(content, AgentIntent.class);

        } catch (Exception e) {
            log.error("AI 解析意图失败：{}", e.getMessage());
            return null;
        }
    }
}