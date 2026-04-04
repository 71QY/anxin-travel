package com.anxin.travel.module.map.controller;

import com.alibaba.fastjson.JSON;
import com.anxin.travel.agent.model.CandidateDestination;
import com.anxin.travel.agent.service.AgentService;
import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.map.client.AmapClient;
import com.anxin.travel.module.map.client.TencentMapClient;
import com.anxin.travel.module.map.dto.PoiDTO;
import com.anxin.travel.module.map.dto.ReverseGeocodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapController {

    private final AmapClient amapClient;
    private final AgentService agentService;
    private final TencentMapClient tencentMapClient;
    private final RedisUtil redisUtil;

    @GetMapping("/search-destination")
    public Result<List<PoiDTO>> searchDestination(
            @RequestParam String keyword,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        log.info("============ 主页搜索框请求 ============");
        log.info("搜索目的地：keyword={}, lat={}, lng={}", keyword, lat, lng);
        
        // 【自动定位】如果前端没有传递坐标，尝试 IP 定位
        if (lat == null || lng == null) {
            log.warn("⚠️ 前端未传递坐标，尝试 IP 定位...");
            try {
                double[] ipLocation = agentService.getIpLocationForController();
                if (ipLocation != null) {
                    lat = ipLocation[0];
                    lng = ipLocation[1];
                    log.info("✅ IP 定位成功：lat={}, lng={}", lat, lng);
                } else {
                    return Result.error("无法获取您的位置信息，请在地图上手动选择起点");
                }
            } catch (Exception e) {
                log.error("IP 定位失败", e);
                return Result.error("定位失败：" + e.getMessage());
            }
        }
        
        log.info("使用坐标：lat={}, lng={}", lat, lng);
        
        try {
            // 【关键修复】优先使用腾讯地图搜索（通过 AmapClient 的 searchNearbyPlaces 内部会判断）
            // 注意：这里需要模拟 sessionId 和 userId，实际应该从前端传递
            String sessionId = "map_search_" + System.currentTimeMillis();
            Long userId = 0L;  // 临时用户 ID
            
            // 调用智能体服务，返回 AgentResponse 对象
            Object result = agentService.processIntention(sessionId, userId, keyword, lat, lng);
            
            List<PoiDTO> poiList;
            if (result instanceof com.anxin.travel.agent.dto.AgentResponse) {
                // 从 AgentResponse 中提取 places 字段
                com.anxin.travel.agent.dto.AgentResponse response = 
                    (com.anxin.travel.agent.dto.AgentResponse) result;
                poiList = response.getPlaces();
                log.info("✅ 智能搜索成功：找到 {} 个地点（已按评分降序排列）", poiList != null ? poiList.size() : 0);
                
                // 【关键】确保返回给前端的列表已经按评分排序
                if (poiList != null && !poiList.isEmpty()) {
                    log.info("📊 返回给前端的 POI 列表（按评分从高到低）:");
                    for (int i = 0; i < Math.min(5, poiList.size()); i++) {
                        PoiDTO poi = poiList.get(i);
                        log.info("  #{}: {} - 评分：{:.4f}", i + 1, poi.getName(), poi.getScore());
                    }
                }
            } else if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<PoiDTO> searchResult = (List<PoiDTO>) result;
                poiList = searchResult;
                log.info("✅ 智能搜索成功：找到 {} 个地点", poiList.size());
            } else {
                log.warn("⚠️ 智能搜索返回未知类型：{}", result.getClass().getName());
                poiList = new ArrayList<>();
            }
            
            log.info("======================================");
            return Result.success(poiList != null ? poiList : new ArrayList<>());
            
        } catch (Exception e) {
            log.error("❌ 智能搜索失败，降级处理", e);
            // 异常降级处理：使用腾讯地图
            List<PoiDTO> poiResults = tencentMapClient.searchNearbyPlaces(keyword, lat, lng, 5000);
            return Result.success(poiResults);
        }
    }

    @GetMapping("/geocode/reverse")
    public Result<ReverseGeocodeResponse> reverseGeocode(
            @RequestParam Double lat,
            @RequestParam Double lng) {
        log.info("逆地理编码请求：lat={}, lng={}", lat, lng);
        
        if (lat == null || lng == null) {
            return Result.error("经纬度不能为空");
        }
        
        ReverseGeocodeResponse response = amapClient.reverseGeocode(lat, lng);
        
        if (response == null) {
            return Result.error("逆地理编码失败");
        }
        
        return Result.success(response);
    }

    @GetMapping("/poi/nearby")
    public Result<List<PoiDTO>> searchNearbyPoi(
            @RequestParam String keyword,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "5000") int radius,
            @RequestParam(defaultValue = "false") boolean nationwide) {
        log.info("POI 搜索请求：keyword={}, lat={}, lng={}, page={}, pageSize={}, radius={}, nationwide={}", 
                keyword, lat, lng, page, pageSize, radius, nationwide);
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.error("关键词不能为空");
        }
        
        // 【自动定位】如果前端没有传递坐标，尝试 IP 定位
        if (lat == null || lng == null) {
            log.warn("⚠️ 前端未传递坐标，尝试 IP 定位...");
            try {
                double[] ipLocation = agentService.getIpLocationForController();
                if (ipLocation != null) {
                    lat = ipLocation[0];
                    lng = ipLocation[1];
                    log.info("✅ IP 定位成功：lat={}, lng={}", lat, lng);
                } else {
                    return Result.error("无法获取您的位置信息，请在地图上手动选择起点");
                }
            } catch (Exception e) {
                log.error("IP 定位失败", e);
                return Result.error("定位失败：" + e.getMessage());
            }
        }
        
        // 【关键修复】统一使用智能体搜索策略（腾讯地图优先）
        try {
            String sessionId = "map_poi_" + System.currentTimeMillis();
            Long userId = 0L;  // 临时用户 ID
            
            // 调用智能体服务，使用腾讯地图优先的搜索策略
            Object result = agentService.processIntention(sessionId, userId, keyword, lat, lng);
            
            List<PoiDTO> poiList;
            if (result instanceof com.anxin.travel.agent.dto.AgentResponse) {
                // 从 AgentResponse 中提取 places 字段
                com.anxin.travel.agent.dto.AgentResponse response = 
                    (com.anxin.travel.agent.dto.AgentResponse) result;
                poiList = response.getPlaces();
                log.info("✅ 智能搜索成功：找到 {} 个地点（已按评分降序排列）", poiList != null ? poiList.size() : 0);
                
                // 【关键】确保返回给前端的列表已经按评分排序
                if (poiList != null && !poiList.isEmpty()) {
                    log.info("📊 返回给前端的 POI 列表（按评分从高到低）:");
                    for (int i = 0; i < Math.min(5, poiList.size()); i++) {
                        PoiDTO poi = poiList.get(i);
                        log.info("  #{}: {} - 评分：{:.4f}", i + 1, poi.getName(), poi.getScore());
                    }
                }
            } else if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<PoiDTO> searchResult = (List<PoiDTO>) result;
                poiList = searchResult;
                log.info("✅ 智能搜索成功：找到 {} 个地点", poiList.size());
            } else {
                log.warn("⚠️ 智能搜索返回非列表类型：{}", result.getClass().getName());
                poiList = new ArrayList<>();
            }
            
            // 【注意】不再进行二次过滤，因为智能体服务内部已经完成过滤
            log.info("POI 搜索成功：找到 {} 个地点", poiList.size());
            return Result.success(poiList);
            
        } catch (Exception e) {
            log.error("❌ 智能搜索失败", e);
            return Result.error("搜索失败：" + e.getMessage());
        }
    }

    @GetMapping("/poi/detail")
    public Result<Map<String, Object>> getPoiDetailAndRoute(
            @RequestParam String poiName,
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "driving") String mode) {
        log.info("============ POI 详情请求（用户点击地点）============");
        log.info("POI 名称：{}, 坐标：({}, {}), 出行方式：{}", poiName, lat, lng, mode);
        
        if (poiName == null || poiName.trim().isEmpty()) {
            log.error("❌ POI 名称为空");
            return Result.error("POI 名称不能为空");
        }
        
        if (lat == null || lng == null) {
            log.error("❌ 坐标为空");
            return Result.error("起点坐标不能为空");
        }
        
        try {
            // 修复：不再重新搜索，而是直接使用传入的 poiName 和坐标
            // 前端应该在点击 POI 时已经传入了精确的坐标，不需要再次搜索
            
            // 新增：直接使用前端传入的坐标构建 POI 对象
            PoiDTO targetPoi = new PoiDTO();
            targetPoi.setName(poiName);
            targetPoi.setLat(lat);
            targetPoi.setLng(lng);
            targetPoi.setAddress(poiName); // 地址暂时用名称填充
            targetPoi.setDistance(0.0);
            targetPoi.setScore(5.0); // 【关键】设置默认满分评分（因为用户手动选择的地点）
            
            log.info("✅ 使用前端传入的坐标：name={}, lat={}, lng={}", targetPoi.getName(), lat, lng);
            log.info("⚠️ 注意：此方法不会触发新的搜索，直接使用传入坐标");
            
            // 新增：将选中的 POI 保存到 MemoryService，以便后续下单
            // 使用 sessionId 来标识当前用户的会话
            String sessionId = "map_click_" + System.currentTimeMillis();
            List<PoiDTO> candidates = java.util.Collections.singletonList(targetPoi);
            agentService.saveCandidatesToMemory(sessionId, candidates);
            log.info("💾 已保存选中 POI 到内存：sessionId={}, name={}, lat={}, lng={}", 
                sessionId, targetPoi.getName(), targetPoi.getLat(), targetPoi.getLng());
            
            // 3. 计算路线
            String origin = String.format("%.6f,%.6f", lat, lng);
            String destination = String.format("%.6f,%.6f", targetPoi.getLat(), targetPoi.getLng());
            
            log.info("🗺️ 开始计算路线：origin={}, destination={}", origin, destination);
            com.anxin.travel.module.map.dto.RouteResult route = amapClient.getRoute(origin, destination, mode);
            
            // 4. 构建响应数据
            Map<String, Object> result = new HashMap<>();
            result.put("poi", targetPoi);
            result.put("route", route);
            result.put("canOrder", true);
            result.put("sessionId", sessionId); // 返回 sessionId 给前端，以便后续下单时使用
            
            log.info("✅ POI 详情获取成功：name={}, 距离={}m, 时长={}s, 预估价格={}元",
                    targetPoi.getName(), route.getDistance(), route.getDuration(), route.getPrice());
            log.info("📋 返回数据结构：{poi: {...}, route: {...}, canOrder: true, sessionId: ...}");
            log.info("======================================");
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("❌ 获取 POI 详情失败", e);
            log.info("======================================");
            return Result.error("获取 POI 详情失败：" + e.getMessage());
        }
    }

    /**
     * 地图点击确认下单（新增接口）
     */
    @PostMapping("/order/confirm")
    public Result<Object> confirmOrderFromMapClick(
            @RequestParam String sessionId,
            @RequestParam String poiName) {
        log.info("============ 地图点击确认下单 ============");
        log.info("sessionId: {}, poiName: {}", sessionId, poiName);
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.error("sessionId 不能为空");
        }
        
        if (poiName == null || poiName.trim().isEmpty()) {
            return Result.error("POI 名称不能为空");
        }
        
        try {
            // 从 UserContext 获取当前登录用户 ID
            Long userId = com.anxin.travel.common.util.UserContext.getUserId();
            log.info("当前用户 ID: {}", userId);
            
            // 调用 AgentService 创建订单
            Object orderResult = agentService.createOrderFromMapClick(sessionId, userId, poiName);
            
            if (orderResult instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) orderResult;
                if (resultMap.containsKey("success") && !Boolean.parseBoolean(resultMap.get("success").toString())) {
                    return Result.error(resultMap.get("message").toString());
                }
            }
            
            log.info("✅ 地图点击下单成功：{}", orderResult);
            log.info("======================================");
            return Result.success(orderResult);
            
        } catch (Exception e) {
            log.error("❌ 地图点击下单失败", e);
            log.info("======================================");
            return Result.error("下单失败：" + e.getMessage());
        }
    }

    @GetMapping("/route")
    public Result<com.anxin.travel.module.map.dto.RouteResult> getRoute(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(defaultValue = "driving") String mode) {
        log.info("路线规划请求：origin={}, destination={}, mode={}", origin, destination, mode);
        
        if (origin == null || origin.isEmpty() || destination == null || destination.isEmpty()) {
            return Result.error("起点和终点坐标不能为空");
        }
        
        try {
            com.anxin.travel.module.map.dto.RouteResult route = amapClient.getRoute(origin, destination, mode);
            return Result.success(route);
        } catch (Exception e) {
            log.error("路线规划失败", e);
            return Result.error("路线规划失败：" + e.getMessage());
        }
    }
}
