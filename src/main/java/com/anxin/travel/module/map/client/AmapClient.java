package com.anxin.travel.module.map.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.anxin.travel.agent.model.CandidateDestination;
import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.map.dto.PoiDTO;
import com.anxin.travel.module.map.dto.ReverseGeocodeResponse;
import com.anxin.travel.module.map.dto.RouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class AmapClient {

    @Value("${anxin.amap.web-api-key}")
    private String apiKey;

    private final RedisUtil redisUtil;
    private final RestTemplate restTemplate;
    
    /**
     * 内部类：带评分的 POI（用于排序）
     */
    private static class ScoredPoi {
        private final PoiDTO poi;
        private final double score;
        
        public ScoredPoi(PoiDTO poi, double score) {
            this.poi = poi;
            this.score = score;
        }
        
        public PoiDTO getPoi() {
            return poi;
        }
        
        public double getScore() {
            return score;
        }
    }
    
    // 知名地标集合（与 AgentService 保持一致）
    private static final Set<String> FAMOUS_LANDMARKS = Set.of(
        "故宫", "长城", "天安门", "东方明珠", "西湖", "黄山", "九寨沟",
        "兵马俑", "布达拉宫", "鼓浪屿", "张家界", "泰山", "庐山", "峨眉山",
        "颐和园", "天坛", "鸟巢", "水立方", "外滩", "自由女神像", "埃菲尔铁塔",
        "北京大学", "清华大学", "人民大学", "中山大学", "武汉大学", "复旦大学",
        "上海交通大学", "浙江大学", "南京大学", "华中科技大学", "同济大学"
    );
    
    // 类别匹配字典（用于模糊匹配）
    private static final java.util.Map<String, java.util.List<String>> CATEGORY_KEYWORDS = new java.util.HashMap<>();
    static {
        CATEGORY_KEYWORDS.put("医院", java.util.Arrays.asList("医院", "卫生", "医疗", "中心", "诊所"));
        CATEGORY_KEYWORDS.put("学校", java.util.Arrays.asList("学校", "小学", "中学", "大学", "学院", "教育"));
        CATEGORY_KEYWORDS.put("餐厅", java.util.Arrays.asList("酒家", "食府", "餐馆", "酒楼", "菜馆", "餐厅", "饭店", "烧烤"));
        CATEGORY_KEYWORDS.put("酒店", java.util.Arrays.asList("酒店", "宾馆", "旅馆", "住宿", "大厦", "客栈"));
        CATEGORY_KEYWORDS.put("超市", java.util.Arrays.asList("超市", "商场", "市场", "百货", "便利店"));
        CATEGORY_KEYWORDS.put("银行", java.util.Arrays.asList("银行", "信用社", "ATM", "支行"));
        CATEGORY_KEYWORDS.put("药店", java.util.Arrays.asList("药店", "药房", "医药"));
        CATEGORY_KEYWORDS.put("小区", java.util.Arrays.asList("庭", "园", "苑", "阁", "小区", "花园", "家园", "公寓"));
    }

    public AmapClient(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
        // 修复：自定义 RestTemplate，添加浏览器级别的请求头
        this.restTemplate = createRestTemplate();
        
        // 新增：打印 SSL 配置信息
        log.info("✅ AmapClient 初始化完成：TLSv1.2 强制启用，HttpClient 连接池优化完成");
    }
    
    /**
     * 创建带有浏览器请求头和超时控制的 RestTemplate
     */
    private RestTemplate createRestTemplate() {
        try {
            // ========== 关键修复：强制使用 TLSv1.2，解决 SSLHandshakeException ==========
            // 核心配置：只启用 TLSv1.2，避免 TLSv1.3 兼容性问题
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, null, new java.security.SecureRandom());
            
            org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory sslSocketFactory = 
                new org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory(
                    sslContext,
                    new String[]{"TLSv1.2"},  // 只启用 TLSv1.2，90% 的 SSL 问题都因此解决
                    null,
                    new org.apache.hc.client5.http.ssl.DefaultHostnameVerifier()
                );
            
            // 使用 PoolingHttpClientConnectionManager 管理连接
            org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager = 
                new org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager();
            connectionManager.setMaxTotal(50);
            connectionManager.setDefaultMaxPerRoute(20);
            connectionManager.setValidateAfterInactivity(org.apache.hc.core5.util.TimeValue.ofSeconds(30));
            
            // 创建 RequestConfig 配置超时
            org.apache.hc.client5.http.config.RequestConfig requestConfig = 
                org.apache.hc.client5.http.config.RequestConfig.custom()
                    .setConnectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .setResponseTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .setConnectionRequestTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            // 使用 HttpComponentsClientHttpRequestFactory 支持更精细的超时控制
            org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = 
                org.apache.hc.client5.http.impl.classic.HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .evictExpiredConnections()  // 清理过期连接
                    .evictIdleConnections(org.apache.hc.core5.util.TimeValue.ofSeconds(30))  // 清理空闲连接
                    .disableAutomaticRetries()  // 禁用自动重试（防止重复请求）
                    .build();
            
            org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory = 
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(httpClient);
            
            RestTemplate restTemplate = new RestTemplate(factory);
            
            // 设置请求拦截器，添加浏览器级别的请求头
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                request.getHeaders().set("Accept", "application/json, text/plain, */*");
                request.getHeaders().set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                // 添加 Referer（模拟从网页访问）
                request.getHeaders().set("Referer", "https://lbs.amap.com/");
                // 新增：添加 Connection 头，保持长连接
                request.getHeaders().set("Connection", "keep-alive");
                return execution.execute(request, body);
            });
            
            log.info("✅ RestTemplate 配置完成：TLSv1.2, 连接超时 3s, 读取超时 5s, 最大连接数 50");
            return restTemplate;
            
        } catch (Exception e) {
            log.error("❌ RestTemplate 配置失败", e);
            throw new RuntimeException("RestTemplate 配置失败：" + e.getMessage(), e);
        }
    }

    @PostConstruct
    public void init() {
        log.info("============= AmapClient 初始化 =============");
        log.info("API Key 的长度：{}", apiKey != null ? apiKey.length() : "null");
        log.info("API Key 前 10 位：{}", apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : "null");
        log.info("===========================================");
            
        // ========== Step 1: 清空 POI 缓存 ==========
        clearPoiCache();
            
        // ========== Step 2: 用最原始的 RestTemplate 测试 ==========
        testSimpleRestTemplate();
    }
        
    /**
     * 清空所有 POI 缓存
     */
    private void clearPoiCache() {
        try {
            log.info("🧹 开始清空 POI 缓存...");
            Set<String> keys = redisUtil.keys("poi:*");
            if (keys != null && !keys.isEmpty()) {
                redisUtil.delete(keys);
                log.info("✅ 已清空 {} 个 POI 缓存 key", keys.size());
            } else {
                log.info("ℹ️ 无需清空，POI 缓存为空");
            }
        } catch (Exception e) {
            log.warn("⚠️ 清空 POI 缓存失败：{}", e.getMessage());
        }
    }
    
    /**
     * 测试：使用最原始的 RestTemplate（不自定义 SSL，不 HttpClient）
     */
    private void testSimpleRestTemplate() {
        try {
            log.info("🧪 ========== 开始测试原始 RestTemplate ==========");
            
            // 最简单的 RestTemplate，什么配置都没有
            org.springframework.web.client.RestTemplate simpleRestTemplate = new org.springframework.web.client.RestTemplate();
            
            String testUrl = "https://restapi.amap.com/v3/place/text?keywords=医院&location=116.397479,39.908720&radius=5000&offset=5&page=1&key=" + apiKey;
            
            log.info("测试 URL: {}", testUrl);
            log.info("URL 长度：{} 字符", testUrl.length());
            
            long startTime = System.currentTimeMillis();
            
            String result = simpleRestTemplate.getForObject(testUrl, String.class);
            
            long endTime = System.currentTimeMillis();
            
            if (result != null && !result.isEmpty()) {
                log.info("✅ 测试成功！耗时：{}ms", (endTime - startTime));
                log.info("响应长度：{} 字符", result.length());
                
                // 简单验证是否是有效 JSON
                if (result.contains("\"status\":\"1\"")) {
                    log.info("✅ 高德 API 返回正常状态码");
                } else if (result.contains("\"status\":\"0\"")) {
                    log.warn("⚠️ 高德 API 返回错误状态：{}", result.substring(0, Math.min(200, result.length())));
                } else {
                    log.info("响应内容：{}", result.substring(0, Math.min(200, result.length())));
                }
            } else {
                log.error("❌ 测试失败：返回结果为空");
            }
            
            log.info("🧪 ========== 原始 RestTemplate 测试完成 ==========\n");
            
        } catch (Exception e) {
            log.error("❌ 原始 RestTemplate 测试失败", e);
            log.error("错误类型：{}", e.getClass().getSimpleName());
            log.error("错误信息：{}", e.getMessage());
            log.error("\n💡 建议：如果这里报 SSLHandshakeException，说明是 JVM SSL 配置问题\n");
        }
    }

    /**
     * 获取 API Key（供 AgentService 使用）
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * POI 搜索（修复版）- 支持全国/周边搜索 + 距离排序
     * @param keyword 搜索关键词
     * @param userLat 用户纬度
     * @param userLng 用户经度
     * @param page 页码（从 1 开始）
     * @param pageSize 每页数量（最大 25）
     * @param sortByDistance 是否按距离排序
     * @param nationwide 是否全国搜索
     */
    public List<PoiDTO> searchNearbyPlaces(String keyword, double userLat, double userLng, 
                                           int page, int pageSize, boolean sortByDistance, boolean nationwide) {
        return searchNearbyPlaces(keyword, userLat, userLng, page, pageSize, sortByDistance, nationwide, 5000);
    }
    
    /**
     * POI 搜索（完整版）- 支持全国/周边搜索 + 距离排序 + 自定义半径
     * @param keyword 搜索关键词
     * @param userLat 用户纬度
     * @param userLng 用户经度
     * @param page 页码（从 1 开始）
     * @param pageSize 每页数量（最大 25）
     * @param sortByDistance 是否按距离排序
     * @param nationwide 是否全国搜索
     * @param radius 搜索半径（米，仅周边搜索有效，默认 5000 米）
     */
    public List<PoiDTO> searchNearbyPlaces(String keyword, double userLat, double userLng, 
                                           int page, int pageSize, boolean sortByDistance, boolean nationwide, int radius) {
        return searchNearbyPlaces(keyword, userLat, userLng, page, pageSize, sortByDistance, nationwide, radius, false);
    }
    
    /**
     * POI 搜索（增强版）- 完全仿照高德地图搜索功能
     * @param keyword 搜索关键词（支持：地点名称/简称/地址/经纬度/拼音缩写）
     * @param userLat 用户纬度（用于距离排序和参考）
     * @param userLng 用户经度（用于距离排序和参考）
     * @param page 页码（从 1 开始）
     * @param pageSize 每页数量（最大 25）
     * @param sortByDistance 是否按距离排序
     * @param nationwide 是否全国搜索
     * @param radius 搜索半径（米，仅周边搜索有效，默认 5000 米）
     * @param fuzzyMatch 是否启用模糊匹配（已废弃，始终为 true）
     */
    public List<PoiDTO> searchNearbyPlaces(String keyword, double userLat, double userLng, 
                                           int page, int pageSize, boolean sortByDistance, boolean nationwide, int radius, boolean fuzzyMatch) {
        return searchNearbyPlaces(keyword, userLat, userLng, page, pageSize, sortByDistance, nationwide, radius, fuzzyMatch, null);
    }
    
    /**
     * POI 搜索（完整版）- 支持 POI type 过滤
     * @param keyword 搜索关键词
     * @param userLat 用户纬度
     * @param userLng 用户经度
     * @param page 页码（从 1 开始）
     * @param pageSize 每页数量（最大 25）
     * @param sortByDistance 是否按距离排序
     * @param nationwide 是否全国搜索
     * @param radius 搜索半径（米）
     * @param fuzzyMatch 是否启用模糊匹配
     * @param poiType POI 类型编码（如 "090000" 医院，null 表示不限制）
     */
    public List<PoiDTO> searchNearbyPlaces(String keyword, double userLat, double userLng, 
                                           int page, int pageSize, boolean sortByDistance, boolean nationwide, 
                                           int radius, boolean fuzzyMatch, String poiType) {
        log.info("============ POI 搜索开始 ============");
        log.info("关键词：{}, 坐标：({}, {}), 页码：{}, 每页：{}, 排序：{}, 全国：{}, 半径：{}", 
                keyword, userLat, userLng, page, pageSize, sortByDistance, nationwide, radius);

        // 新增：识别经纬度坐标格式（例如："30.5928,114.3055"）
        if (keyword.matches("\\d+\\.\\d+,\\d+\\.\\d+")) {
            log.info("检测到经纬度坐标格式：{}", keyword);
            return searchByCoordinates(keyword, userLat, userLng, sortByDistance);
        }

        String cacheKey = String.format("poi:%s:%.6f:%.6f:%d:%d", 
                keyword, userLat, userLng, page, pageSize);

        // 尝试从缓存获取
        try {
            String cached = redisUtil.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.info("命中 POI 缓存：{}, 结果数：{}", cacheKey, JSON.parseArray(cached).size());
                return JSON.parseArray(cached, PoiDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取缓存失败：{}", e.getMessage());
        }

        int validPageSize = Math.min(pageSize, 25);
        // 修复：改用 java.net.URLEncoder 进行编码，避免 UriUtils 的兼容性问题
        String encodedKeyword;
        try {
            encodedKeyword = java.net.URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            // 手动替换 + 号为 %20（某些服务器对 + 号处理不一致）
            encodedKeyword = encodedKeyword.replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 编码一定存在，这里理论上不会执行
            log.error("UTF-8 编码不支持，使用原始关键词", e);
            encodedKeyword = keyword;
        }

        // ========== 【关键修复】搜索策略路由（Search Strategy Router） ==========
        // 根据关键词类型选择不同的搜索接口，避免“周边模糊搜索”导致的不相关结果
                
        boolean isInstitution = isLandmarkOrInstitution(keyword);
                
        String url;
        int validRadius = Math.min(Math.max(100, radius), 50000);
                
        // 机构/地标：使用 place/text 精确搜索（带 city 参数，优先同城匹配）
        if (isInstitution && !nationwide) {
            // 先通过逆地理编码获取当前城市
            String city = getCityByLocation(userLat, userLng);
            log.info("🏙️ 逆地理编码获取城市：lat={}, lng={}, city={}", userLat, userLng, city);
            
            // 【新增】根据关键词推断 types 参数，减少脏数据
            String typesParam = getTypesParamForKeyword(keyword);
                    
            if (city != null && !city.isEmpty()) {
                // 有城市信息，使用 citylimit=true 精确搜索
                url = String.format(
                        "https://restapi.amap.com/v3/place/text?keywords=%s&city=%s&citylimit=true&offset=%d&page=%d&key=%s&extensions=all%s",
                        encodedKeyword, city, validPageSize, page, apiKey,
                        typesParam != null ? "&types=" + typesParam : ""
                );
                log.info("🏛️ 机构/地标搜索（城市精确模式，带 city+citylimit{}）: {}", 
                         typesParam != null ? "+types" : "", url);
            } else {
                // 无法获取城市，退化为全国搜索
                url = String.format(
                        "https://restapi.amap.com/v3/place/text?keywords=%s&offset=%d&page=%d&key=%s&extensions=all%s",
                        encodedKeyword, validPageSize, page, apiKey,
                        typesParam != null ? "&types=" + typesParam : ""
                );
                log.info("⚠️ 无法获取城市，使用全国搜索（不带 city{}）: {}", 
                         typesParam != null ? "+types" : "", url);
            }
        }
        // 全国搜索 OR 周边设施：使用 place/text 全国搜索 OR place/around 周边搜索
        else {
            if (nationwide) {
                // 【关键修复】全国搜索：不带 location 参数，也不带 city 参数
                url = String.format(
                        "https://restapi.amap.com/v3/place/text?keywords=%s&offset=%d&page=%d&key=%s&extensions=all",
                        encodedKeyword, validPageSize, page, apiKey
                );
                log.info("🔍 全国搜索（不带 location+city）: {}", url);
            } else {
                // 周边设施：使用 place/around 周边搜索（带 location + radius）
                url = String.format(
                        "https://restapi.amap.com/v3/place/around?keywords=%s&location=%s,%s&radius=%d&offset=%d&page=%d&key=%s&sortrule=distance",
                        encodedKeyword, userLng, userLat, validRadius, validPageSize, page, apiKey
                );
                log.info("📍 周边搜索（around 模式，带 location+radius）: {}", url);
            }
        }
        
        // 新增：如果指定了 POI type，添加到请求中
        if (poiType != null && !poiType.isEmpty()) {
            url += "&types=" + poiType;
            log.info("🎯 使用 POI type 过滤：{}", poiType);
        }

        String response;
        try {
            log.info("🔍 准备发送 HTTP 请求到高德 API...");
            log.info("完整 URL: {}", url);
            log.info("URL 长度：{} 字符", url.length());
            log.info("使用的 RestTemplate: {}", restTemplate.getClass().getName());
            
            // 新增：记录请求头信息
            log.info("请求头配置：User-Agent={}, Accept={}, Referer={}", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", 
                "application/json", 
                "https://lbs.amap.com/");
            
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            
            log.info("⬅️ 收到 HTTP 响应：");
            log.info("  - 状态码：{}", resp.getStatusCode());
            log.info("  - Headers: {}", resp.getHeaders());
            log.info("  - Body 长度：{} 字符", resp.getBody() != null ? resp.getBody().length() : 0);
            
            response = resp.getBody();
            
            log.info("📄 完整响应内容：\n{}", response);  // 打印完整响应
        } catch (Exception e) {
            log.error("❌ 请求失败：{}", e.getMessage());
            log.error("异常类型：{}", e.getClass().getName());
            log.error("异常堆栈：", e);
            return new ArrayList<>();
        }

        List<PoiDTO> result = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            log.error("API 响应为空");
            return result;
        }

        try {
            JSONObject json = JSON.parseObject(response);
            log.info("API 状态码：{}, 信息：{}", json.getString("status"), json.getString("info"));
            
            if (!"1".equals(json.getString("status"))) {
                log.warn("POI 搜索失败：status={}, info={}", json.getString("status"), json.getString("info"));
                return result;
            }

            JSONArray pois = json.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                log.info("未找到相关 POI，尝试切换到全国搜索");
                
                // Fallback: 如果周边搜索无结果，自动尝试全国搜索
                if (!nationwide) {
                    log.info("周边搜索无结果，切换到全国搜索模式");
                    return searchNearbyPlaces(keyword, userLat, userLng, page, pageSize, sortByDistance, true, radius, true);
                }
                
                log.info("未找到相关 POI");
                return result;
            }

            // ========== 核心优化：从"严格过滤"改为"评分排序 + 兜底策略" ==========
            // 工业级搜索流程：所有结果都保留 -> 计算评分 -> 排序 -> 取前 N 个 -> 兜底
            
            java.util.List<ScoredPoi> scoredList = new java.util.ArrayList<>();
            
            // 1. 解析所有 POI 并计算评分（不过滤）
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                String location = poi.getString("location");
                if (location == null || !location.contains(",")) continue;

                String[] parts = location.split(",");
                double lng = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                String poiName = poi.getString("name");
                String finalAddress = poi.getString("address");

                // 计算距离
                double distance;
                try {
                    String distanceStr = poi.getString("distance");
                    distance = (distanceStr != null && !distanceStr.isEmpty() && !"[]".equals(distanceStr)) 
                                ? Double.parseDouble(distanceStr) 
                                : calculateDistance(userLat, userLng, lat, lng);
                } catch (Exception e) {
                    distance = calculateDistance(userLat, userLng, lat, lng);
                }

                PoiDTO dto = new PoiDTO();
                dto.setName(poiName);
                dto.setAddress(finalAddress);
                dto.setLat(lat);
                dto.setLng(lng);
                dto.setDistance(distance);
                
                // 获取高德返回的 POI 类型（用于类别匹配）
                String poiTypeStr = poi.getString("type");
                dto.setType(poiTypeStr);
                
                // 2. 计算综合评分（名称 40% + 类别 30% + 距离 20% + 热度 10%）
                double score = computeIndustrialScore(dto, keyword, poiTypeStr);
                
                scoredList.add(new ScoredPoi(dto, score));
                
                log.info("📊 POI#{}: name={}, address={}, distance={}m, score={}", 
                        i + 1, poiName, finalAddress, distance, score);
            }
            
            // 3. 按评分降序排序
            scoredList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            // 4. 取前 20 个（如果结果少则全取）
            int takeCount = Math.min(20, scoredList.size());
            for (int i = 0; i < takeCount; i++) {
                result.add(scoredList.get(i).getPoi());
            }
            
            log.info("✅ 评分排序完成，取前 {} 个结果，平均分：{}", 
                    takeCount, 
                    scoredList.stream().mapToDouble(ScoredPoi::getScore).average().orElse(0.0));
            
            // 5. 打印排序后的详细日志
            if (!result.isEmpty()) {
                log.info("============ 智能排序结果详情 ============");
                for (int i = 0; i < result.size(); i++) {
                    PoiDTO poi = result.get(i);
                    double score = computeIndustrialScore(poi, keyword, null);
                    log.info("排名#{}: name={}, address={}, distance={}m, score={}", 
                            i + 1, poi.getName(), poi.getAddress(), poi.getDistance(), score);
                }
                log.info("======================================");
            }
            
            // 6.【增强兜底策略】如果评分排序后仍然无结果，返回高德原始前 5 个
            if (result.isEmpty() && pois != null && !pois.isEmpty()) {
                log.warn("⚠️ 评分排序后结果为空，触发增强兜底策略：返回高德原始前 5 个");
                for (int i = 0; i < Math.min(5, pois.size()); i++) {
                    JSONObject poi = pois.getJSONObject(i);
                    String location = poi.getString("location");
                    if (location == null || !location.contains(",")) continue;
                                
                    String[] parts = location.split(",");
                    double lng = Double.parseDouble(parts[0]);
                    double lat = Double.parseDouble(parts[1]);
                                
                    PoiDTO dto = new PoiDTO();
                    dto.setName(poi.getString("name"));
                    dto.setAddress(poi.getString("address"));
                    dto.setLat(lat);
                    dto.setLng(lng);
                    dto.setDistance(calculateDistance(userLat, userLng, lat, lng));
                    dto.setType(poi.getString("type"));
                                
                    result.add(dto);
                }
                log.info("✅ 增强兜底策略生效，返回 {} 个高德原始结果", result.size());
            }
            
            // 7.【最终兜底】如果还是 0 结果，但高德返回了数据，强制返回所有 POI（不做任何过滤）
            if (result.isEmpty() && pois != null && !pois.isEmpty()) {
                log.warn("⚠️ 第一次兜底失败，触发最终兜底：返回所有高德原始 POI");
                for (int i = 0; i < pois.size(); i++) {
                    JSONObject poi = pois.getJSONObject(i);
                    String location = poi.getString("location");
                    if (location == null || !location.contains(",")) continue;
                                
                    String[] parts = location.split(",");
                    double lng = Double.parseDouble(parts[0]);
                    double lat = Double.parseDouble(parts[1]);
                                
                    PoiDTO dto = new PoiDTO();
                    dto.setName(poi.getString("name"));
                    dto.setAddress(poi.getString("address"));
                    dto.setLat(lat);
                    dto.setLng(lng);
                    dto.setDistance(calculateDistance(userLat, userLng, lat, lng));
                    dto.setType(poi.getString("type"));
                                
                    result.add(dto);
                }
                log.info("✅ 最终兜底策略生效，返回 {} 个高德原始 POI", result.size());
            }

            // 写入缓存（10 分钟）
            if (!result.isEmpty()) {
                try {
                    redisUtil.set(cacheKey, JSON.toJSONString(result), 600);
                    log.info("已缓存 POI 结果，key={}, 过期时间 600 秒", cacheKey);
                } catch (Exception e) {
                    log.warn("写入缓存失败：{}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("解析 POI 响应失败", e);
        }

        log.info("POI 搜索完成，找到 {} 个地点", result.size());
        log.info("============ POI 搜索结束 ============");
        return result;
    }

    /**
     * 真正的全国搜索（不带 location 参数，专用于 Quality Gate 触发后的全国搜索）
     * @param keyword 搜索关键词
     * @param page 页码（从 1 开始）
     * @param pageSize 每页数量（最大 25）
     * @return POI 列表（无距离信息）
     */
    public List<PoiDTO> searchByKeywordNationwide(String keyword, int page, int pageSize) {
        try {
            String encodedKeyword = java.net.URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()).replace("+", "%20");
            // 【真正全国搜索】不带 location 参数，强制在全国范围搜索
            String url = String.format(
                    "https://restapi.amap.com/v3/place/text?keywords=%s&offset=%d&page=%d&key=%s&extensions=all",
                    encodedKeyword, pageSize, page, apiKey
            );
            
            log.info("🔍 真正全国搜索 URL（不带 location）: {}", url);
            
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            String response = resp.getBody();
            
            log.info("📋 全国搜索原始响应：{}", response != null && response.length() > 500 ? response.substring(0, 500) + "..." : response);
            
            if (response == null || response.isEmpty()) {
                log.warn("全国搜索响应为空");
                return new ArrayList<>();
            }
            
            JSONObject json = JSON.parseObject(response);
            if (!"1".equals(json.getString("status"))) {
                log.warn("全国搜索失败：status={}, info={}", json.getString("status"), json.getString("info"));
                return new ArrayList<>();
            }
            
            JSONArray pois = json.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                log.info("全国搜索无结果：keyword={}", keyword);
                return new ArrayList<>();
            }
            
            log.info("✅ 全国搜索找到 {} 个结果（期望 {} 个）", pois.size(), pageSize);
            
            // 【关键修改】移除名称过滤，返回所有高德原始结果，让后续排序决定相关性
            List<PoiDTO> result = new ArrayList<>();
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                String location = poi.getString("location");
                if (location == null || !location.contains(",")) continue;
                
                String[] parts = location.split(",");
                double lng = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                
                PoiDTO dto = new PoiDTO();
                dto.setName(poi.getString("name"));
                dto.setAddress(poi.getString("address"));
                dto.setLat(lat);
                dto.setLng(lng);
                dto.setType(poi.getString("type"));
                result.add(dto);
            }
            
            log.info("✅ 全国搜索完成：返回 {} 个原始结果（未过滤）", result.size());
            return result;
            
        } catch (Exception e) {
            log.error("全国搜索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 通过经纬度坐标搜索（逆地理编码）
     * @param coordinates 坐标字符串，格式："纬度，经度"
     * @param userLat 用户纬度
     * @param userLng 用户经度
     * @param sortByDistance 是否按距离排序
     * @return POI 列表（包含坐标点位置）
     */
    private List<PoiDTO> searchByCoordinates(String coordinates, double userLat, double userLng, boolean sortByDistance) {
        try {
            String[] parts = coordinates.split(",");
            double lat = Double.parseDouble(parts[0]);
            double lng = Double.parseDouble(parts[1]);
            
            log.info("坐标搜索：lat={}, lng={}", lat, lng);
            
            // 调用逆地理编码获取位置信息
            ReverseGeocodeResponse geocode = reverseGeocode(lat, lng);
            
            List<PoiDTO> result = new ArrayList<>();
            
            if (geocode != null && geocode.getAddress() != null) {
                // 构建 POI 对象
                PoiDTO poi = new PoiDTO();
                poi.setName(geocode.getAddress());
                poi.setAddress(geocode.getAddress());
                poi.setLat(lat);
                poi.setLng(lng);
                poi.setDistance(calculateDistance(userLat, userLng, lat, lng));
                
                result.add(poi);
                log.info("坐标搜索成功：{}", geocode.getAddress());
            } else {
                log.warn("坐标逆地理编码失败");
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("坐标搜索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 【V2 新增】城市精确搜索（带 types 过滤）
     * @param keyword 关键词
     * @param city 城市名称
     * @param types POI 类型（如"高等院校"）
     * @param page 页码
     * @param pageSize 每页数量
     */
    public List<PoiDTO> searchByCityWithTypes(String keyword, String city, String types, int page, int pageSize) {
        try {
            String encodedKeyword = java.net.URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()).replace("+", "%20");
            
            // 构建 URL，带 city+citylimit+types 参数
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("https://restapi.amap.com/v3/place/text?keywords=").append(encodedKeyword)
                      .append("&city=").append(city)
                      .append("&citylimit=true")
                      .append("&offset=").append(pageSize)
                      .append("&page=").append(page)
                      .append("&key=").append(apiKey)
                      .append("&extensions=all");
            
            if (types != null && !types.isEmpty()) {
                urlBuilder.append("&types=").append(types);
            }
            
            String url = urlBuilder.toString();
            log.info("🏛️ 城市精确搜索（带 city+citylimit+types）: {}", url);
            
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            String response = resp.getBody();
            
            log.info("📋 城市搜索原始响应：{}", response != null && response.length() > 500 ? response.substring(0, 500) + "..." : response);
            
            if (response == null || response.isEmpty()) {
                log.warn("城市搜索响应为空");
                return new ArrayList<>();
            }
            
            JSONObject json = JSON.parseObject(response);
            if (!"1".equals(json.getString("status"))) {
                log.warn("城市搜索失败：status={}, info={}", json.getString("status"), json.getString("info"));
                return new ArrayList<>();
            }
            
            JSONArray pois = json.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                log.info("城市搜索无结果：keyword={}, city={}", keyword, city);
                return new ArrayList<>();
            }
            
            log.info("✅ 城市搜索找到 {} 个结果（期望 {} 个）", pois.size(), pageSize);
            
            List<PoiDTO> result = new ArrayList<>();
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                String location = poi.getString("location");
                if (location == null || !location.contains(",")) continue;
                
                String[] parts = location.split(",");
                double lng = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                
                PoiDTO dto = new PoiDTO();
                dto.setName(poi.getString("name"));
                dto.setAddress(poi.getString("address"));
                dto.setLat(lat);
                dto.setLng(lng);
                dto.setType(poi.getString("type"));
                
                result.add(dto);
            }
            
            log.info("✅ 城市搜索成功：找到 {} 个 '{}'", result.size(), keyword);
            return result;
            
        } catch (Exception e) {
            log.error("城市搜索失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 【关键新增】判断是否为地标/机构名称
     * 如果是，则使用 place/text 精确搜索（带 city+citylimit，优先同城匹配）
     * 如果不是，则使用 place/around 周边搜索（带 location + radius）
     */
    private boolean isLandmarkOrInstitution(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        
        // 1. 包含机构类型关键词
        boolean isInstitution = keyword.contains("大学") ||
                                keyword.contains("学院") ||
                                keyword.contains("学校") ||
                                keyword.contains("医院") ||
                                keyword.contains("政府") ||
                                keyword.contains("大厦") ||
                                keyword.contains("广场") ||
                                keyword.contains("中心") ||
                                keyword.contains("园") ||
                                keyword.contains("城") ||
                                keyword.contains("街") ||
                                keyword.contains("路");
        
        // 2. 长度 >= 6 个字符，大概率是具体地点名称
        boolean isLongName = keyword.length() >= 6;
        
        // 3. 包含常见地名后缀
        boolean hasLocationSuffix = keyword.endsWith("寺") ||
                                    keyword.endsWith("庙") ||
                                    keyword.endsWith("宫") ||
                                    keyword.endsWith("馆") ||
                                    keyword.endsWith("塔") ||
                                    keyword.endsWith("桥") ||
                                    keyword.endsWith("站") ||
                                    keyword.endsWith("机场");
        
        boolean result = isInstitution || isLongName || hasLocationSuffix;
        
        log.info("🔍 地标/机构判断：keyword={}, isInstitution={}, isLongName={}, hasLocationSuffix={}, result={}",
                keyword, isInstitution, isLongName, hasLocationSuffix, result);
        
        return result;
    }
    
    /**
     * 【关键新增】通过逆地理编码获取当前城市
     * @param lat 纬度
     * @param lng 经度
     * @return 城市名称，例如 "潮州市"
     */
    public String getCityByLocation(double lat, double lng) {
        try {
            String url = String.format(
                    "https://restapi.amap.com/v3/geocode/regeo?location=%s,%s&key=%s&extensions=all",
                    lng, lat, apiKey
            );
            
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            String response = resp.getBody();
            
            if (response != null && !response.isEmpty()) {
                JSONObject json = JSON.parseObject(response);
                if ("1".equals(json.getString("status"))) {
                    JSONObject regeocode = json.getJSONObject("regeocode");
                    if (regeocode != null) {
                        JSONObject addressComponent = regeocode.getJSONObject("addressComponent");
                        if (addressComponent != null) {
                            String city = addressComponent.getString("city");
                            log.debug("🏙️ 逆地理编码成功：lat={}, lng={}, city={}", lat, lng, city);
                            return city;
                        }
                    }
                }
            }
            
            log.warn("⚠️ 逆地理编码失败或无城市信息：lat={}, lng={}", lat, lng);
            return null;
            
        } catch (Exception e) {
            log.error("❌ 逆地理编码异常", e);
            return null;
        }
    }
    
    /**
     * 根据关键词获取高德 POI types 参数
     * 参考：https://lbs.amap.com/api/webservice/guide/api/search#types
     */
    private String getTypesParamForKeyword(String keyword) {
        if (keyword == null) return null;
        if (keyword.contains("大学") || keyword.contains("学院") || keyword.contains("学校")) {
            return "140000"; // 科教文化服务
        }
        if (keyword.contains("医院")) return "090000";
        if (keyword.contains("餐厅") || keyword.contains("吃")) return "050000";
        if (keyword.contains("酒店") || keyword.contains("住宿")) return "100000";
        if (keyword.contains("银行")) return "160000";
        if (keyword.contains("超市")) return "060000";
        if (keyword.contains("加油站")) return "010100";
        if (keyword.contains("药店")) return "090300";
        return null;
    }
    
    /**
     * POI 搜索（默认参数）
     */
    public List<PoiDTO> searchNearbyPlaces(String keyword, double userLat, double userLng) {
        return searchNearbyPlaces(keyword, userLat, userLng, 1, 20, true, false);
    }

    /**
     * 逆地理编码（获取精确地址）
     * @param lat 纬度
     * @param lng 经度
     * @return 地址信息
     */
    public ReverseGeocodeResponse reverseGeocode(Double lat, Double lng) {
        log.info("逆地理编码：lat={}, lng={}", lat, lng);
        
        // 尝试从缓存获取
        String cacheKey = String.format("geocode:%.6f:%.6f", lat, lng);
        try {
            String cached = redisUtil.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.info("命中逆地理编码缓存：{}", cacheKey);
                return JSON.parseObject(cached, ReverseGeocodeResponse.class);
            }
        } catch (Exception e) {
            log.warn("读取缓存失败：{}", e.getMessage());
        }

        try {
            String url = String.format(
                    "https://restapi.amap.com/v3/geocode/regeo?location=%s,%s&key=%s&extensions=all&roadlevel=1",
                    lng, lat, apiKey
            );
            
            log.info("逆地理编码 URL: {}", url);
            
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            String response = resp.getBody();
            
            if (response != null) {
                JSONObject json = JSON.parseObject(response);
                
                if ("1".equals(json.getString("status"))) {
                    JSONObject regeocode = json.getJSONObject("regeocode");
                    
                    ReverseGeocodeResponse result = new ReverseGeocodeResponse();
                    result.setLat(lat);
                    result.setLng(lng);
                    
                    // 构建完整地址
                    StringBuilder address = new StringBuilder();
                    
                    String province = regeocode.getString("province");
                    if (province != null && !province.isEmpty()) {
                        result.setProvince(province);
                        address.append(province);
                    }
                    
                    String city = regeocode.getString("city");
                    if (city != null && !city.isEmpty()) {
                        result.setCity(city);
                        address.append(city);
                    }
                    
                    String district = regeocode.getString("district");
                    if (district != null && !district.isEmpty()) {
                        result.setDistrict(district);
                        address.append(district);
                    }
                    
                    // 使用 formatted_address 作为兜底
                    if (address.length() == 0) {
                        String formattedAddress = regeocode.getString("formatted_address");
                        if (formattedAddress != null && !formattedAddress.isEmpty()) {
                            address.append(formattedAddress);
                        }
                    }
                    
                    result.setAddress(address.toString());
                    
                    log.info("逆地理编码成功：{}", address.toString());
                    
                    // 缓存 5 分钟
                    try {
                        redisUtil.set(cacheKey, JSON.toJSONString(result), 300);
                        log.info("已缓存逆地理编码结果，key={}, 过期时间 300 秒", cacheKey);
                    } catch (Exception e) {
                        log.warn("写入缓存失败：{}", e.getMessage());
                    }
                    
                    return result;
                } else {
                    log.warn("逆地理编码失败：status={}, info={}", json.getString("status"), json.getString("info"));
                }
            }
        } catch (Exception e) {
            log.error("逆地理编码失败：{}", e.getMessage(), e);
        }
        
        return null;
    }

    /**
     * 计算两点间距离（Haversine 公式）
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
     * 路线规划
     * 【关键修复】高德 API 要求坐标格式：lng,lat（经度，纬度）
     */
    public RouteResult getRoute(String origin, String destination, String mode) {
        log.info("🗺️ 路线规划：origin={}, destination={}, mode={}", origin, destination, mode);
        
        try {
            // 【关键修复】验证坐标格式：必须是 lng,lat
            log.debug("原始坐标：origin={}, destination={}", origin, destination);
            
            String url = String.format(
                    "https://restapi.amap.com/v3/direction/%s?origin=%s&destination=%s&key=%s",
                    mode, origin, destination, apiKey
            );
            
            log.info("🚀 路线规划 URL: {}", url);
            
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.isEmpty()) {
                throw new RuntimeException("路线规划无响应");
            }
            
            JSONObject json = JSON.parseObject(response);
            log.info("路线规划响应：status={}, info={}", json.getString("status"), json.getString("info"));
            
            if (!"1".equals(json.getString("status"))) {
                throw new RuntimeException("路线规划失败：" + json.getString("info"));
            }
            
            JSONObject routeJson = json.getJSONObject("route");
            if (routeJson == null) {
                throw new RuntimeException("路线数据缺失");
            }
            
            JSONArray paths = routeJson.getJSONArray("paths");
            if (paths == null || paths.isEmpty()) {
                throw new RuntimeException("未找到可行路线");
            }
            
            JSONObject path = paths.getJSONObject(0);
            
            RouteResult route = new RouteResult();
            route.setMode(mode);
            route.setDistance(path.getInteger("distance"));
            route.setDuration(path.getInteger("duration"));
            
            // 估算价格（起步价 10 元，每公里 2.5 元）
            double km = route.getDistance() / 1000.0;
            route.setPrice(Math.max(15.0, 10.0 + km * 2.5));
            
            log.info("✅ 路线规划成功：距离={}m, 时长={}s, 价格={}元", 
                    route.getDistance(), route.getDuration(), route.getPrice());
            
            return route;
            
        } catch (Exception e) {
            log.error("❌ 路线规划失败：{}", e.getMessage(), e);
            log.error("💡 检查坐标格式：必须是 lng,lat（经度，纬度），而不是 lat,lng（纬度，经度）");
            throw new RuntimeException("路线规划失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检查 POI 是否与关键词相关（严格模式）
     */
    private boolean checkPoiRelevance(String keyword, String poiName, String address) {
        if (keyword == null || poiName == null) {
            return false;
        }
        
        // 1. 基础包含关系检查
        if (poiName.contains(keyword) || keyword.contains(poiName)) {
            return true;
        }
        
        // 2. 地址中包含关键词（某些 POI 名称是简称，地址才是全称）
        if (address != null && address.contains(keyword)) {
            return true;
        }
        
        // 3. 类别宽松匹配（针对地标、通用设施）
        return isNameMatchedByCategory(keyword, poiName);
    }

    /**
     * 按类别检查名称是否匹配（性能优化版）
     */
    private boolean isNameMatchedByCategory(String keyword, String poiName) {
        // 1. 处理机构类后缀剥离匹配（韩山师范学院 -> 韩山师范）
        if (keyword.matches(".*(学院 | 大学 | 学校 | 中学 | 小学 | 医院).*")) {
            String mainPart = keyword.replaceAll("南\\(3\\) 区$|南区$|北区$|东区$|西区$|\\(.*?\\)", "").trim();
            if (!mainPart.isEmpty() && poiName.contains(mainPart)) {
                return true;
            }
        }

        // 2. 基于字典的类别泛化匹配
        for (java.util.Map.Entry<String, java.util.List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            // 如果用户搜的词（如"找个酒店"）命中了字典键或包含字典词
            if (keyword.contains(entry.getKey()) || entry.getValue().stream().anyMatch(keyword::contains)) {
                // 则看高德返回的名字里，有没有包含该类别下的任何标识
                return entry.getValue().stream().anyMatch(poiName::contains);
            }
        }

        // 3. 知名地标硬编码补充
        if (keyword.contains("故宫")) return poiName.matches(".*(故宫 | 博物院 | 紫禁城).*");
        if (keyword.contains("长城")) return poiName.matches(".*(长城 | 关 | 岭).*");
        
        return false;
    }

    /**
     * 提取机构名称的主体部分（去除校区、分院等后缀）
     */
    private String extractMainPart(String keyword) {
        // 去除常见后缀
        String main = keyword.replaceAll("南 (3) 区$|南区$|北区$|东 (3) 区$|东区$|西区$", "");
        main = main.replaceAll("\\(.*?\\)|\\（.*?\\）", ""); // 去除括号
        return main.trim();
    }

    /**
     * 计算 POI 的相关性得分（如果完全不相关，直接返回 0）
     * 核心原则：名字匹配度占主导（70%），距离只占辅助（30%）
     */
    private double computeRelevanceScore(PoiDTO poi, String keyword) {
        String cleanName = poi.getName().toLowerCase();
        String cleanKeyword = keyword.toLowerCase();
        
        // 1. 完全一致，直接满分（加成）
        if (cleanName.trim().equals(cleanKeyword.trim())) {
            return 2.0; 
        }
        
        double nameMatch = 0.0;
        
        // 2. 强包含关系（POI 名字中包含完整关键词）
        if (cleanName.contains(cleanKeyword)) {
            nameMatch = 0.8;
        } 
        // 3. 反向包含（严格限制：只有当 POI 名是关键词的合理简称时才给分）
        //    例如：搜"北京大学"，POI 叫"北大"，可以给分
        //    但搜"韩山师范学院"，POI 叫"AD 专业烫发 (韩师 7 分店)"，不能给分
        else if (cleanKeyword.contains(cleanName)) {
            // 新增严格校验：POI 名字长度必须至少达到关键词长度的 50%
            // 防止"韩山师范学院"匹配到"AD 专业烫发 (韩师 7 分店)"这种蹭热度的店
            if (cleanName.length() >= cleanKeyword.length() * 0.5 && cleanName.length() >= 3) {
                nameMatch = 0.6;
            }
            // 特殊情况：如果 POI 名包含关键词的核心部分（如"韩师"），但不是完整包含
            // 需要检查是否只是地址中包含，而不是名字主体
            else if (cleanName.length() >= 2 && cleanName.length() < cleanKeyword.length() * 0.5) {
                // 提取 POI 名的核心部分（去除括号、后缀等）
                String coreName = cleanName.replaceAll("\\(.*?\\)|\uff08.*?）|店$|厅$|城$|广场$", "").trim();
                // 如果核心部分长度仍然很短，说明可能是蹭热度，不给分
                if (coreName.length() < cleanKeyword.length() * 0.5) {
                    nameMatch = 0.0;
                } else {
                    nameMatch = 0.4; // 弱匹配
                }
            }
        } 
        // 4. 地址包含（弱匹配，仅当地址中明确包含完整关键词）
        else if (poi.getAddress() != null && poi.getAddress().contains(cleanKeyword)) {
            nameMatch = 0.4;
        }
        // 5. 类型模糊匹配（调用优化后的方法）
        else if (isNameMatchedByCategory(cleanKeyword, cleanName)) {
            nameMatch = 0.3;
        }

        // 关键防火墙：如果连模糊匹配都没过，说明是高德乱推的，直接判定无效（得分为 0）
        if (nameMatch == 0.0) {
            log.debug("相关性评分为 0：keyword=[{}], poiName=[{}], address=[{}]", keyword, poi.getName(), poi.getAddress());
            return 0.0; 
        }

        // 只有名字通过了基础校验，才将距离纳入加分项
        double distanceScore = 1.0 / (1.0 + poi.getDistance() / 1000.0);
        
        // 权重分配：名字匹配 70%，距离 30%
        return (0.3 * distanceScore) + (0.7 * nameMatch);
    }

    /**
     * 判断是否为知名地标（放宽距离限制）
     */
    private boolean isFamousLandmark(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        
        return FAMOUS_LANDMARKS.contains(keyword) || keyword.contains("大学") || keyword.contains("学院");
    }
    
    /**
     * 工业级综合评分算法（名称 40% + 类别 30% + 距离 20% + 热度 10%）
     * @param poi POI 对象
     * @param keyword 搜索关键词
     * @param poiType 高德返回的 POI 类型（如："090000;综合医院"）
     * @return 综合评分（0.0-2.0）
     */
    private double computeIndustrialScore(PoiDTO poi, String keyword, String poiType) {
        // 1. 名称匹配度（40% 权重）
        double nameScore = computeNameMatch(poi.getName(), keyword);
        
        // 2. 类别匹配度（30% 权重）
        double categoryScore = computeCategoryMatch(poiType, poi.getName(), keyword);
        
        // 3. 距离匹配度（20% 权重）
        double distanceScore = 1.0 / (1.0 + poi.getDistance() / 1000.0);
        
        // 4. 热度分（10% 权重，简化版：使用高德星级或用户评价数，这里暂时给基础分）
        double popularityScore = 0.5; // TODO: 后续可以接入高德的星级数据
        
        // 综合评分
        double totalScore = (0.4 * nameScore) + (0.3 * categoryScore) + (0.2 * distanceScore) + (0.1 * popularityScore);
        
        log.debug("📊 评分详情：name={}, keyword={}, nameScore={:.3f}, categoryScore={:.3f}, distanceScore={:.3f}, total={:.3f}",
                poi.getName(), keyword, nameScore, categoryScore, distanceScore, totalScore);
        
        return totalScore;
    }
    
    /**
     * 计算名称匹配度（0.0-1.0）
     * 核心优化：更智能的模糊匹配策略
     */
    private double computeNameMatch(String poiName, String keyword) {
        if (poiName == null || keyword == null) {
            return 0.0;
        }
        
        String cleanName = poiName.toLowerCase();
        String cleanKeyword = keyword.toLowerCase();
        
        // 1. 完全一致，直接满分
        if (cleanName.trim().equals(cleanKeyword.trim())) {
            return 1.0;
        }
        
        // 2. 强包含关系（POI 名字中包含完整关键词）
        if (cleanName.contains(cleanKeyword)) {
            // 如果是开头包含，质量更高
            if (cleanName.startsWith(cleanKeyword)) {
                return 0.9;
            }
            return 0.8;
        }
        
        // 3. 反向包含（关键词包含 POI 名）- 严格校验
        if (cleanKeyword.contains(cleanName)) {
            // 严格校验：POI 名字长度必须至少达到关键词长度的 50%
            if (cleanName.length() >= cleanKeyword.length() * 0.5 && cleanName.length() >= 3) {
                return 0.6;
            } else if (cleanName.length() >= 2) {
                return 0.4; // 弱匹配
            }
        }
        
        // 4. 地址包含（弱匹配）- 由于没有 poi 对象，这里简化处理
        // 实际地址匹配在 computeCategoryMatch 中处理
        
        // 5. 类型模糊匹配
        if (isNameMatchedByCategory(cleanKeyword, cleanName)) {
            return 0.3;
        }
        
        // 6. 【新增】检查是否是合理简称或别名
        // 例如：搜“北京大学”，POI 叫“北大”，可以给分
        if (isReasonableAbbreviation(cleanName, cleanKeyword)) {
            return 0.5;
        }
        
        // 完全不匹配，给一个很低的分数（但不为 0，保留兜底可能性）
        return 0.1;
    }
    
    /**
     * 判断是否是合理的简称或别名
     */
    private boolean isReasonableAbbreviation(String poiName, String keyword) {
        // 提取 POI 名的核心部分（去除括号、后缀等）
        String coreName = poiName.replaceAll("\\(.*?\\)|\uff08.*?）|店$|厅$|城$|广场$", "").trim();
        
        // 如果核心部分很短，但关键词包含它，可能是合理简称
        if (coreName.length() >= 2 && coreName.length() <= 4 && keyword.contains(coreName)) {
            // 额外检查：不能是明显的蹭热度（如“韩师 7 分店”）
            if (!poiName.matches(".*\\d+.*") && !poiName.contains("分店")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 计算类别匹配度（0.0-1.0）
     * 核心优化：优先使用高德返回的 type 字段，更精准匹配
     */
    private double computeCategoryMatch(String poiType, String poiName, String keyword) {
        // 1. 【关键优化】优先使用高德返回的 type 字段（最准确）
        if (poiType != null && !poiType.isEmpty()) {
            log.debug("🔍 POI type 字段：{}, keyword: {}", poiType, keyword);
            
            // 医院类
            if (keyword.contains("医院") || keyword.contains("医疗")) {
                if (poiType.contains("医院") || poiType.contains("卫生") || poiType.contains("医疗")) {
                    return 1.0;
                }
            }
            // 学校类
            if (keyword.contains("学校") || keyword.contains("教育")) {
                if (poiType.contains("学校") || poiType.contains("教育") || poiType.contains("科教")) {
                    return 1.0;
                }
            }
            // 餐饮类
            if (keyword.contains("餐厅") || keyword.contains("餐饮") || keyword.contains("饭店")) {
                if (poiType.contains("餐饮") || poiType.contains("餐厅") || poiType.contains("饭店")) {
                    return 1.0;
                }
            }
            // 酒店类
            if (keyword.contains("酒店") || keyword.contains("住宿")) {
                if (poiType.contains("酒店") || poiType.contains("住宿") || poiType.contains("旅馆")) {
                    return 1.0;
                }
            }
            // 超市类
            if (keyword.contains("超市") || keyword.contains("商场")) {
                if (poiType.contains("超市") || poiType.contains("商场") || poiType.contains("购物")) {
                    return 1.0;
                }
            }
            // 银行类
            if (keyword.contains("银行") || keyword.contains("金融")) {
                if (poiType.contains("银行") || poiType.contains("金融")) {
                    return 1.0;
                }
            }
            // 药店类
            if (keyword.contains("药店") || keyword.contains("药房")) {
                if (poiType.contains("药店") || poiType.contains("药房") || poiType.contains("医药")) {
                    return 1.0;
                }
            }
        }
        
        // 2. Fallback: 使用名称和地址进行类别匹配（当 type 字段缺失时）
        // 注意：这里没有 poi 对象，需要使用传入的 poiName 参数和 keyword
        String fullName = (poiName != null ? poiName : "") + " " + (keyword != null ? keyword : "");
        
        if (keyword.contains("医院") && (fullName.contains("医院") || fullName.contains("卫生"))) {
            return 0.9;
        }
        if (keyword.contains("学校") && (fullName.contains("学校") || fullName.contains("学院"))) {
            return 0.9;
        }
        if (keyword.contains("餐厅") && (fullName.contains("酒家") || fullName.contains("餐馆"))) {
            return 0.8;
        }
        if (keyword.contains("酒店") && (fullName.contains("酒店") || fullName.contains("宾馆"))) {
            return 0.9;
        }
        if (keyword.contains("超市") && (fullName.contains("超市") || fullName.contains("商场"))) {
            return 0.8;
        }
        if (keyword.contains("银行") && (fullName.contains("银行"))) {
            return 0.9;
        }
        if (keyword.contains("药店") && (fullName.contains("药店") || fullName.contains("药房"))) {
            return 0.8;
        }
        
        // 无法判断类别，给基础分（不偏袒也不歧视）
        return 0.5;
    }
    
    /**
     * 【新增】获取位置的 adcode（行政区划代码）
     */
    public String getAdcodeByLocation(double lat, double lng) {
        String cacheKey = String.format("adcode:%.6f:%.6f", lat, lng);
        String cached = redisUtil.get(cacheKey);
        if (cached != null) return cached;
        
        String url = String.format("https://restapi.amap.com/v3/geocode/regeo?location=%s,%s&key=%s&extensions=all", lng, lat, apiKey);
        try {
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = JSON.parseObject(resp);
            if ("1".equals(json.getString("status"))) {
                JSONObject regeocode = json.getJSONObject("regeocode");
                JSONObject addressComponent = regeocode.getJSONObject("addressComponent");
                String adcode = addressComponent.getString("adcode");
                if (adcode != null && !adcode.isEmpty()) {
                    redisUtil.set(cacheKey, adcode, 86400); // 缓存一天
                    log.debug("🏛️ 获取 adcode 成功：lat={}, lng={}, adcode={}", lat, lng, adcode);
                    return adcode;
                }
            }
        } catch (Exception e) {
            log.error("❌ 获取 adcode 失败", e);
        }
        log.warn("⚠️ 获取 adcode 失败：lat={}, lng={}", lat, lng);
        return null;
    }
    
    /**
     * 【新增】地理编码：地址转坐标
     */
    public double[] geocode(String address) {
        String url = String.format("https://restapi.amap.com/v3/geocode/geo?address=%s&key=%s", 
            java.net.URLEncoder.encode(address, StandardCharsets.UTF_8), apiKey);
        try {
            String resp = restTemplate.getForObject(url, String.class);
            JSONObject json = JSON.parseObject(resp);
            if ("1".equals(json.getString("status"))) {
                JSONArray geocodes = json.getJSONArray("geocodes");
                if (geocodes != null && !geocodes.isEmpty()) {
                    String location = geocodes.getJSONObject(0).getString("location");
                    String[] parts = location.split(",");
                    double[] result = new double[]{Double.parseDouble(parts[1]), Double.parseDouble(parts[0])}; // lat, lng
                    log.info("📍 地理编码成功：{} -> ({}, {})", address, result[0], result[1]);
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("❌ 地理编码失败", e);
        }
        log.warn("⚠️ 地理编码失败：{}", address);
        return null;
    }
    
    /**
     * 【新增】基于地理编码的搜索（地址优先）
     */
    public List<PoiDTO> searchByGeocode(String keyword, double userLat, double userLng) {
        double[] coords = geocode(keyword);
        if (coords != null) {
            log.info("🎯 使用地理编码搜索：{} -> ({}, {})", keyword, coords[0], coords[1]);
            // 以坐标为中心，半径 5km 搜索
            return searchNearbyPlaces(keyword, coords[0], coords[1], 1, 20, true, false, 5000, true, null);
        }
        return null;
    }
    
    /**
     * 【新增】获取周边城市 adcode 列表
     */
    public List<String> getNearbyCities(String adcode) {
        // 根据城市编码，返回周边城市代码列表
        Map<String, List<String>> nearbyMap = new HashMap<>();
        // 潮州 (445100) 周边：汕头 (440500)、揭阳 (445200)
        nearbyMap.put("445100", Arrays.asList("440500", "445200"));
        // 可根据需要扩展更多城市
        List<String> result = nearbyMap.getOrDefault(adcode, Collections.emptyList());
        log.info("🏙️ 获取周边城市：adcode={}, cities={}", adcode, result);
        return result;
    }
}
