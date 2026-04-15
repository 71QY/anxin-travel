package com.anxin.travel.agent.service;

import com.alibaba.fastjson.JSON;
import com.anxin.travel.agent.ai.TongyiQianwenClient;
import com.anxin.travel.agent.dto.AgentResponse;
import com.anxin.travel.agent.model.AgentIntent;
import com.anxin.travel.agent.model.AgentState;
import com.anxin.travel.module.map.client.AmapClient;
import com.anxin.travel.module.map.client.TencentMapClient;
import com.anxin.travel.module.map.dto.PoiDTO;
import com.anxin.travel.module.map.dto.RouteResult;
import com.anxin.travel.module.order.dto.CreateOrderRequest;
import com.anxin.travel.module.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class AgentService {

    private final MemoryService memoryService;
    private final AmapClient amapClient;
    private final TencentMapClient tencentMapClient;  // 新增：腾讯地图客户端
    private final OrderService orderService;
    private final TongyiQianwenClient tongyiClient;
    private final ImageRecognitionService imageRecognitionService;
    private final Executor businessExecutor;
    private final SearchStrategyEngine searchStrategyEngine;  // 新增：搜索策略引擎
    
    // 新增：知名地标集合（这些地点应该直接进行全国搜索）
    private static final Set<String> FAMOUS_LANDMARKS = Set.of(
        "故宫", "长城", "天安门", "东方明珠", "西湖", "黄山", "九寨沟",
        "兵马俑", "布达拉宫", "鼓浪屿", "张家界", "泰山", "庐山", "峨眉山",
        "颐和园", "天坛", "鸟巢", "水立方", "外滩", "自由女神像", "埃菲尔铁塔",
        // 知名大学
        "北京大学", "清华大学", "复旦大学", "上海交通大学", "浙江大学", "南京大学",
        "中国人民大学", "中国科学技术大学", "武汉大学", "华中科技大学", "中山大学"
    );
    
    // 新增：知名地标坐标缓存（用于高德搜索失败时的备选方案）
    private static final Map<String, double[]> FAMOUS_LANDMARK_COORDINATES = new HashMap<>();
    static {
        // 北京
        FAMOUS_LANDMARK_COORDINATES.put("故宫", new double[]{39.916345, 116.397155});
        FAMOUS_LANDMARK_COORDINATES.put("天安门", new double[]{39.908720, 116.397479});
        FAMOUS_LANDMARK_COORDINATES.put("长城", new double[]{40.431667, 116.570833});
        FAMOUS_LANDMARK_COORDINATES.put("颐和园", new double[]{39.999317, 116.275466});
        FAMOUS_LANDMARK_COORDINATES.put("天坛", new double[]{39.881630, 116.406611});
        FAMOUS_LANDMARK_COORDINATES.put("鸟巢", new double[]{39.992440, 116.397690});
        FAMOUS_LANDMARK_COORDINATES.put("水立方", new double[]{39.991970, 116.390370});
        // 上海
        FAMOUS_LANDMARK_COORDINATES.put("东方明珠", new double[]{31.239689, 121.499755});
        FAMOUS_LANDMARK_COORDINATES.put("外滩", new double[]{31.239689, 121.490370});
        // 杭州
        FAMOUS_LANDMARK_COORDINATES.put("西湖", new double[]{30.259661, 120.144490});
        // 西安
        FAMOUS_LANDMARK_COORDINATES.put("兵马俑", new double[]{34.384860, 109.273470});
        // 拉萨
        FAMOUS_LANDMARK_COORDINATES.put("布达拉宫", new double[]{29.655410, 91.118090});
        // 其他
        FAMOUS_LANDMARK_COORDINATES.put("黄山", new double[]{30.137000, 118.155000});
        FAMOUS_LANDMARK_COORDINATES.put("泰山", new double[]{36.260000, 117.120000});
        // 广州塔
        FAMOUS_LANDMARK_COORDINATES.put("广州塔", new double[]{23.109, 113.319});
        // 韩山师范学院
        FAMOUS_LANDMARK_COORDINATES.put("韩山师范学院", new double[]{23.6705, 116.6547});
        // 知名大学
        FAMOUS_LANDMARK_COORDINATES.put("北京大学", new double[]{39.986940, 116.305700});
        FAMOUS_LANDMARK_COORDINATES.put("清华大学", new double[]{40.003200, 116.326800});
        FAMOUS_LANDMARK_COORDINATES.put("复旦大学", new double[]{31.297640, 121.503700});
        FAMOUS_LANDMARK_COORDINATES.put("上海交通大学", new double[]{31.024900, 121.433600});
        FAMOUS_LANDMARK_COORDINATES.put("浙江大学", new double[]{30.308600, 120.086200});
    }
    
    // 新增：搜索词纠偏字典（热点映射）- 用户习惯称呼 -> 标准名称
    private static final Map<String, String> KEYWORD_CORRECTION_MAP = new HashMap<>();
    static {
        // 学校类
        KEYWORD_CORRECTION_MAP.put("老校区", "韩山师范学院东区");
        KEYWORD_CORRECTION_MAP.put("新校区", "韩山师范学院西区");
        KEYWORD_CORRECTION_MAP.put("本部", "韩山师范学院");
        
        // 地名俗称
        KEYWORD_CORRECTION_MAP.put("大裤衩", "CCTV 总部大楼");
        KEYWORD_CORRECTION_MAP.put("鸟蛋", "国家大剧院");
        KEYWORD_CORRECTION_MAP.put("玉米棒", "央视大楼");
        
        // 商圈简称
        KEYWORD_CORRECTION_MAP.put("万达", "万达广场");
        KEYWORD_CORRECTION_MAP.put("万象城", "万象天地");
        KEYWORD_CORRECTION_MAP.put("太古里", "三里屯太古里");
        
        // 交通枢纽
        KEYWORD_CORRECTION_MAP.put("北京站", "北京火车站");
        KEYWORD_CORRECTION_MAP.put("上海站", "上海火车站");
        KEYWORD_CORRECTION_MAP.put("广州南", "广州南站");
    }

    public AgentService(MemoryService memoryService, 
                        AmapClient amapClient,
                        TencentMapClient tencentMapClient,  // 新增参数
                        OrderService orderService,
                        TongyiQianwenClient tongyiClient,
                        ImageRecognitionService imageRecognitionService,
                        @org.springframework.beans.factory.annotation.Qualifier("businessExecutor") Executor businessExecutor,
                        SearchStrategyEngine searchStrategyEngine) {  // 新增参数
        this.memoryService = memoryService;
        this.amapClient = amapClient;
        this.tencentMapClient = tencentMapClient;  // 注入腾讯地图客户端
        this.orderService = orderService;
        this.tongyiClient = tongyiClient;
        this.imageRecognitionService = imageRecognitionService;
        this.businessExecutor = businessExecutor;
        this.searchStrategyEngine = searchStrategyEngine;  // 注入搜索策略引擎
    }

    /**
     * 核心入口：解析用户意图并执行
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param message 用户消息
     * @param lat 纬度
     * @param lng 经度
     * @return AgentResponse 统一响应对象
     */
    public AgentResponse processIntention(String sessionId, Long userId, String message, Double lat, Double lng) {
        
        // 保存对话历史
        memoryService.saveMessage(sessionId, "user", message);
        memoryService.saveLocation(sessionId, lat, lng);

        try {
            // ① 创建意图对象
            AgentIntent intent = new AgentIntent();
            intent.setSessionId(sessionId);
            intent.setCurrentState(AgentState.INIT);
            log.info("【状态流转】sessionId={}, state=INIT", sessionId);
            
            // 新增：缓存用户位置（用于后续 confirmSelection）
            if (lat != null && lng != null) {
                memoryService.saveLocation(sessionId, lat, lng);
                log.info("💾 用户位置已缓存：sessionId=({}, {})", lat, lng);
            }
            
            // ② AI 意图识别（通义千问）
            AgentIntent aiIntent = parseIntentWithAI(message);
            
            // ③ AI 失败则 fallback
            if (aiIntent == null || aiIntent.getType() == null) {
                log.info("⚠️ AI 解析失败，使用 fallback");
                intent = fallbackIntent(message);
                intent.setSessionId(sessionId);
            } else {
                intent = aiIntent;
                intent.setSessionId(sessionId);
                
                // 修复：如果 AI 返回 CHAT 但用户输入包含知名地标/具体地点，强制切换为 SEARCH
                if ("CHAT".equals(aiIntent.getType())) {
                    boolean hasSpecificLocation = FAMOUS_LANDMARKS.stream().anyMatch(landmark -> message.contains(landmark))
                        || message.contains("医院") || message.contains("药店") || message.contains("餐厅") 
                        || message.contains("酒店") || message.contains("超市") || message.contains("银行")
                        || message.contains("加油站") || message.contains("商场") || message.contains("地铁")
                        || message.contains("大学") || message.contains("学院") || message.contains("去");
                    
                    if (hasSpecificLocation) {
                        log.warn("⚠️ AI 识别为 CHAT 但用户输入包含具体地点，强制切换为 SEARCH: {}", message);
                        intent = fallbackIntent(message);
                        intent.setSessionId(sessionId);
                    }
                }
                
                // 新增：如果 AI 替换了关键词，但原始消息包含具体地名，优先使用原始输入
                if (!message.equals(aiIntent.getKeyword()) && 
                    (message.contains("故宫") || message.contains("长城") || message.contains("天安门") ||
                     message.contains("北京大学") || message.contains("清华大学"))) {
                    intent.setKeyword(message);  // 使用用户原始输入
                    log.info("🔧 检测到 AI 替换关键词，恢复为用户原始输入：{}", message);
                }
                
                // 新增：严格校验 AI 返回的关键词是否合理
                if (aiIntent.getKeyword() != null && !aiIntent.getKeyword().trim().isEmpty()) {
                    // 检查 AI 是否返回了完全不相关的关键词
                    String aiKeyword = aiIntent.getKeyword();
                    if (!message.contains(aiKeyword) && !aiKeyword.equals("医院") && !aiKeyword.equals("药店") && 
                        !aiKeyword.equals("餐厅") && !aiKeyword.equals("酒店") && !aiKeyword.equals("超市") &&
                        !aiKeyword.equals("银行") && !aiKeyword.equals("加油站") && !aiKeyword.equals("商场") &&
                        !aiKeyword.equals("地铁站") && !aiKeyword.contains("大学") && !aiKeyword.contains("学院")) {
                        log.warn("⚠️ AI 返回的关键词可疑：{}, 原始消息：{}, 将使用 fallback 逻辑", aiKeyword, message);
                        intent = fallbackIntent(message);
                        intent.setSessionId(sessionId);
                    }
                }
            }
            
            // 补充位置信息
            intent.setLat(lat);
            intent.setLng(lng);
            
            // ========== 关键修复：使用 finalType 确保类型不会被覆盖 ==========
            String finalType = intent.getType();
            String finalKeyword = intent.getKeyword();
            
            // 双重确认：如果 finalType 是 CHAT 但 message 包含地点词，再次强制 SEARCH
            if ("CHAT".equals(finalType) && message != null && !message.trim().isEmpty()) {
                boolean isSimpleSearch = message.length() <= 6 && 
                    (message.contains("医院") || message.contains("药店") || message.contains("餐厅") ||
                     message.contains("酒店") || message.contains("超市") || message.contains("银行") ||
                     message.contains("加油站") || message.contains("商场") || message.contains("地铁") ||
                     message.contains("大学") || message.contains("学院") || message.contains("学校") ||
                     message.contains("火车站") || message.contains("机场"));
                
                if (isSimpleSearch) {
                    log.warn("🔧 二次修正：简单地点词强制 SEARCH: {}", message);
                    finalType = "SEARCH";
                    finalKeyword = message;
                }
            }
            
            log.info("✅ 最终意图：type={}, keyword={}", finalType, finalKeyword);
            
            // ④ 执行意图（使用 finalType 和 finalKeyword，同时传递原始 message）
            ExecutionResult execResult = executeIntentWithFinalType(sessionId, userId, finalType, finalKeyword, message, lat, lng);
            
            // 状态流转：根据结果更新状态
            if (execResult.getPlaces() != null && !execResult.getPlaces().isEmpty()) {
                intent.setCurrentState(AgentState.DEST_PARSED);
                log.info("【状态流转】sessionId={}, state=DEST_PARSED, poiCount={}", sessionId, execResult.getPlaces().size());
                
                // 保存候选 POI
                memoryService.saveCandidates(sessionId, execResult.getPlaces());
                
                // 删除自动下单，始终等待用户确认
                intent.setCurrentState(AgentState.WAIT_CONFIRM);
                log.info("【状态流转】sessionId={}, state=WAIT_CONFIRM (等待用户确认)", sessionId);
                
                // 构建搜索成功响应
                String searchMessage = buildSearchGuideMessage(execResult.getPlaces());
                AgentResponse response = AgentResponse.successSearch(execResult.getPlaces(), searchMessage);
                // 【关键修复】保存AI回复到记忆，支持多轮对话上下文
                memoryService.saveMessage(sessionId, "assistant", response.getMessage());
                return response;
            } else if (execResult.getOrderData() != null) {
                // 【关键修复】检查是否为 CONFIRM 返回的确认数据（Map类型）
                if (execResult.getOrderData() instanceof Map) {
                    Map<String, Object> confirmData = (Map<String, Object>) execResult.getOrderData();
                    String confirmMessage = (String) confirmData.get("message");
                    
                    // 保持 WAIT_CONFIRM 状态，等待用户说"下单"
                    intent.setCurrentState(AgentState.WAIT_CONFIRM);
                    log.info("【状态流转】sessionId={}, state=WAIT_CONFIRM (等待用户明确下单)", sessionId);
                    
                    AgentResponse response = AgentResponse.successChat(confirmMessage);
                    memoryService.saveMessage(sessionId, "assistant", response.getMessage());
                    return response;
                }
                
                // 真正的订单数据
                intent.setCurrentState(AgentState.ORDER_CREATED);
                log.info("【状态流转】sessionId={}, state=ORDER_CREATED", sessionId);
                
                // 构建订单成功响应
                AgentResponse response = AgentResponse.successOrder(execResult.getOrderData(), "订单创建成功");
                // 【关键修复】保存AI回复到记忆
                memoryService.saveMessage(sessionId, "assistant", response.getMessage());
                return response;
            } else {
                // 聊天响应
                AgentResponse response = AgentResponse.successChat(execResult.getMessage());
                // 【关键修复】保存AI回复到记忆（包括CHAT类型）
                memoryService.saveMessage(sessionId, "assistant", response.getMessage());
                return response;
            }
            
        } catch (Exception e) {
            log.error("处理用户请求失败", e);
            return AgentResponse.error("处理失败：" + e.getMessage());
        }
    }

    /**
     * AI 意图识别（通义千问）
     */
    private AgentIntent parseIntentWithAI(String message) {
        try {
            AgentIntent intent = tongyiClient.parseIntent(message);
            
            if (intent != null) {
                intent.setRawText(message);
                
                String type = intent.getType();
                if (type != null && !type.equals("SEARCH") && !type.equals("ORDER") && !type.equals("CONFIRM") && !type.equals("CHAT")) {
                    intent.setType("SEARCH");
                    if (intent.getKeyword() == null || intent.getKeyword().isEmpty()) {
                        intent.setKeyword(type);
                    }
                    intent.setNeedSearch(true);
                }
                
                // 新增：防污染校验
                String aiKeyword = intent.getKeyword();
                if (aiKeyword != null && isKeywordPolluted(aiKeyword, message)) {
                    log.warn("⚠️ 检测到 AI 关键词污染：[{}]，尝试二次清洗", aiKeyword);
                    String cleanedKeyword = smartExtract(message);
                    if (cleanedKeyword != null && !cleanedKeyword.isEmpty()) {
                        intent.setKeyword(cleanedKeyword);
                        log.info("✅ 关键词二次清洗成功：{} -> {}", aiKeyword, cleanedKeyword);
                    }
                }
                
                log.info("✅ AI 解析成功：{}", intent.getType());
                return intent;
            }
        } catch (Exception e) {
            log.warn("AI 解析异常：{}", e.getMessage());
        }
        
        return null;
    }

    /**
     * 判断关键词是否被污染（包含口语杂质）
     */
    private boolean isKeywordPolluted(String keyword, String rawMessage) {
        if (keyword == null || keyword.isEmpty()) return false;
        
        // 1. 包含常见的口语动词或语气词
        String pollutionPattern = ".*(我想去 | 带我到 | 请问 | 怎么走 | 有没有 | 的那个 | 帮我找 | 带我去 | 麻烦搜 | 要去).*";
        if (keyword.matches(pollutionPattern)) {
            return true;
        }
        
        // 2. 如果关键词在原句中找不到完整匹配（且不是分类词）
        if (!rawMessage.contains(keyword.trim()) && 
            !keyword.equals("医院") && !keyword.equals("药店") && 
            !keyword.equals("餐厅") && !keyword.equals("酒店") &&
            !keyword.equals("超市") && !keyword.equals("银行") &&
            !keyword.equals("加油站") && !keyword.equals("商场") &&
            !keyword.equals("地铁站") && !keyword.equals("地铁")) {
            return true;
        }
        
        // 3. 检查是否包含了明显的非地名后缀
        if (keyword.endsWith("的地方") || keyword.endsWith("的位置") || 
            keyword.endsWith("在哪") || keyword.endsWith("怎么走")) {
            return true;
        }
        
        // 4. 长度异常（超过 30 个字符可能是整句话）
        if (keyword.length() > 30) {
            log.warn("关键词过长，可能是整句话：{}", keyword);
            return true;
        }
        
        return false;
    }

    /**
     * 智能提取关键词（暴力清洗版）
     */
    private String smartExtract(String message) {
        String msg = message.toLowerCase();
        
        // 优先匹配知名地标
        for (String landmark : FAMOUS_LANDMARKS) {
            if (msg.contains(landmark)) {
                return landmark;
            }
        }
        
        // 匹配通用类别词
        String[] categories = {"医院", "药店", "餐厅", "酒店", "超市", "银行", "加油站", "商场", "地铁站", "大学", "学院"};
        for (String category : categories) {
            if (msg.contains(category)) {
                return category;
            }
        }
        
        // 提取"去"字后面的内容
        int quIndex = msg.indexOf("去");
        if (quIndex >= 0 && quIndex < msg.length() - 1) {
            String afterQu = msg.substring(quIndex + 1).trim();
            // 去除语气词
            afterQu = afterQu.replaceAll("那个 | 那个 | 吧 | 啊 | 呀 | 哦$", "");
            if (!afterQu.isEmpty() && afterQu.length() <= 20) {
                return afterQu;
            }
        }
        
        // Fallback: 使用 cleanKeyword 处理 AI 返回的词
        return null;
    }

    /**
     * Fallback 本地意图识别（兜底）
     */
    private AgentIntent fallbackIntent(String message) {
        AgentIntent intent = new AgentIntent();
        intent.setRawText(message);

        String msg = message.toLowerCase();
        String keyword = null;

        // 简单关键词匹配
        if (msg.contains("医院")) {
            keyword = "医院";
        } else if (msg.contains("酒店") || msg.contains("宾馆")) {
            keyword = "酒店";
        } else if (msg.contains("餐") || msg.contains("吃") || msg.contains("饭店")) {
            keyword = "餐厅";
        } else if (msg.contains("超市") || msg.contains("便利店")) {
            keyword = "超市";
        } else if (msg.contains("银行") || msg.contains("atm")) {
            keyword = "银行";
        } else if (msg.contains("药店") || msg.contains("药房")) {
            keyword = "药店";
        } else if (msg.contains("加油站") || msg.contains("加油")) {
            keyword = "加油站";
        } else if (msg.contains("商场") || msg.contains("购物中心")) {
            keyword = "商场";
        } else if (msg.contains("地铁") || msg.contains("火车站") || msg.contains("高铁站")) {
            keyword = "地铁站";
        } else if (msg.contains("去")) {
            keyword = extractDestinationFromUser(message);
        }
        
        // 新增：知名地标关键词强制识别（优先级最高）
        if (msg.contains("故宫")) {
            keyword = "故宫";
        } else if (msg.contains("长城")) {
            keyword = "长城";
        } else if (msg.contains("天安门")) {
            keyword = "天安门";
        } else if (msg.contains("东方明珠")) {
            keyword = "东方明珠";
        } else if (msg.contains("西湖")) {
            keyword = "西湖";
        } else if (msg.contains("黄山")) {
            keyword = "黄山";
        } else if (msg.contains("兵马俑")) {
            keyword = "兵马俑";
        } else if (msg.contains("布达拉宫")) {
            keyword = "布达拉宫";
        } else if (msg.contains("颐和园")) {
            keyword = "颐和园";
        } else if (msg.contains("天坛")) {
            keyword = "天坛";
        } else if (msg.contains("北京大学")) {
            keyword = "北京大学";
        } else if (msg.contains("清华大学")) {
            keyword = "清华大学";
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            intent.setType("SEARCH");
            intent.setKeyword(keyword);
            intent.setNeedSearch(true);
        } else {
            intent.setType("CHAT");
        }

        return intent;
    }

    /**
     * 统一意图执行入口（使用 finalType 和 finalKeyword）
     */
    private ExecutionResult executeIntentWithFinalType(String sessionId, Long userId, String finalType, String finalKeyword, String originalMessage, Double lat, Double lng) {
        
        switch (finalType) {
            case "SEARCH":
                // 创建临时 intent 用于搜索
                AgentIntent searchIntent = new AgentIntent();
                searchIntent.setType("SEARCH");
                searchIntent.setKeyword(finalKeyword);
                searchIntent.setNeedSearch(true);
                searchIntent.setLat(lat);
                searchIntent.setLng(lng);
                List<PoiDTO> places = handleSearch(sessionId, userId, searchIntent, lat, lng);
                return new ExecutionResult(places);
                
            case "ORDER":
                // 【关键修复】检查是否有待确认的 POI
                Map<String, Object> pendingConfirm = memoryService.getPendingOrder(sessionId);
                if (pendingConfirm != null && pendingConfirm.containsKey("selectedPoi")) {
                    PoiDTO selectedPoi = (PoiDTO) pendingConfirm.get("selectedPoi");
                    log.info("✅ 检测到待确认订单，直接下单：{}", selectedPoi.getName());
                    return new ExecutionResult(handleOrder(userId, selectedPoi.getName(), selectedPoi.getLat(), selectedPoi.getLng()));
                }
                
                // 普通 ORDER 意图（用户直接说“帮我叫车去XX”）
                return new ExecutionResult(handleOrder(userId, finalKeyword, lat, lng));
                
            case "CONFIRM":
                return new ExecutionResult(handleConfirm(sessionId, userId, finalKeyword));
                
            case "CHAT":
                // 【关键修复】调用真正的 AI 对话，使用原始消息而非 keyword
                String chatMessage = (finalKeyword != null && !finalKeyword.trim().isEmpty()) ? finalKeyword : originalMessage;
                String chatResponse = generateChatResponse(sessionId, chatMessage);
                return new ExecutionResult(chatResponse != null ? chatResponse : "你好！我可以帮你查找地点、规划路线或叫车出行。请告诉我你的需求~");
                
            default:
                // 【关键修复】未知类型也走 AI 对话，使用原始消息
                String fallbackMessage = (finalKeyword != null && !finalKeyword.trim().isEmpty()) ? finalKeyword : originalMessage;
                String fallbackResponse = generateChatResponse(sessionId, fallbackMessage);
                return new ExecutionResult(fallbackResponse != null ? fallbackResponse : "我可以帮你找医院/餐厅/酒店，请告诉我目的地");
        }
    }

    /**
     * 统一意图执行入口
     * @return ExecutionResult 包含 places、orderData 或 message
     */
    private ExecutionResult executeIntent(String sessionId, Long userId, AgentIntent intent, Double lat, Double lng) {
        
        switch (intent.getType()) {
            case "SEARCH":
                List<PoiDTO> places = handleSearch(sessionId, userId, intent, lat, lng);
                return new ExecutionResult(places);
                
            case "ORDER":
                Object orderData = handleOrder(userId, intent.getKeyword(), lat, lng);
                return new ExecutionResult(orderData);
                
            case "CONFIRM":
                // 处理用户确认选择
                Object confirmResult = handleConfirm(sessionId, userId, intent.getKeyword());
                return new ExecutionResult(confirmResult);
                
            default:
                return new ExecutionResult("我可以帮你找医院/餐厅/酒店，请告诉我目的地");
        }
    }

    /**
     * 处理搜索意图（返回 POI 列表）
     * 全链路：关键词清洗 -> 策略分配 -> API 调用 -> 防火墙过滤 -> 排序 -> 路线计算
     */
    private List<PoiDTO> handleSearch(String sessionId, Long userId, AgentIntent intent, Double lat, Double lng) {
        
        // 声明为 final 以便 lambda 表达式使用
        final Double currentLat;
        final Double currentLng;
        
        if (lat == null || lng == null) {
            log.warn("⚠️ 搜索缺少坐标，尝试从缓存获取用户位置...");
            
            // 1. 先从缓存中获取上次保存的位置
            double[] cachedLocation = memoryService.getLocation(sessionId);
            if (cachedLocation != null) {
                currentLat = cachedLocation[0];
                currentLng = cachedLocation[1];
                log.info("✅ 从缓存获取位置：lat={}, lng={}", currentLat, currentLng);
            } else {
                // 2. 缓存没有，尝试 IP 定位
                log.warn("⚠️ 缓存无位置，尝试 IP 定位...");
                double[] ipLocation = getIpLocation();
                if (ipLocation != null) {
                    currentLat = ipLocation[0];
                    currentLng = ipLocation[1];
                    log.info("✅ IP 定位成功：lat={}, lng={}", currentLat, currentLng);
                    
                    // 保存到缓存，下次可以直接使用
                    memoryService.saveLocation(sessionId, currentLat, currentLng);
                } else {
                    log.error("❌ IP 定位失败，无法获取用户位置");
                    throw new RuntimeException("无法获取您的位置信息，请在地图上手动选择起点，或允许浏览器获取您的定位");
                }
            }
        } else {
            // 前端传递了坐标，直接使用并缓存
            currentLat = lat;
            currentLng = lng;
            log.debug("✅ 使用前端传递的坐标：lat={}, lng={}", currentLat, currentLng);
            
            // 缓存位置信息
            memoryService.saveLocation(sessionId, currentLat, currentLng);
        }

        // ========== 第一阶段：关键词清洗 + 纠偏 ==========
        String rawKeyword = intent.getKeyword();
        String cleanedKeyword = cleanKeyword(rawKeyword);
        
        if (cleanedKeyword == null || cleanedKeyword.trim().isEmpty()) {
            throw new RuntimeException("未能识别有效目的地，请再说具体一点");
        }
        
        // 新增：搜索词纠偏（热点映射）
        final String correctedKeyword = correctKeyword(cleanedKeyword);
        if (!correctedKeyword.equals(cleanedKeyword)) {
            log.info("🔧 触发搜索词纠偏：{} -> {}", cleanedKeyword, correctedKeyword);
        }
        
        log.info("🔍 开始搜索：rawKeyword={}, cleanedKeyword={}, correctedKeyword={}, lat={}, lng={}", 
                rawKeyword, cleanedKeyword, correctedKeyword, currentLat, currentLng);
        
        // ========== 第二阶段：调用搜索策略引擎（工业版） ==========
        log.info("🚀 使用搜索策略引擎 V1 进行智能搜索...");
        List<PoiDTO> poiList = searchStrategyEngine.search(correctedKeyword, currentLat, currentLng);
        
        // 【关键】跟踪实际用于排序的关键词（可能是 correctedKeyword 或 cleanedKeyword）
        String actualSortKeyword = correctedKeyword;
        
        // ========== 第三阶段：POI 过滤（弱过滤模式，只过滤脏数据） ==========
        log.info("🔍 开始 POI 过滤（弱过滤模式，只过滤黑名单）...");
        poiList = filterPoiList(poiList, correctedKeyword);
        
        // 新增：如果过滤后结果为空，记录警告（不再重新搜索，避免重复调用）
        if (poiList.isEmpty()) {
            log.warn("⚠️ 过滤后结果为 0，将直接返回空列表");
        }
        
        // ========== 第四阶段：防火墙过滤结果检查 ==========
        if (poiList == null || poiList.isEmpty()) {
            log.error("❌ 搜索结果为空！rawKeyword={}, cleanedKeyword={}, correctedKeyword={}, lat={}, lng={}", 
                     rawKeyword, cleanedKeyword, correctedKeyword, currentLat, currentLng);
            
            // 特殊处理：如果是知名地标，给出明确提示
            boolean isFamous = FAMOUS_LANDMARKS.contains(correctedKeyword);
            if (isFamous) {
                log.error("💡 知名地标 '{}' 在全国范围内都未找到，这可能是高德地图数据限制", correctedKeyword);
                log.error("建议：告知用户系统当前仅支持广东省内搜索，或建议使用其他方式查询");
                throw new RuntimeException(String.format(
                    "抱歉，暂时无法找到'%s'的相关信息。该地点可能超出当前服务范围，建议尝试搜索广东省内的地点", 
                    correctedKeyword));
            }
            
            // 新增：零结果降级反馈逻辑（Did you mean?）
            if (!correctedKeyword.equals(cleanedKeyword)) {
                log.warn("💡 触发零结果降级反馈：是否要搜索原始词 '{}'", cleanedKeyword);
                // 尝试使用原始清洗后的词再次搜索
                poiList = executeSearchStrategy(cleanedKeyword, currentLat, currentLng);
                if (poiList != null && !poiList.isEmpty()) {
                    log.info("✅ 降级反馈成功：使用原始词 '{}' 找到 {} 个地点", cleanedKeyword, poiList.size());
                    // 【关键修复】降级搜索结果也需要过滤黑名单
                    poiList = filterPoiList(poiList, cleanedKeyword);
                    log.info("✅ 降级反馈过滤完成：{} -> {} 个地点", poiList.size(), poiList.size());
                    // 【关键】更新排序关键词为实际搜索的词
                    actualSortKeyword = cleanedKeyword;
                }
            }
            
            if (poiList == null || poiList.isEmpty()) {
                return Collections.emptyList();
            }
        }

        log.info("✅ 搜索完成，找到 {} 个地点", poiList.size());
        
        // ========== 第四阶段：为每个 POI 生成唯一 ID ==========
        for (int i = 0; i < poiList.size(); i++) {
            PoiDTO poi = poiList.get(i);
            poi.setId(UUID.randomUUID().toString());
        }
        
        // 创建 final 引用以便 lambda 表达式使用
        final String finalSortKeyword = actualSortKeyword;
        
        // ========== 第五阶段：智能排序（带同城权重） ==========
        poiList = sortAndRankPoiListWithLocation(poiList, finalSortKeyword, currentLat, currentLng);
        double topScore = computeRelevanceScore(poiList.get(0), finalSortKeyword, currentLat, currentLng);
        log.info("智能排序完成，最佳匹配：{} (得分：{})", poiList.get(0).getName(), topScore);
        
        // 打印所有候选地点的详细信息
        log.info("============ 候选地点列表 ============");
        for (int i = 0; i < poiList.size(); i++) {
            PoiDTO poi = poiList.get(i);
            log.info("候选#{}: id={}, name={}, address={}, distance={}m, score={}", 
                    i + 1, poi.getId(), poi.getName(), poi.getAddress(), poi.getDistance(), computeRelevanceScore(poi, finalSortKeyword, currentLat, currentLng));
        }
        log.info("======================================");

        // ========== 第六阶段：并行路线计算与价格预估（使用自定义线程池） ==========
        // 创建 final 引用以便 lambda 表达式使用
        final List<PoiDTO> finalPoiList = poiList;
        
        // 使用自定义线程池执行并行任务
        java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
            finalPoiList.forEach(poi -> {
                try {
                    // 【关键修复】腾讯地图 API 要求坐标格式：lat,lng（纬度在前）
                    String origin = String.format("%.6f,%.6f", currentLat, currentLng);  // lat,lng
                    String destination = String.format("%.6f,%.6f", poi.getLat(), poi.getLng());  // lat,lng
                    log.debug("🗺️ 计算路线：origin={}, destination={}", origin, destination);
                    com.anxin.travel.module.map.dto.RouteResult route = tencentMapClient.getRoute(origin, destination);  // 使用腾讯地图
                    // 修复：不污染 address 字段，使用独立字段存储路线信息
                    if (route != null) {
                        poi.setDuration(route.getDuration());
                        poi.setPrice(route.getPrice());
                    }
                } catch (Exception e) {
                    log.warn("❌ 计算路线失败：{}", poi.getName(), e);
                }
            });
        }, businessExecutor);
        
        // 等待任务完成（最多等待 10 秒）
        try {
            future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            log.info("✅ 并行路线计算完成");
        } catch (Exception e) {
            log.error("并行路线计算超时或失败", e);
        }

        // 新增：多候选地点二次确认逻辑
        if (poiList.size() > 1) {
            long highScoreCount = poiList.stream()
                .filter(poi -> computeRelevanceScore(poi, finalSortKeyword, currentLat, currentLng) > 0.6)
                .count();
            
            if (highScoreCount > 1) {
                log.info("检测到 {} 个高相关性候选地点，需要前端二次确认", highScoreCount);
            }
        }

        return poiList;
    }

    /**
     * 抽取出的独立搜索策略引擎（增强版）- 腾讯地图优先
     */
    private List<PoiDTO> executeSearchStrategy(String cleanedKeyword, Double lat, Double lng) {
        log.info("🚀 执行搜索策略（腾讯地图优先）：keyword={}, location=({}, {})", cleanedKeyword, lat, lng);
        
        // 直接调用搜索策略引擎（已经内置腾讯地图优先逻辑）
        return searchStrategyEngine.search(cleanedKeyword, lat, lng);
    }
    
    /**
     * 格式化评分为 3 位小数
     */
    private String formatScore(double score) {
        return String.format("%.3f", score);
    }
    
    /**
     * 【图片识别专用】计算 POI 与 OCR 文字的匹配分数
     * 核心原则：OCR 识别出的地址必须排第一
     * @param poiName POI 名称
     * @param poiAddress POI 地址
     * @param ocrText OCR 识别的文字
     * @return 匹配分数（越高越匹配）
     */
    private int calculateImageMatchScore(String poiName, String poiAddress, String ocrText) {
        if (poiName == null || ocrText == null || ocrText.isEmpty()) {
            return 0;
        }
        
        String cleanName = poiName.replaceAll("\\s+", "").toLowerCase();
        String cleanAddress = poiAddress != null ? poiAddress.replaceAll("\\s+", "").toLowerCase() : "";
        String cleanOcr = ocrText.replaceAll("\\s+", "").toLowerCase();
        
        // 1. 名称完全等于 OCR 文字（最高优先级）
        if (cleanName.equals(cleanOcr)) {
            return 1000;
        }
        
        // 2. 名称包含 OCR 文字（OCR 是名称的一部分）
        if (cleanName.contains(cleanOcr)) {
            return 800;
        }
        
        // 3. OCR 文字包含名称（名称是 OCR 的简称）
        if (cleanOcr.contains(cleanName) && cleanName.length() >= 2) {
            return 700;
        }
        
        // 4. 地址包含 OCR 文字
        if (cleanAddress.contains(cleanOcr)) {
            return 600;
        }
        
        // 5. OCR 文字包含地址关键字
        if (!cleanAddress.isEmpty() && cleanOcr.contains(cleanAddress)) {
            return 500;
        }
        
        // 6. 名称和地址都包含 OCR 的核心词（2-4 个字）
        if (cleanOcr.length() >= 2) {
            String coreWord = cleanOcr.length() > 4 ? cleanOcr.substring(0, 4) : cleanOcr;
            boolean nameContains = cleanName.contains(coreWord);
            boolean addrContains = cleanAddress.contains(coreWord);
            
            if (nameContains && addrContains) {
                return 400;
            } else if (nameContains) {
                return 300;
            } else if (addrContains) {
                return 200;
            }
        }
        
        // 7. 完全不匹配
        return 0;
    }

    /**
     * 判断是否为通用类别词（如：医院、酒店、餐厅等）
     */
    private boolean isCategoryKeyword(String keyword) {
        String[] categories = {"医院", "药店", "餐厅", "酒店", "超市", "银行", "加油站", 
                               "商场", "地铁站", "地铁", "火车站", "学校", "大学", "中学", "小学"};
        for (String category : categories) {
            if (keyword.equals(category) || keyword.contains(category)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取高德地图 POI type 编码（用于精确过滤）
     * @param keyword 关键词
     * @return POI type 编码，null 表示不限制
     */
    private String getAmapType(String keyword) {
        switch (keyword) {
            case "医院": return "090000";      // 医疗卫生服务
            case "餐厅": return "050000";      // 餐饮服务
            case "酒店": return "100000";      // 住宿服务
            case "银行": return "160000";      // 银行 ATM
            case "超市": return "060000";      // 购物服务
            case "加油站": return "010100";    // 加油站
            case "药店": return "090300";      // 药店
            case "商场": return "060100";      // 商场
            case "地铁站": return "150500";    // 轨道交通
            case "学校": 
            case "大学": 
            case "中学": 
            case "小学": return "140000";      // 科教文化服务
            default: return null;              // 不限制类型
        }
    }
    
    /**
     * 搜索词纠偏（热点映射）
     */
    private String correctKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return keyword;
        }
        
        // 精确匹配
        String corrected = KEYWORD_CORRECTION_MAP.get(keyword);
        if (corrected != null) {
            return corrected;
        }
        
        // 模糊匹配（包含关键词）
        for (Map.Entry<String, String> entry : KEYWORD_CORRECTION_MAP.entrySet()) {
            if (keyword.contains(entry.getKey())) {
                // 将原词中的简称替换为标准名
                return keyword.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return keyword; // 无需纠偏
    }

    /**
     * 对 POI 列表进行智能排序（优化比较器的计分性能）
     */
    private List<PoiDTO> sortAndRankPoiList(List<PoiDTO> poiList, String keyword) {
        return poiList.stream()
            .sorted(Comparator.comparingDouble((PoiDTO poi) -> computeRelevanceScore(poi, keyword)).reversed())
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 带坐标的排序方法（支持同城权重）
     */
    private List<PoiDTO> sortAndRankPoiListWithLocation(List<PoiDTO> poiList, String keyword, Double userLat, Double userLng) {
        return poiList.stream()
            .sorted(Comparator.comparingDouble((PoiDTO poi) -> computeRelevanceScore(poi, keyword, userLat, userLng)).reversed())
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 计算单个 POI 的相关性得分
     * 核心优化：名称匹配 > 类型匹配 > 距离（防止理发店排在大学前面）
     */
    private double computeRelevanceScore(PoiDTO poi, String keyword, Double userLat, Double userLng) {
        // 判断是否为知名地标
        boolean isFamous = FAMOUS_LANDMARKS.contains(keyword);
        
        // 1. 距离分数计算（权重最低，仅作为辅助因素）
        double distanceScore = 1.0 / (1.0 + poi.getDistance() / 1000.0);
        
        // 2. 名称匹配度检查（权重最高）
        double nameMatchScore = 0.0;
        if (poi.getName().equals(keyword)) {
            nameMatchScore = 1.0;  // 完全匹配
        } else if (poi.getName().contains(keyword)) {
            // 进一步检查包含的质量
            int index = poi.getName().indexOf(keyword);
            if (index == 0) {
                nameMatchScore = 0.9;  // 开头包含，质量高
            } else {
                nameMatchScore = 0.7;  // 中间包含，质量一般
            }
        } else if (poi.getAddress() != null && poi.getAddress().contains(keyword)) {
            nameMatchScore = 0.3;  // 仅地址包含，质量低
        } else if (keyword.contains(poi.getName()) && poi.getName().length() >= 3) {
            nameMatchScore = 0.4;  // 关键词包含 POI 名
        }
        
        // 3. 类型匹配度检查（新增，中等权重）
        double typeMatchScore = calculateTypeMatchScore(poi, keyword);
        
        // 基础得分计算（优化权重分配）
        double baseScore;
        if (isFamous) {
            // 知名地标：名称权重 0.8，类型权重 0.15，距离权重 0.05
            baseScore = 0.8 * nameMatchScore + 0.15 * typeMatchScore + 0.05 * distanceScore;
        } else {
            // 普通地点：名称权重 0.5，类型权重 0.3，距离权重 0.2
            baseScore = 0.5 * nameMatchScore + 0.3 * typeMatchScore + 0.2 * distanceScore;
        }
        
        // 新增：同城权重加成（通过逆地理编码判断是否同城）
        double locationWeight = calculateLocationWeight(poi, userLat, userLng);
        
        return baseScore * locationWeight;
    }
    
    /**
     * 计算 POI 类型匹配度得分
     * @param poi POI 对象
     * @param keyword 搜索关键词
     * @return 类型匹配得分（0.0-1.0）
     */
    private double calculateTypeMatchScore(PoiDTO poi, String keyword) {
        if (poi == null || keyword == null) {
            return 0.0;
        }
        
        // 获取高德返回的 POI 类型信息（如果有）
        // 注意：PoiDTO 目前没有 type 字段，这里通过名称和地址进行简单推断
        
        String name = poi.getName().toLowerCase();
        String address = poi.getAddress() != null ? poi.getAddress().toLowerCase() : "";
        String fullText = name + " " + address;
        
        // 根据关键词判断期望的类型
        switch (keyword) {
            case "医院":
                if (fullText.contains("医院") || fullText.contains("卫生") || fullText.contains("医疗")) {
                    return 1.0;
                }
                break;
            case "餐厅":
                if (fullText.contains("酒家") || fullText.contains("食府") || fullText.contains("餐馆") || 
                    fullText.contains("酒楼") || fullText.contains("菜馆") || fullText.contains("餐厅")) {
                    return 1.0;
                }
                break;
            case "酒店":
                if (fullText.contains("酒店") || fullText.contains("宾馆") || fullText.contains("旅馆") || 
                    fullText.contains("住宿") || fullText.contains("客栈")) {
                    return 1.0;
                }
                break;
            case "银行":
                if (fullText.contains("银行") || fullText.contains("信用社") || fullText.contains("ATM")) {
                    return 1.0;
                }
                break;
            case "超市":
                if (fullText.contains("超市") || fullText.contains("商场") || fullText.contains("市场") || 
                    fullText.contains("百货") || fullText.contains("便利店")) {
                    return 1.0;
                }
                break;
            case "药店":
                if (fullText.contains("药店") || fullText.contains("药房") || fullText.contains("医药")) {
                    return 1.0;
                }
                break;
            case "加油站":
                if (fullText.contains("加油站") || fullText.contains("加油")) {
                    return 1.0;
                }
                break;
            case "学校":
            case "大学":
            case "中学":
            case "小学":
                if (fullText.contains("学校") || fullText.contains("学院") || fullText.contains("大学") || 
                    fullText.contains("中学") || fullText.contains("小学") || fullText.contains("教育")) {
                    return 1.0;
                }
                break;
        }
        
        // 如果无法判断或不符合，给基础分 0.5
        return 0.5;
    }
    
    /**
     * 简化版：不传坐标的兼容方法
     */
    private double computeRelevanceScore(PoiDTO poi, String keyword) {
        return computeRelevanceScore(poi, keyword, null, null);
    }
    
    /**
     * 计算位置权重系数（同城加成）
     * @return 同城返回 1.2，异地返回 1.0
     */
    private double calculateLocationWeight(PoiDTO poi, Double userLat, Double userLng) {
        if (userLat == null || userLng == null) {
            return 1.0; // 无坐标信息，不加成
        }
        
        // 简单判断：如果 POI 距离用户 < 100km，认为是同城
        if (poi.getDistance() < 100000) { // 100km
            return 1.2; // 同城权重加成 20%
        }
        
        return 1.0; // 异地不加权
    }

    /**
     * 清洗关键词，去除括号及其内容、冗余后缀，并处理拼音缩写
     */
    private String cleanKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return "";
        }
        
        // 0. 先检查是否是拼音缩写（纯小写字母）
        if (keyword.matches("[a-z]+") && keyword.length() >= 2 && keyword.length() <= 10) {
            log.info("检测到拼音缩写：{}, 尝试解析...", keyword);
            String expanded = expandPinyinAbbreviation(keyword);
            if (expanded != null && !expanded.isEmpty()) {
                log.info("拼音缩写解析成功：{} -> {}", keyword, expanded);
                return expanded;
            }
        }
        
        // 1. 去除所有形式的括号及其内容
        String cleaned = keyword.replaceAll("\\(.*?\\)|\\（.*?\\）", "").replaceAll("区 $|号楼$|栋$|门$", "");
        
        // 2. 使用数组遍历替代多层 if-else 嵌套
        String[] coreSuffixes = {"学院", "大学", "医院", "校区"};
        for (String suffix : coreSuffixes) {
            int index = cleaned.indexOf(suffix);
            if (index > 0) {
                cleaned = cleaned.substring(0, index + suffix.length());
                break;
            }
        }
        
        log.info("关键词清洗：[{}] -> [{}]", keyword, cleaned);
        return cleaned.trim();
    }

    /**
     * 展开拼音缩写为标准地名
     */
    private String expandPinyinAbbreviation(String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) {
            return null;
        }
        
        // 常见大学/学校拼音缩写映射表
        Map<String, String> abbreviationMap = new HashMap<>();
        abbreviationMap.put("bjdx", "北京大学");
        abbreviationMap.put("tsdx", "清华大学");
        abbreviationMap.put("rmdx", "人民大学");
        abbreviationMap.put("bjsfdx", "北京师范大学");
        abbreviationMap.put("gzdx", "广州大学");
        abbreviationMap.put("gdsfdx", "广东师范大学");
        abbreviationMap.put("gdwywmdx", "广东外语外贸大学");
        abbreviationMap.put("hnsfdx", "湖南师范大学");
        abbreviationMap.put("hsfsxy", "韩山师范学院");
        abbreviationMap.put("hssyxx", "韩山师范学院");
        abbreviationMap.put("sysdx", "中山大学");
        abbreviationMap.put("hndx", "湖南大学");
        abbreviationMap.put("whdx", "武汉大学");
        abbreviationMap.put("hust", "华中科技大学");
        abbreviationMap.put("tjdx", "天津大学");
        abbreviationMap.put("nkdx", "南开大学");
        abbreviationMap.put("fddx", "复旦大学");
        abbreviationMap.put("sjtu", "上海交通大学");
        abbreviationMap.put("zju", "浙江大学");
        abbreviationMap.put("nju", "南京大学");
        abbreviationMap.put("ustc", "中国科学技术大学");
        
        // 尝试从映射表中查找
        String result = abbreviationMap.get(pinyin.toLowerCase());
        if (result != null) {
            return result;
        }
        
        // 如果映射表中没有，尝试通过 AI 解析
        // （这里可以调用通义千问，但为了性能考虑，暂时返回原词）
        log.warn("未找到拼音缩写 '{}' 的映射，返回原词", pinyin);
        return pinyin; // 返回原词，让后续搜索逻辑处理
    }

    /**
     * 处理订单意图（返回订单信息）
     */
    private Object handleOrder(Long userId, String destName, Double lat, Double lng) {
        
        if (destName == null || destName.trim().isEmpty()) {
            throw new RuntimeException("目的地不能为空");
        }

        if (lat == null || lng == null) {
            throw new RuntimeException("无法获取目的地位置");
        }

        CreateOrderRequest request = new CreateOrderRequest();
        request.setDestLat(lat);
        request.setDestLng(lng);
        request.setDestName(destName.trim());
        // 新增：如果有候选 POI，使用完整地址
        // （地址信息会在 MapController.getPoiDetailAndRoute 中补充）
        
        var orderVO = orderService.createOrder(userId, request);
        
        log.info("✅ 订单创建成功：orderNo={}, destName={}", orderVO.getOrderNo(), destName);
        
        return orderVO;
    }

    /**
     * 处理地图点击下单（新增：支持 sessionId）
     */
    public AgentResponse createOrderFromMapClick(String sessionId, Long userId, String poiName) {
        log.info("🚗 地图点击下单请求：sessionId={}, poiName={}", sessionId, poiName);
        
        // 1. 从内存中获取候选 POI
        List<PoiDTO> candidates = memoryService.getCandidates(sessionId);
        if (candidates == null || candidates.isEmpty()) {
            log.warn("⚠️ 未找到会话数据，可能已过期：sessionId={}", sessionId);
            return AgentResponse.error("会话已过期，请重新选择地点");
        }
        
        // 2. 查找匹配的 POI
        PoiDTO selected = candidates.stream()
            .filter(poi -> poi.getName().equals(poiName))
            .findFirst()
            .orElse(null);
            
        if (selected == null) {
            log.error("❌ 未找到匹配的 POI: name={}, sessionId={}", poiName, sessionId);
            return AgentResponse.error("未找到该地点，请重新选择");
        }
        
        log.info("✅ 找到选中的 POI: name={}, lat={}, lng={}, address={}", 
            selected.getName(), selected.getLat(), selected.getLng(), selected.getAddress());
        
        // 3. 创建订单
        try {
            Object orderResult = handleOrder(userId, selected.getAddress(), selected.getLat(), selected.getLng());
            return AgentResponse.successOrder(orderResult, "订单创建成功");
        } catch (Exception e) {
            log.error("地图点击下单失败", e);
            return AgentResponse.error("下单失败：" + e.getMessage());
        }
    }

    /**
     * 处理用户确认选择
     */
    private Object handleConfirm(String sessionId, Long userId, String selectedPoiName) {
        log.info("用户确认选择：{}", selectedPoiName);
        
        List<PoiDTO> candidates = memoryService.getCandidates(sessionId);
        if (candidates == null || candidates.isEmpty()) {
            return buildErrorResponse("请先搜索目的地");
        }
        
        PoiDTO selected = candidates.stream()
            .filter(poi -> poi.getName().equals(selectedPoiName))
            .findFirst()
            .orElse(null);
            
        if (selected == null) {
            return buildErrorResponse("未找到该地点，请重新选择");
        }
        
        log.info("✅ 用户确认：{} - {}", selected.getName(), selected.getAddress());
        
        // 【关键修复】不直接下单，而是返回确认信息，等待用户明确说"下单"
        String confirmMessage = String.format(
            "已选择 %s，地址：%s\n距离您 %.1f 公里，预计费用 %.0f 元\n\n需要帮您叫车吗？请回复'下单'或'叫车'",
            selected.getName(),
            selected.getAddress(),
            selected.getDistance() / 1000.0,
            selected.getPrice() != null ? selected.getPrice() : 0.0
        );
        
        // 将确认信息保存到候选 POI 的扩展字段，供后续下单使用
        Map<String, Object> confirmData = new HashMap<>();
        confirmData.put("selectedPoi", selected);
        confirmData.put("message", confirmMessage);
        
        // 【关键修复】保存到 Redis，供 ORDER 意图使用
        memoryService.savePendingOrder(sessionId, confirmData);
        
        log.info("⏳ 等待用户确认下单：{}", confirmMessage);
        return confirmData;
    }

    /**
     * 计算两点之间的距离（Haversine 公式）
     * @param lat1 起点纬度
     * @param lng1 起点经度
     * @param lat2 终点纬度
     * @param lng2 终点经度
     * @return 距离（米）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 执行结果封装类
     */
    private static class ExecutionResult {
        private final List<PoiDTO> places;
        private final Object orderData;
        private final String message;

        // 搜索结果的构造器
        public ExecutionResult(List<PoiDTO> places) {
            this.places = places;
            this.orderData = null;
            this.message = null;
        }

        // 订单结果的构造器
        public ExecutionResult(Object orderData) {
            this.orderData = orderData;
            this.places = null;
            this.message = null;
        }

        // 聊天结果的构造器
        public ExecutionResult(String message) {
            this.message = message;
            this.places = null;
            this.orderData = null;
        }

        public List<PoiDTO> getPlaces() {
            return places;
        }

        public Object getOrderData() {
            return orderData;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 构建聊天回复
     */
    private Map<String, Object> buildChatResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        return response;
    }

    /**
     * 构建错误响应
     */
    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    /**
     * 通过高德地图 IP 定位 API 获取用户大致位置
     */
    private double[] getIpLocation() {
        try {
            log.info("调用高德 IP 定位 API...");

            String url = String.format(
                "https://restapi.amap.com/v3/ip?key=%s",
                amapClient.getApiKey()
            );

            org.springframework.http.ResponseEntity<String> response = 
                new org.springframework.web.client.RestTemplate().getForEntity(url, String.class);

            String resp = response.getBody();
            if (resp != null) {
                com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(resp);

                if ("1".equals(json.getString("status"))) {
                    String rectangle = json.getString("rectangle");
                    if (rectangle != null && !rectangle.isEmpty()) {
                        String[] parts = rectangle.split(";");
                        if (parts.length == 2) {
                            String[] lowerLeft = parts[0].split(",");
                            String[] upperRight = parts[1].split(",");

                            double lng1 = Double.parseDouble(lowerLeft[0]);
                            double lat1 = Double.parseDouble(lowerLeft[1]);
                            double lng2 = Double.parseDouble(upperRight[0]);
                            double lat2 = Double.parseDouble(upperRight[1]);

                            double centerLat = (lat1 + lat2) / 2;
                            double centerLng = (lng1 + lng2) / 2;

                            log.info("IP 定位成功：中心坐标=({}, {})", centerLat, centerLng);
                            return new double[]{centerLat, centerLng};
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("IP 定位失败：{}", e.getMessage());
        }

        return null;
    }

    /**
     * 供 Controller 调用的 IP 定位方法（公开版本）
     */
    public double[] getIpLocationForController() {
        return getIpLocation();
    }

    /**
     * 从用户文本中提取目的地
     */
    private String extractDestinationFromUser(String text) {
        if (text == null) return null;

        String[] patterns = {
                "我要去 (.+)",
                "去 (.+)",
                "到 (.+)",
                "打车到 (.+)"
        };

        for (String p : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    /**
     * 处理图片识别请求
     */
    public AgentResponse processImage(String sessionId, Long userId, String imageBase64, Double lat, Double lng) {
        return processImageInternal(sessionId, userId, java.util.Collections.singletonList(imageBase64), lat, lng, false);
    }
    
    /**
     * 批量处理多张图片（让 AI 理解图片间的关系）
     */
    public AgentResponse processBatchImages(String sessionId, Long userId, List<String> imageBase64List, Double lat, Double lng) {
        if (imageBase64List == null || imageBase64List.isEmpty()) {
            return AgentResponse.error("没有图片数据");
        }
        return processImageInternal(sessionId, userId, imageBase64List, lat, lng, true);
    }
    
    /**
     * 内部方法：处理单张或多张图片
     * @param isBatch 是否为批量模式
     */
    private AgentResponse processImageInternal(String sessionId, Long userId, List<String> imageBase64List, Double lat, Double lng, boolean isBatch) {
        try {
            log.info("【图片识别】sessionId={}, 图片数量={}, isBatch={}", sessionId, imageBase64List.size(), isBatch);
            
            // ⭐ 批量模式：对所有图片进行 OCR 和描述
            StringBuilder allOcrText = new StringBuilder();
            StringBuilder allImageDesc = new StringBuilder();
            
            for (int i = 0; i < imageBase64List.size(); i++) {
                String imageBase64 = imageBase64List.get(i);
                
                // OCR 提取文字
                String extractedText = imageRecognitionService.extractTextFromImage(imageBase64);
                log.info("【OCR 结果 - 图片{}】{}", i + 1, extractedText);
                
                // 图片描述
                String imageDescription = imageRecognitionService.describeImage(imageBase64);
                log.info("🖼️ 【图片描述 - 图片{}】{}", i + 1, imageDescription);
                
                // 拼接所有图片的文本
                if (i > 0) {
                    allOcrText.append("\n\n--- 图片" + (i + 1) + " ---\n");
                    allImageDesc.append("\n\n--- 图片" + (i + 1) + " ---\n");
                }
                
                if (extractedText != null && !extractedText.trim().isEmpty() && !extractedText.contains("未识别到")) {
                    allOcrText.append(extractedText);
                }
                
                if (imageDescription != null && !imageDescription.equals("一张图片")) {
                    allImageDesc.append(imageDescription);
                }
            }
            
            String extractedText = allOcrText.toString();
            String imageDescription = allImageDesc.toString();
            
            log.info("🧠 合并后的 OCR 文本长度：{}", extractedText.length());
            log.info("🧠 合并后的图片描述长度：{}", imageDescription.length());
            
            // ③ 【关键修复】构建完整的图片理解文本（OCR + 图片描述）
            String fullImageUnderstanding = "";
            if (extractedText != null && !extractedText.trim().isEmpty() && !extractedText.contains("未识别到")) {
                fullImageUnderstanding += "图片中的文字：" + extractedText;
            }
            if (imageDescription != null && !imageDescription.equals("一张图片")) {
                if (!fullImageUnderstanding.isEmpty()) {
                    fullImageUnderstanding += "\n";
                }
                fullImageUnderstanding += "图片内容描述：" + imageDescription;
            }
            
            // 如果既没有OCR文字也没有图片描述，给出友好提示
            if (fullImageUnderstanding.isEmpty()) {
                fullImageUnderstanding = "用户上传了一张图片，但未能识别出具体内容";
            }
            
            log.info("🧠 完整图片理解：{}", fullImageUnderstanding);
            
            // ④ 保存图片理解到记忆（支持后续多轮对话）
            memoryService.saveMessage(sessionId, "user", "[图片] " + fullImageUnderstanding);
            log.info("💾 图片已保存到记忆");
            memoryService.saveLocation(sessionId, lat, lng);
            
            // ⑤ 【关键修复】判断是否为地址类图片
            boolean isAddressImage = false;
            
            if (extractedText != null && !extractedText.trim().isEmpty() && !extractedText.contains("未识别到")) {
                // 1. 包含明确的地址关键词
                boolean hasAddressKeyword = extractedText.contains("路") || extractedText.contains("街") 
                    || extractedText.contains("号") || extractedText.contains("市")
                    || extractedText.contains("区") || extractedText.contains("省")
                    || extractedText.contains("县") || extractedText.contains("镇")
                    || extractedText.contains("村") || extractedText.contains("乡")
                    || extractedText.contains("道") || extractedText.contains("巷");
                
                // 2. 或者 AI 意图识别为 SEARCH（知名地标、POI名称等）
                AgentIntent tempIntent = parseIntentWithAI(extractedText);
                boolean isSearchIntent = tempIntent != null && "SEARCH".equals(tempIntent.getType());
                
                isAddressImage = hasAddressKeyword || isSearchIntent;
                
                log.info("🔍 地址类图片判断：hasAddressKeyword={}, isSearchIntent={}, isAddressImage={}", 
                    hasAddressKeyword, isSearchIntent, isAddressImage);
            }
            
            AgentResponse response;
            
            if (isAddressImage) {
                // 地址类图片：执行搜索逻辑
                log.info("📍 检测到地址类图片，执行搜索...");
                
                // 调用 AI 解析意图
                AgentIntent intent = parseIntentWithAI(extractedText);
                if (intent == null || intent.getType() == null) {
                    intent = fallbackIntent(extractedText);
                }
                
                intent.setSessionId(sessionId);
                intent.setLat(lat);
                intent.setLng(lng);
                intent.setCurrentState(AgentState.INTENT_RECOGNIZED);
                
                // 执行意图
                ExecutionResult execResult = executeIntent(sessionId, userId, intent, lat, lng);
                
                // 【关键修复】图片识别场景：将OCR识别的文字作为最高优先级
                if (execResult.getPlaces() != null && !execResult.getPlaces().isEmpty()) {
                    List<PoiDTO> places = execResult.getPlaces();
                    String ocrKeyword = extractedText.trim();
                    
                    log.info("🎯 图片识别排序优化：OCR关键词='{}', 原始结果数={}", ocrKeyword, places.size());
                    
                    // 重新排序
                    places.sort((p1, p2) -> {
                        String name1 = p1.getName() != null ? p1.getName() : "";
                        String name2 = p2.getName() != null ? p2.getName() : "";
                        String addr1 = p1.getAddress() != null ? p1.getAddress() : "";
                        String addr2 = p2.getAddress() != null ? p2.getAddress() : "";
                        
                        int score1 = calculateImageMatchScore(name1, addr1, ocrKeyword);
                        int score2 = calculateImageMatchScore(name2, addr2, ocrKeyword);
                        
                        return Integer.compare(score2, score1);
                    });
                    
                    log.info("✅ 图片识别排序完成，前3个结果：");
                    for (int i = 0; i < Math.min(3, places.size()); i++) {
                        PoiDTO poi = places.get(i);
                        int score = calculateImageMatchScore(
                            poi.getName() != null ? poi.getName() : "", 
                            poi.getAddress() != null ? poi.getAddress() : "",
                            ocrKeyword
                        );
                        log.info("  #{}: {} - 匹配分={}", i + 1, poi.getName(), score);
                    }
                }
                
                // 保存候选 POI
                if (execResult.getPlaces() != null && !execResult.getPlaces().isEmpty()) {
                    memoryService.saveCandidates(sessionId, execResult.getPlaces());
                    log.info("💾 保存候选 POI：sessionId={}, size={}", sessionId, execResult.getPlaces().size());
                }
                
                // 构建响应
                response = new AgentResponse();
                response.setType("IMAGE_RESULT");
                response.setSuccess(true);
                response.setMessage("图片识别成功");
                
                Map<String, Object> data = new HashMap<>();
                data.put("ocrText", extractedText);
                
                if (execResult.getPlaces() != null && !execResult.getPlaces().isEmpty()) {
                    data.put("places", execResult.getPlaces());
                    response.setPlaces(execResult.getPlaces());
                    log.info("✅ 找到 {} 个地点", execResult.getPlaces().size());
                } else if (execResult.getOrderData() != null) {
                    data.put("order", execResult.getOrderData());
                } else {
                    data.put("message", execResult.getMessage() != null ? execResult.getMessage() : "未找到相关地点");
                }
                
                response.setData(data);
                
            } else {
                // 非地址类图片：像豆包一样直接描述图片内容
                log.info("🖼️ 检测到非地址类图片，生成自然回复...");
                
                // 【关键修复】处理 imageDescription 为 null 的情况
                String safeImageDesc = (imageDescription != null && !imageDescription.equals("一张图片")) 
                    ? imageDescription 
                    : "这是一张图片";
                
                // ⭐ 批量模式：让 AI 理解多张图片的关系
                String chatMessage;
                if (isBatch) {
                    chatMessage = "我看到了" + imageBase64List.size() + "张图片：\n" + safeImageDesc;
                } else {
                    chatMessage = "我看到这张图片：" + safeImageDesc;
                }
                
                String aiResponse = generateChatResponse(sessionId, chatMessage);
                
                // 如果AI回复为空，使用默认回复
                if (aiResponse == null || aiResponse.trim().isEmpty()) {
                    aiResponse = "我看到了这张图片。" + safeImageDesc;
                }
                
                response = new AgentResponse();
                response.setType("IMAGE_RESULT");
                response.setSuccess(true);
                response.setMessage(aiResponse);
                
                Map<String, Object> data = new HashMap<>();
                data.put("ocrText", extractedText != null ? extractedText : "");
                data.put("imageDescription", imageDescription != null ? imageDescription : "");
                data.put("message", aiResponse);
                // ⭐ 批量模式：添加图片数量
                if (isBatch) {
                    data.put("imageCount", imageBase64List.size());
                }
                response.setData(data);
                
                log.info("✅ 生成图片描述回复：{}", aiResponse);
            }
            
            // 【关键修复】保存AI回复到记忆，支持多轮对话上下文
            memoryService.saveMessage(sessionId, "assistant", response.getMessage());
            log.info("💾 AI回复已保存到记忆：sessionId={}, message={}", sessionId, response.getMessage());
            
            return response;
            
        } catch (Exception e) {
            log.error("图片识别失败", e);
            // 返回错误响应，包含详细错误信息
            AgentResponse errorResponse = new AgentResponse();
            errorResponse.setType("IMAGE_RESULT");
            errorResponse.setSuccess(false);
            errorResponse.setMessage("图片识别失败：" + e.getMessage());
            errorResponse.setError(e.getMessage());
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("ocrText", "");
            errorData.put("message", "识别失败：" + e.getMessage());
            errorResponse.setData(errorData);
            
            // 【关键修复】保存错误信息到记忆
            memoryService.saveMessage(sessionId, "assistant", errorResponse.getMessage());
            
            return errorResponse;
        }
    }

    /**
     * 确认选择（严格按前端文档返回 data 结构）
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param selectedPoiName 选中的 POI 名称
     * @param lat 纬度（新增参数，用于计算路线）
     * @param lng 经度（新增参数，用于计算路线）
     * @return Object 前端期望的 data 结构：{type, message, poi, route}
     */
    public Object confirmSelection(String sessionId, Long userId, String selectedPoiName, Double lat, Double lng) {
        log.info("【状态流转】sessionId={}, state=WAIT_CONFIRM -> ORDER_CREATED (用户确认)", sessionId);
        
        // 参数校验
        if (selectedPoiName == null || selectedPoiName.trim().isEmpty()) {
            throw new IllegalArgumentException("选择的地点不能为空");
        }
        
        try {
            // 1. 从内存中获取候选 POI
            List<PoiDTO> candidates = memoryService.getCandidates(sessionId);
            if (candidates == null || candidates.isEmpty()) {
                log.warn("⚠️ 未找到候选 POI，sessionId={}", sessionId);
                // 返回 Map 格式的错误响应，保持类型一致
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("type", "ERROR");
                errorData.put("message", "请先搜索目的地");
                return errorData;
            }
            
            log.info("💡 候选 POI 列表：{}", candidates.stream().map(PoiDTO::getName).toList());
            
            // 2. 查找匹配的 POI（优化：支持模糊匹配，去除空格、大小写等差异）
            final String trimmedSelectedName = selectedPoiName.trim();
            PoiDTO selected = candidates.stream()
                .filter(poi -> poi.getName() != null)  // 先过滤 null 值
                .filter(poi -> {
                    String candidateName = poi.getName().trim();
                    // 精确匹配（去除首尾空格后比较）
                    return candidateName.equals(trimmedSelectedName);
                })
                .findFirst()
                .orElse(null);
                
            if (selected == null) {
                log.error("❌ 未找到匹配的 POI: selectedPoiName='{}', availableNames={}", 
                    trimmedSelectedName, candidates.stream().map(p -> "'" + p.getName() + "'").toList());
                // 返回 Map 格式的错误响应，保持类型一致
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("type", "ERROR");
                errorData.put("message", "未找到该地点，请重新选择");
                return errorData;
            }
            
            log.info("✅ 用户确认：{} - {}", selected.getName(), selected.getAddress());
            
            // 3. 使用传递的位置信息（优先）或从缓存获取
            double[] userLocation;
            if (lat != null && lng != null) {
                userLocation = new double[]{lat, lng};
                log.info("📍 使用请求传递的位置信息：lat={}, lng={}", lat, lng);
            } else {
                // Fallback: 从缓存获取
                userLocation = memoryService.getLocation(sessionId);
                if (userLocation == null) {
                    log.error("❌ 无法获取用户位置，sessionId={}", sessionId);
                    // 返回 Map 格式的错误响应，保持类型一致
                    Map<String, Object> errorData = new HashMap<>();
                    errorData.put("type", "ERROR");
                    errorData.put("message", "无法获取用户位置，请检查是否传递了 lat 和 lng 参数");
                    return errorData;
                }
                log.info("📍 使用缓存的位置信息：lat={}, lng={}", userLocation[0], userLocation[1]);
            }
            
            // 4. 计算路线（使用腾讯地图，坐标格式：lat,lng）
            String origin = String.format("%.6f,%.6f", userLocation[0], userLocation[1]);  // lat,lng
            String destination = String.format("%.6f,%.6f", selected.getLat(), selected.getLng());  // lat,lng
            
            log.info("🗺️ 开始调用腾讯地图 API 计算路线：origin={}, destination={}", origin, destination);
            RouteResult route = tencentMapClient.getRoute(origin, destination);  // 使用腾讯地图
            log.info("✅ 腾讯地图 API 返回：route={}", route);
            
            if (route == null) {
                log.error("❌ 路线计算失败：origin={}, destination={}", origin, destination);
                // 返回 Map 格式的错误响应，保持类型一致
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("type", "ERROR");
                errorData.put("message", "无法计算路线，请检查起点和终点位置是否有效");
                return errorData;
            }
            
            log.info("✅ 路线规划成功：距离={}m, 时长={}s, 价格={}元", 
                    route.getDistance(), route.getDuration(), route.getPrice());
            
            // 5. 构建符合前端期望的 data 结构
            // 前端文档期望的格式：
            // {
            //   "type": "ORDER",
            //   "message": "已确认目的地，正在创建订单",
            //   "poi": {...},
            //   "route": {"distance": ..., "duration": ..., "price": ...}
            // }
            Map<String, Object> data = new HashMap<>();
            data.put("type", "ORDER");
            data.put("message", "已确认目的地，正在创建订单");  // 修复：与前端文档一致
            
            // 构建 poi 对象（确保所有字段都不为 null）
            Map<String, Object> poiData = new HashMap<>();
            poiData.put("id", selected.getId() != null ? selected.getId() : selected.getName());
            poiData.put("name", selected.getName() != null ? selected.getName() : "未知地点");
            poiData.put("address", selected.getAddress() != null ? selected.getAddress() : "未知地址");
            poiData.put("lat", selected.getLat());
            poiData.put("lng", selected.getLng());
            data.put("poi", poiData);
            
            // 构建 route 对象（route 字段都是基本类型，不会为 null）
            Map<String, Object> routeData = new HashMap<>();
            routeData.put("distance", route.getDistance());   // 单位：米
            routeData.put("duration", route.getDuration());   // 单位：秒
            routeData.put("price", route.getPrice());         // 单位：元
            data.put("route", routeData);
            
            log.info("✅ 构建响应数据：poi.name={}, route.distance={}", 
                    poiData.get("name"), routeData.get("distance"));
            
            return data;
            
        } catch (IllegalArgumentException e) {
            log.error("❌ 参数校验失败", e);
            // 返回错误信息的 Map，保持类型一致（符合前端文档格式）
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("type", "ERROR");
            errorData.put("message", e.getMessage());
            return errorData;
        } catch (Exception e) {
            log.error("❌ 确认失败", e);
            // 返回具体错误信息，便于调试（符合前端文档格式）
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("type", "ERROR");
            errorData.put("message", e.getMessage() != null ? e.getMessage() : "确认选择失败");
            // 打印详细堆栈，便于定位问题
            log.error("详细错误：{}", e.getClass().getName() + " - " + e.getMessage());
            return errorData;
        }
    }

    /**
     * 保存候选 POI 到内存（供 MapController 使用）
     */
    public void saveCandidatesToMemory(String sessionId, List<PoiDTO> candidates) {
        memoryService.saveCandidates(sessionId, candidates);
        log.info("💾 保存候选 POI：sessionId={}, size={}", sessionId, candidates.size());
    }

    /**
     * 清理会话
     */
    public void cleanupSession(String sessionId) {
        memoryService.clearSession(sessionId);
    }

    /**
     * 更新用户位置
     */
    public void updateUserLocation(String sessionId, Double lat, Double lng) {
        memoryService.saveLocation(sessionId, lat, lng);
    }

    /**
     * POI 过滤器（精简版：只去除明确脏数据）
     */
    private List<PoiDTO> filterPoiList(List<PoiDTO> poiList, String keyword) {
        if (poiList == null || poiList.isEmpty()) {
            return poiList;
        }
    
        // 只过滤明确的脏数据：美发、理发等
        String[] blacklist = {"理发", "美发", "美容", "烫发", "染发", "剪发", "造型", "形象设计"};
                
        List<PoiDTO> filtered = new ArrayList<>();
        for (PoiDTO poi : poiList) {
            String name = poi.getName();
            if (name == null) continue;
        
            boolean isBad = false;
            for (String bad : blacklist) {
                if (name.contains(bad)) {
                    isBad = true;
                    break;
                }
            }
            if (!isBad) {
                filtered.add(poi);
            }
        }
        
        log.info("✅ POI 过滤完成：{} -> {} (精简过滤模式)", poiList.size(), filtered.size());
    
        return filtered;
    }
    
    /**
     * 【关键新增】构建搜索引导消息（主动引导用户下一步操作）
     * @param places POI 列表
     * @return 包含引导信息的消息文本
     */
    private String buildSearchGuideMessage(List<PoiDTO> places) {
        if (places == null || places.isEmpty()) {
            return "抱歉，未找到相关地点";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("为你找到以下地点：\n\n");
        
        // 显示前 3 个地点
        int showCount = Math.min(3, places.size());
        for (int i = 0; i < showCount; i++) {
            PoiDTO poi = places.get(i);
            double distanceKm = poi.getDistance() / 1000.0;
            Double price = poi.getPrice();
            
            sb.append(String.format("%d. %s\n", i + 1, poi.getName()));
            sb.append(String.format("   距离：%.1f 公里", distanceKm));
            if (price != null && price > 0) {
                sb.append(String.format("，预计费用：%.0f 元", price));
            }
            sb.append("\n");
        }
        
        if (places.size() > 3) {
            sb.append(String.format("\n还有 %d 个地点...\n", places.size() - 3));
        }
        
        // 【关键】主动引导用户下一步操作
        sb.append("\n您可以：\n");
        sb.append("• 回复'第一个'/'第二个'选择地点\n");
        sb.append("• 或直接说'帮我叫车'下单\n");
        sb.append("• 也可以说'换一个'重新搜索");
        
        return sb.toString();
    }
    
    /**
     * 【关键新增】生成真正的 AI 对话回复（用于 CHAT 类型）
     * @param sessionId 会话 ID
     * @param userMessage 用户消息
     * @return AI 生成的自然语言回复
     */
    private String generateChatResponse(String sessionId, String userMessage) {
        try {
            // 【关键修复】防止 userMessage 为 null
            if (userMessage == null || userMessage.trim().isEmpty()) {
                log.warn("⚠️ userMessage 为空，使用默认问候语");
                return "你好！我是小安，你的智能出行助手。我可以帮你查找地点、规划路线或叫车出行。有什么可以帮你的吗？";
            }
            
            // 【新增】检查是否为位置查询
            if (isLocationQuery(userMessage)) {
                log.info("📍 检测到位置查询：{}", userMessage);
                return handleLocationQuery(sessionId, userMessage);
            }
            
            // 1. 获取历史消息（最多 10 条）
            List<com.anxin.travel.agent.model.ChatMessage> history = memoryService.getMessages(sessionId);
            
            // 2. 构建完整的消息列表（system + history + current）
            com.alibaba.fastjson.JSONArray messages = new com.alibaba.fastjson.JSONArray();
            
            // System Prompt - 定义 AI 助手角色（像豆包那样的自然对话 + 事实准确）
            com.alibaba.fastjson.JSONObject systemMsg = new com.alibaba.fastjson.JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", 
                "你是一个智能助手，名叫'小安'。你既是一个专业的出行助手，也是一个友好的聊天伙伴。\n\n" +
                "【核心能力】\n" +
                "1. **出行服务**：查找地点、规划路线、预估价格、叫车下单（这是你的强项，可以调用工具获取真实数据）\n" +
                "2. **日常聊天**：进行自然对话，分享观点和建议\n" +
                "3. **知识问答**：回答常见问题，但要注意事实准确性\n\n" +
                "【对话风格】\n" +
                "- 保持友好、自然、亲切的语气，像朋友一样交流\n" +
                "- 回复要生动有趣，避免机械化的固定话术\n" +
                "- 根据用户情绪调整语气（开心时活泼，难过时安慰）\n" +
                "- 适当使用表情符号增加亲和力（如 😊 👍 🚗）\n" +
                "- 回复长度灵活控制，简单问题简短回答，复杂问题详细说明\n" +
                "- **不要过度冗长**，保持简洁但有温度\n\n" +
                "【重要原则 - 事实准确性】\n" +
                "- **严禁编造不确定的信息**！如果不知道确切答案，诚实说明\n" +
                "- **对于实时数据**（如天气、新闻、股价等），由于你无法访问实时信息，请采取以下策略：\n" +
                "  * 天气问题：提供季节性建议和出行提示，例如'现在这个季节通常比较温暖/凉爽，出门可以带件外套哦～如果要获取精确预报，建议查看天气APP'\n" +
                "  * 新闻问题：礼貌说明无法获取最新新闻，但可以聊聊相关话题\n" +
                "  * 其他实时数据：诚实说明限制，但尽量提供有用的替代建议\n" +
                "- 对于常识性问题（如历史、科学、文化），可以基于训练数据自信回答\n" +
                "- **保持自然对话**，避免机械化的固定话术，像朋友一样交流\n" +
                "- 在不确定时，可以提供一般性建议或引导用户查询可靠来源\n\n" +
                "【出行服务优先】\n" +
                "- 当用户提到地点、路线、打车等需求时，优先提供准确的出行服务\n" +
                "- 可以主动询问细节以确保提供正确帮助（如'你想去哪个地方的医院呢？'）\n" +
                "- 在聊天中自然引导到出行服务，但不要强行推销\n\n" +
                "【示例对话】\n" +
                "用户：你好\n助手：你好呀！😊 我是小安，很高兴认识你！今天有什么我可以帮你的吗？\n\n" +
                "用户：今天天气怎么样\n助手：现在是春季，天气通常比较舒适温暖呢～ ☀️ 不过具体预报可能会有变化，建议你查看天气APP获取最准确的信息。如果要出门游玩或办事，我可以帮你规划路线或者叫车哦！\n\n" +
                "用户：我想去北京大学\n助手：好的！我来帮你找一下北京大学的地点。🚗\n\n" +
                "用户：谢谢\n助手：不客气～ 😊 随时为你服务！还有其他需要帮忙的吗？\n\n" +
                "用户：你今天过得怎么样\n助手：谢谢关心！我今天状态很好，随时准备帮助你呢～ 你今天过得如何呀？\n\n" +
                "用户：最近有什么新闻\n助手：抱歉，我无法获取最新的实时新闻呢～ 不过如果你对某个话题感兴趣，我们可以聊聊！或者如果你要出行，我可以帮你查找地点和规划路线哦～"
            );
            messages.add(systemMsg);
            
            // 添加历史消息（最多 10 条）
            if (history != null && !history.isEmpty()) {
                int startIdx = Math.max(0, history.size() - 10);
                for (int i = startIdx; i < history.size(); i++) {
                    com.anxin.travel.agent.model.ChatMessage msg = history.get(i);
                    com.alibaba.fastjson.JSONObject jsonMsg = new com.alibaba.fastjson.JSONObject();
                    jsonMsg.put("role", "user".equals(msg.getRole()) ? "user" : "assistant");
                    jsonMsg.put("content", msg.getContent());
                    messages.add(jsonMsg);
                }
                log.info("✅ 已添加 {} 条历史消息到对话上下文", history.size() - startIdx);
            }
            
            // 添加当前用户消息
            com.alibaba.fastjson.JSONObject currentMsg = new com.alibaba.fastjson.JSONObject();
            currentMsg.put("role", "user");
            currentMsg.put("content", userMessage);
            messages.add(currentMsg);
            
            log.info("🤖 调用 AI 对话生成：sessionId={}, messageCount={}", sessionId, messages.size());
            
            // 3. 调用通义千问 API 生成回复
            String aiResponse = tongyiClient.chatCompletion(messages);
            
            if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                log.info("✅ AI 对话生成成功：回复长度={}", aiResponse.length());
                return aiResponse.trim();
            } else {
                log.warn("⚠️ AI 返回空回复，使用默认回复");
                return "你好！我是小安，你的智能出行助手。我可以帮你查找地点、规划路线或叫车出行。有什么可以帮你的吗？";
            }
            
        } catch (Exception e) {
            log.error("❌ AI 对话生成失败", e);
            // Fallback：返回友好的默认回复
            return "抱款，我暂时遇到了一些问题。你可以尝试告诉我你想去哪里，我会帮你查找地点并规划路线。";
        }
    }
    
    /**
     * 判断是否为位置查询
     */
    private boolean isLocationQuery(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String msg = message.toLowerCase();
        return msg.contains("我在哪里") || msg.contains("我现在在哪里") || 
               msg.contains("我的位置") || msg.contains("我现在在哪") ||
               msg.contains("位置信息") || msg.contains("当前定位");
    }
    
    /**
     * 处理位置查询（调用逆地理编码 API）
     */
    private String handleLocationQuery(String sessionId, String userMessage) {
        try {
            // 1. 从缓存获取用户位置
            double[] location = memoryService.getLocation(sessionId);
                
            if (location == null || location.length != 2) {
                log.warn("⚠️ 无法获取用户位置，sessionId={}", sessionId);
                return "抱款，我还没有收到您的位置信息呢～ 请允许应用获取您的定位，或者在地图上点击选择当前位置。";
            }
                
            double lat = location[0];
            double lng = location[1];
            log.info("📍 开始逆地理编码：lat={}, lng={}", lat, lng);
                
            // 2. 调用高德地图逆地理编码 API
            com.anxin.travel.module.map.dto.ReverseGeocodeResponse geocodeResult = amapClient.reverseGeocode(lat, lng);
                
            if (geocodeResult == null || geocodeResult.getAddress() == null || geocodeResult.getAddress().trim().isEmpty()) {
                log.error("❌ 逆地理编码失败");
                return "抱款，暂时无法解析您的位置信息，请稍后再试。";
            }
                
            String address = geocodeResult.getAddress();
            log.info("✅ 逆地理编码成功：{}", address);
                
            // 3. 生成友好回复
            return String.format("您现在位于%s附近。需要我帮您查找周边的医院、餐厅或其他地点吗？😊", address);
                
        } catch (Exception e) {
            log.error("❌ 处理位置查询失败", e);
            return "抱款，获取位置信息时遇到了问题，请稍后再试。";
        }
    }
}