package com.anxin.travel.agent.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.anxin.travel.agent.model.AgentIntent;
import com.anxin.travel.agent.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

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
        return parseIntentWithContext(null, text);
    }
    
    /**
     * 带上下文的意图识别（支持多轮对话）
     * @param history 历史消息列表
     * @param currentMessage 当前消息
     * @return AgentIntent 对象
     */
    public AgentIntent parseIntentWithContext(List<ChatMessage> history, String currentMessage) {
        try {
            // 使用更简洁、强制性的 System Prompt（指令驱动模式）
            String systemPrompt = "你是一个出行助手意图提取引擎。请严格按要求输出 JSON。\n" +
                    "【任务】从用户输入中提取目的地或意图类别。\n" +
                    "【输出格式】{\"type\":\"SEARCH|CONFIRM|ORDER|CHAT\",\"keyword\":\"String|null\",\"needSearch\":boolean,\"autoOrder\":false}\n" +
                    "【重要规则】autoOrder 必须始终为 false！绝对不允许自动下单！\n" +
                    "【位置信息说明 - 重要】\n" +
                    "每条用户消息都包含以下字段：\n" +
                    "- content: 用户的文字消息\n" +
                    "- lat: 用户当前纬度（Double，可能为 null）\n" +
                    "- lng: 用户当前经度（Double，可能为 null）\n" +
                    "\n" +
                    "当用户询问'我在哪里'、'我现在在哪里'、'我的位置'等问题时：\n" +
                    "1. 检查 lat 和 lng 字段是否存在\n" +
                    "2. 如果存在，后端会调用逆地理编码 API（将经纬度转换为地址）\n" +
                    "3. 你的任务是识别这是 CHAT 类型，让后端处理位置转换\n" +
                    "4. 示例回答（由后端生成）：'您现在位于广东省潮州市金山中学附近，具体位置是金碧路与东山路交汇处'\n" +
                    "5. 注意事项：不要告诉用户你收到了经纬度数据，直接告诉用户具体地址\n" +
                    "6. 如果 lat/lng 为 null，才说'我还没有收到您的位置信息'\n" +
                    "7. 回答要简洁明了，适合长辈理解\n" +
                    "\n" +
                    "【意图分类规则 - 重要】\n" +
                    "1. CHAT 类型（普通对话）：当用户说以下内容时，type=CHAT, keyword=null\n" +
                    "   - 问候语：'你好'、'您好'、'早上好'、'在吗'\n" +
                    "   - 感谢语：'谢谢'、'感谢你'、'太好了'\n" +
                    "   - 告别语：'再见'、'拜拜'、'下次见'\n" +
                    "   - 闲聊：'你是谁'、'你能做什么'、'介绍一下自己'\n" +
                    "   - 确认/否定：'好的'、'可以'、'不行'、'不需要'（无上下文时）\n" +
                    "   - 位置查询：'我在哪里'、'我的位置'、'我现在在哪'（此时后端会使用 lat/lng 返回地址）\n" +
                    "   - 其他非出行相关的对话\n" +
                    "2. SEARCH 类型（搜索地点）：当用户想要查找地点时\n" +
                    "   - '找医院'、'附近的餐厅'、'去北京大学'、'我要去故宫'\n" +
                    "   - keyword 必须提取核心地名，去除口语杂质\n" +
                    "   - 【禁止】SEARCH 类型绝对不能设置 autoOrder=true\n" +
                    "3. CONFIRM 类型（确认选择）：当用户在确认之前搜索结果中的选项\n" +
                    "   - '第一个'、'第二个'、'就这个'、'去这里'、'选这个'\n" +
                    "   - 【前提】必须有历史消息显示之前返回过搜索结果\n" +
                    "4. ORDER 类型（创建订单）：当用户明确要求下单时\n" +
                    "   - '下单'、'叫车'、'打车'、'帮我叫车'\n" +
                    "   - 【前提】必须先有 CONFIRM 步骤，不能直接从 SEARCH 跳到 ORDER\n" +
                    "\n" +
                    "【关键词提取规则】\n" +
                    "1. 核心词提取：只保留地名主体。去除'我想去'、'带我到'、'麻烦搜一下'等口语。\n" +
                    "2. 强力清洗：绝对禁止包含括号 ()、（）、空格、楼层、或'南区/北区'等子区域。示例：'北京大学 (东门)' -> '北京大学'。\n" +
                    "3. 缩写转换：bjdx->北京大学，tsdx->清华大学，hsfsxy->韩山师范学院。\n" +
                    "4. 如果是'找医院'、'附近的药店'，keyword 分别为'医院'、'药店'。\n" +
                    "5. 保持原地名：绝对不要修改或替换用户输入的原始地名，保持原样输出。\n" +
                    "6. CHAT 类型时，keyword 必须为 null，不要强行提取关键词。\n" +
                    "\n" +
                    "【上下文感知 - 重要】如果提供了历史消息，必须根据上下文理解用户的意图。例如：\n" +
                    "   - 历史：用户搜索了'医院'，AI 返回了多个医院列表\n" +
                    "   - 当前：'第一个' -> type=CONFIRM, keyword=null （表示确认选择第一个医院）\n" +
                    "   - 当前：'第二个' -> type=CONFIRM, keyword=null （表示确认选择第二个医院）\n" +
                    "   - 当前：'远不远' -> type=CHAT, keyword=null （询问距离信息）\n" +
                    "   - 当前：'换一个' -> type=SEARCH, keyword=医院 （重新搜索同类地点）\n" +
                    "   - 当前：'附近有没有餐厅' -> type=SEARCH, keyword=餐厅 （切换搜索类型）\n" +
                    "   - 历史：无搜索结果，用户说'好的' -> type=CHAT, keyword=null （普通对话）\n" +
                    "\n" +
                    "【关键原则】\n" +
                    "- 优先判断是否为普通对话（CHAT），不要强行将闲聊识别为搜索\n" +
                    "- 只有在明确提到地点或搜索意图时，才使用 SEARCH 类型\n" +
                    "- CONFIRM 类型必须有上下文支持（之前返回过搜索结果）\n" +
                    "- 不确定时，优先使用 CHAT 类型，避免误识别\n" +
                    "- 【绝对禁止】任何情况下都不允许 autoOrder=true，必须由用户手动确认";

            JSONObject body = new JSONObject();
            body.put("model", "qwen-turbo");
            
            // 构造符合 OpenAI/DashScope 规范的消息流
            JSONArray messages = new JSONArray();
            messages.add(new JSONObject().fluentPut("role", "system").fluentPut("content", systemPrompt));
            
            // 【关键修复】添加历史消息到上下文
            if (history != null && !history.isEmpty()) {
                // 【优化】最多保留最近 10 条历史消息（5轮对话），平衡上下文长度和Token消耗
                int startIdx = Math.max(0, history.size() - 10);
                for (int i = startIdx; i < history.size(); i++) {
                    ChatMessage msg = history.get(i);
                    String role = "user".equals(msg.getRole()) ? "user" : "assistant";
                    messages.add(new JSONObject().fluentPut("role", role).fluentPut("content", msg.getContent()));
                }
                log.debug("✅ 已添加 {} 条历史消息到上下文（共 {} 条）", history.size() - startIdx, history.size());
            }
            
            // 添加当前消息
            messages.add(new JSONObject().fluentPut("role", "user").fluentPut("content", currentMessage));
            
            String jsonContent = callTongyiAPI(messages, true);
            return JSON.parseObject(jsonContent, AgentIntent.class);

        } catch (Exception e) {
            log.error("AI 解析意图失败：{}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 通用对话方法（用于 CHAT 类型的自然对话）
     * @param messages 消息列表（包含 system + history + current）
     * @return AI 生成的回复文本
     */
    public String chatCompletion(JSONArray messages) {
        try {
            log.info("🤖 调用通义千问对话 API，消息数：{}", messages.size());
            String result = callTongyiAPI(messages, false);
            log.info("✅ AI 对话成功，回复长度：{}", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("❌ AI 对话生成失败：{}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 调用通义千问 API（通用方法）
     * @param messages 消息列表
     * @param expectJSON 是否期望返回 JSON 格式（意图识别用 true，普通对话用 false）
     * @return 响应内容
     */
    private String callTongyiAPI(JSONArray messages, boolean expectJSON) {
        JSONObject body = new JSONObject();
        body.put("model", "qwen-turbo");
        
        JSONObject input = new JSONObject();
        input.put("messages", messages);
        body.put("input", input);
        body.put("parameters", new JSONObject().fluentPut("result_format", "message"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
        String resp = restTemplate.postForObject(apiUrl, entity, String.class);

        // 解析逻辑
        JSONObject json = JSON.parseObject(resp);
        String content = json.getJSONObject("output")
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content");
        
        if (expectJSON) {
            // 简单清洗下可能存在的 Markdown 标签
            content = content.replace("```json", "").replace("```", "").trim();
            log.info("AI 清洗后意图：{}", content);
        } else {
            log.info("AI 对话回复：{}", content);
        }
        
        return content;
    }
}