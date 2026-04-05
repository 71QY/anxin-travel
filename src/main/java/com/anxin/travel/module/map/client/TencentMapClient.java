package com.anxin.travel.module.map.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.map.dto.PoiDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 腾讯地图 WebService API 客户端
 * 文档：https://lbs.qq.com/service/webService/webServiceGuide
 */
@Slf4j
@Component
public class TencentMapClient {

    @Value("${anxin.tencent.map.key}")
    private String apiKey;

    private final RedisUtil redisUtil;
    private final RestTemplate restTemplate;

    public TencentMapClient(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
        this.restTemplate = createRestTemplate();
        log.info("✅ TencentMapClient 初始化完成");
    }

    /**
     * 创建带有浏览器请求头和超时控制的 RestTemplate
     */
    private RestTemplate createRestTemplate() {
        try {
            // 使用 HttpComponents Client 支持更精细的超时控制
            org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = 
                org.apache.hc.client5.http.impl.classic.HttpClients.custom()
                    .setConnectionManager(createConnectionManager())
                    .build();
            
            org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory = 
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(httpClient);
            
            RestTemplate restTemplate = new RestTemplate(factory);
            
            // 设置请求拦截器，添加浏览器级别的请求头
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                request.getHeaders().set("Accept", "application/json, text/plain, */*");
                request.getHeaders().set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                return execution.execute(request, body);
            });
            
            log.info("✅ TencentMapClient RestTemplate 配置完成");
            return restTemplate;
            
        } catch (Exception e) {
            log.error("❌ TencentMapClient RestTemplate 配置失败", e);
            throw new RuntimeException("RestTemplate 配置失败：" + e.getMessage(), e);
        }
    }

    /**
     * 关键词输入提示 API
     * 接口文档：https://lbs.qq.com/service/webService/webServiceGuide/placeSuggestion
     * @param keyword 搜索关键词
     * @return POI 列表
     */
    public List<PoiDTO> searchSuggestions(String keyword) {
        String cacheKey = String.format("tencent_suggestion:%s", keyword);
        
        // 1. 先检查 Redis 缓存
        try {
            String cached = redisUtil.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.info("命中腾讯关键词提示缓存：{}, 结果数：{}", cacheKey, JSON.parseArray(cached).size());
                return JSON.parseArray(cached, PoiDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取缓存失败：{}", e.getMessage());
        }

        String url = String.format(
            "https://apis.map.qq.com/ws/place/v1/suggestion/?keyword=%s&key=%s",
            encodeValue(keyword), apiKey
        );
        
        log.info("🔍 腾讯关键词提示请求 URL: {}", url);
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = JSON.parseObject(response.getBody());
            
            if (json.getInteger("status") == 0) {
                JSONArray data = json.getJSONArray("data");
                List<PoiDTO> result = new ArrayList<>();
                
                if (data != null && !data.isEmpty()) {
                    for (int i = 0; i < data.size(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        PoiDTO dto = new PoiDTO();
                        dto.setName(item.getString("title"));
                        dto.setAddress(item.getString("address"));
                        
                        JSONObject location = item.getJSONObject("location");
                        if (location != null) {
                            dto.setLat(location.getDoubleValue("lat"));
                            dto.setLng(location.getDoubleValue("lng"));
                        }
                        
                        dto.setId(item.getString("id"));
                        result.add(dto);
                    }
                    
                    log.info("✅ 腾讯关键词提示成功，找到 {} 个结果", result.size());
                    
                    // 写入缓存（5 分钟）
                    if (!result.isEmpty()) {
                        try {
                            redisUtil.set(cacheKey, JSON.toJSONString(result), 300);
                            log.info("已缓存腾讯关键词提示结果，key={}, 过期时间 300 秒", cacheKey);
                        } catch (Exception e) {
                            log.warn("写入缓存失败：{}", e.getMessage());
                        }
                    }
                } else {
                    log.warn("⚠️ 腾讯关键词提示无结果，keyword={}", keyword);
                }
                
                return result;
            } else {
                log.error("❌ 腾讯关键词提示失败，状态码：{}, 信息：{}", 
                         json.getInteger("status"), json.getString("message"));
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("❌ 腾讯关键词提示请求失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 周边搜索 API (POI 搜索)
     * 接口文档：https://lbs.qq.com/service/webService/webServiceGuide/placeSearch
     * @param keyword 搜索关键词
     * @param lat 用户纬度
     * @param lng 用户经度
     * @param radius 搜索半径（米）
     * @return POI 列表
     */
    public List<PoiDTO> searchNearbyPlaces(String keyword, double lat, double lng, int radius) {
        String cacheKey = String.format("tencent_poi:%s:%.6f:%.6f:%d", keyword, lat, lng, radius);
        
        // 1. 先检查 Redis 缓存，如果命中则直接返回
        try {
            String cached = redisUtil.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.info("命中腾讯 POI 缓存：{}, 结果数：{}", cacheKey, JSON.parseArray(cached).size());
                return JSON.parseArray(cached, PoiDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取缓存失败：{}", e.getMessage());
        }

        String url = String.format(
            "https://apis.map.qq.com/ws/place/v1/search?boundary=nearby(%f,%f,%d)&keyword=%s&key=%s",
            lat, lng, radius, encodeValue(keyword), apiKey
        );
        
        log.info("🔍 腾讯周边搜索请求 URL: {}", url);
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = JSON.parseObject(response.getBody());
            List<PoiDTO> result = new ArrayList<>();
            
            if (json.getInteger("status") == 0) {
                JSONArray data = json.getJSONArray("data");
                
                if (data != null && !data.isEmpty()) {
                    for (int i = 0; i < data.size(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        PoiDTO dto = new PoiDTO();
                        
                        dto.setName(item.getString("title"));
                        dto.setAddress(item.getString("address"));
                        
                        JSONObject location = item.getJSONObject("location");
                        if (location != null) {
                            double poiLat = location.getDoubleValue("lat");
                            double poiLng = location.getDoubleValue("lng");
                            dto.setLat(poiLat);
                            dto.setLng(poiLng);
                            
                            // 计算距离
                            dto.setDistance(calculateDistance(lat, lng, poiLat, poiLng));
                        }
                        
                        dto.setId(item.getString("id"));
                        
                        // 获取 POI 类型
                        String poiType = item.getString("type");
                        dto.setType(poiType);
                        
                        result.add(dto);
                    }
                    
                    log.info("✅ 腾讯周边搜索成功，找到 {} 个结果", result.size());
                    
                    // 写入缓存（10 分钟）
                    if (!result.isEmpty()) {
                        try {
                            redisUtil.set(cacheKey, JSON.toJSONString(result), 600);
                            log.info("已缓存腾讯 POI 结果，key={}, 过期时间 600 秒", cacheKey);
                        } catch (Exception e) {
                            log.warn("写入缓存失败：{}", e.getMessage());
                        }
                    }
                } else {
                    log.warn("⚠️ 腾讯周边搜索无结果，keyword={}, status={}, message={}", 
                            keyword, json.getInteger("status"), json.getString("message"));
                }
            } else {
                log.error("❌ 腾讯周边搜索失败，状态码：{}, 信息：{}", 
                         json.getInteger("status"), json.getString("message"));
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 腾讯周边搜索请求失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 地址解析（地理编码）
     * 接口文档：https://lbs.qq.com/service/webService/webServiceGuide/geocoder
     * @param address 地址
     * @return 坐标数组 [lat, lng]，失败返回 null
     */
    public double[] geocode(String address) {
        String cacheKey = String.format("tencent_geocode:%s", address);
        
        // 1. 先检查 Redis 缓存
        try {
            String cached = redisUtil.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.info("命中腾讯地理编码缓存：{}", cacheKey);
                double[] coords = JSON.parseObject(cached, double[].class);
                return coords;
            }
        } catch (Exception e) {
            log.warn("读取缓存失败：{}", e.getMessage());
        }

        String url = String.format(
            "https://apis.map.qq.com/ws/geocoder/v1/?address=%s&key=%s",
            encodeValue(address), apiKey
        );
        
        log.info("🗺️ 腾讯地理编码请求 URL: {}", url);
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = JSON.parseObject(response.getBody());
            
            if (json.getInteger("status") == 0) {
                JSONObject result = json.getJSONObject("result");
                if (result != null) {
                    JSONObject location = result.getJSONObject("location");
                    if (location != null) {
                        double lat = location.getDoubleValue("lat");
                        double lng = location.getDoubleValue("lng");
                        double[] coords = new double[]{lat, lng};
                        
                        log.info("✅ 腾讯地理编码成功：address={}, lat={}, lng={}", address, lat, lng);
                        
                        // 写入缓存（30 分钟）
                        try {
                            redisUtil.set(cacheKey, JSON.toJSONString(coords), 1800);
                            log.info("已缓存腾讯地理编码结果，key={}, 过期时间 1800 秒", cacheKey);
                        } catch (Exception e) {
                            log.warn("写入缓存失败：{}", e.getMessage());
                        }
                        
                        return coords;
                    }
                }
                
                log.warn("⚠️ 腾讯地理编码无结果，address={}", address);
                return null;
            } else {
                log.error("❌ 腾讯地理编码失败，状态码：{}, 信息：{}", 
                         json.getInteger("status"), json.getString("message"));
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ 腾讯地理编码请求失败", e);
            return null;
        }
    }

    /**
     * 创建连接管理器
     */
    private org.apache.hc.client5.http.io.HttpClientConnectionManager createConnectionManager() {
        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager = 
            new org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50);
        connectionManager.setDefaultMaxPerRoute(20);
        return connectionManager;
    }

    /**
     * URL 编码辅助方法
     */
    private String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            log.error("❌ URL 编码失败", e);
            return value;
        }
    }

    /**
     * 计算两点距离（Haversine 公式）
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
     * 路线规划 API（驾车）
     * 接口文档：https://lbs.qq.com/service/webService/webServiceGuide/direction
     * @param origin 起点坐标，格式："lng,lat"
     * @param destination 终点坐标，格式："lng,lat"
     * @return 路线结果，失败返回 null
     */
    public com.anxin.travel.module.map.dto.RouteResult getRoute(String origin, String destination) {
        String cacheKey = String.format("tencent_route:%s:%s", origin, destination);
        
        // 1. 先检查 Redis 缓存
        try {
            String cached = redisUtil.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.info("命中腾讯路线规划缓存：{}", cacheKey);
                return JSON.parseObject(cached, com.anxin.travel.module.map.dto.RouteResult.class);
            }
        } catch (Exception e) {
            log.warn("读取缓存失败：{}", e.getMessage());
        }

        String url = String.format(
            "https://apis.map.qq.com/ws/direction/v1/driving/?from=%s&to=%s&key=%s",
            origin, destination, apiKey
        );
        
        log.info("🗺️ 腾讯路线规划请求 URL: {}", url);
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = JSON.parseObject(response.getBody());
            
            if (json.getInteger("status") == 0) {
                JSONObject result = json.getJSONObject("result");
                if (result != null) {
                    JSONArray routes = result.getJSONArray("routes");
                    if (routes != null && !routes.isEmpty()) {
                        JSONObject route = routes.getJSONObject(0);
                        
                        com.anxin.travel.module.map.dto.RouteResult routeResult = new com.anxin.travel.module.map.dto.RouteResult();
                        routeResult.setDistance(route.getInteger("distance"));  // 单位：米
                        routeResult.setDuration(route.getInteger("duration"));  // 单位：秒
                        
                        // 估算价格（按每公里 2.5 元计算）
                        double price = route.getInteger("distance") / 1000.0 * 2.5;
                        routeResult.setPrice(Math.round(price * 100.0) / 100.0);  // 保留两位小数
                        
                        log.info("✅ 腾讯路线规划成功：距离={}m, 时长={}s, 价格={}元", 
                                routeResult.getDistance(), routeResult.getDuration(), routeResult.getPrice());
                        
                        // 写入缓存（5 分钟）
                        try {
                            redisUtil.set(cacheKey, JSON.toJSONString(routeResult), 300);
                            log.info("已缓存腾讯路线规划结果，key={}, 过期时间 300 秒", cacheKey);
                        } catch (Exception e) {
                            log.warn("写入缓存失败：{}", e.getMessage());
                        }
                        
                        return routeResult;
                    }
                }
                
                log.warn("⚠️ 腾讯路线规划无结果");
                return null;
            } else {
                log.error("❌ 腾讯路线规划失败，状态码：{}, 信息：{}", 
                         json.getInteger("status"), json.getString("message"));
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ 腾讯路线规划请求失败", e);
            return null;
        }
    }

    /**
     * 获取 API Key（供测试使用）
     */
    public String getApiKey() {
        return apiKey;
    }
}
