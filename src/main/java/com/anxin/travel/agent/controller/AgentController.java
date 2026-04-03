package com.anxin.travel.agent.controller;

import com.anxin.travel.agent.dto.AgentResponse;
import com.anxin.travel.agent.service.AgentService;
import com.anxin.travel.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 智能搜索接口（统一返回 Result<AgentResponse> 格式）
     * 前端调用示例：
     * POST /api/agent/search
     * Body: {
     *   "sessionId": "xxx",
     *   "keyword": "医院",
     *   "lat": 23.65,
     *   "lng": 116.67
     * }
     */
    @PostMapping("/search")
    public Result<AgentResponse> searchDestination(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") Long userId) {

        String sessionId = (String) request.get("sessionId");
        String keyword = (String) request.get("keyword");
        Double lat = (Double) request.get("lat");
        Double lng = (Double) request.get("lng");

        log.info("============ 智能搜索请求 ============");
        log.info("sessionId={}, keyword={}, lat={}, lng={}", sessionId, keyword, lat, lng);

        try {
            // 直接获取统一的 AgentResponse 响应
            AgentResponse response = agentService.processIntention(sessionId, userId, keyword, lat, lng);
            
            log.info("✅ 搜索成功，找到 {} 个地点", response.getPlaces() != null ? response.getPlaces().size() : 0);
            return Result.success(response);
        } catch (Exception e) {
            log.error("搜索失败", e);
            return Result.error("搜索失败：" + e.getMessage());
        }
    }

    /**
     * 确认选择接口（返回 Result<AgentResponse> 格式）
     * 前端调用示例：
     * POST /api/agent/confirm
     * Body: {
     *   "sessionId": "xxx",
     *   "selectedPoiName": "韩山师范学院"
     * }
     */
    @PostMapping("/confirm")
    public Result<AgentResponse> confirmSelection(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") Long userId) {

        String sessionId = (String) request.get("sessionId");
        String selectedPoiName = (String) request.get("selectedPoiName");

        log.info("============ 确认选择请求 ============");
        log.info("sessionId={}, userId={}, selectedPoiName={}",
                sessionId, userId, selectedPoiName);

        try {
            AgentResponse response = agentService.confirmSelection(sessionId, userId, selectedPoiName);

            log.info("✅ 确认成功");
            log.info("======================================");
            return Result.success(response);

        } catch (Exception e) {
            log.error("❌ 确认失败", e);
            log.info("======================================");
            return Result.error("确认失败：" + e.getMessage());
        }
    }

    /**
     * 图片识别接口（返回 Result<AgentResponse> 格式）
     * 前端调用示例：
     * POST /api/agent/image
     * Body: {
     *   "sessionId": "xxx",
     *   "imageBase64": "data:image/jpeg;base64,...",
     *   "lat": 23.65,
     *   "lng": 116.67
     * }
     */
    @PostMapping("/image")
    public Result<AgentResponse> processImage(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") Long userId) {

        String sessionId = (String) request.get("sessionId");
        String imageBase64 = (String) request.get("imageBase64");
        Double lat = (Double) request.get("lat");
        Double lng = (Double) request.get("lng");

        log.info("============ 图片识别请求 ============");
        log.info("sessionId={}, userId={}, lat={}, lng={}, imageLength={}",
                sessionId, userId, lat, lng, imageBase64 != null ? imageBase64.length() : 0);

        try {
            AgentResponse response = agentService.processImage(sessionId, userId, imageBase64, lat, lng);

            log.info("✅ 图片识别成功");
            log.info("======================================");
            return Result.success(response);

        } catch (Exception e) {
            log.error("❌ 图片识别失败", e);
            log.info("======================================");
            return Result.error("图片识别失败：" + e.getMessage());
        }
    }

    /**
     * 清理会话接口
     */
    @PostMapping("/cleanup")
    public Result<Void> cleanupSession(
            @RequestBody Map<String, Object> request) {

        String sessionId = (String) request.get("sessionId");

        log.info("清理会话：sessionId={}", sessionId);

        try {
            agentService.cleanupSession(sessionId);
            return Result.success(null);
        } catch (Exception e) {
            log.error("清理会话失败", e);
            return Result.error("清理失败：" + e.getMessage());
        }
    }

    /**
     * 更新用户位置
     */
    @PostMapping("/location")
    public Result<Void> updateLocation(
            @RequestBody Map<String, Object> request) {

        String sessionId = (String) request.get("sessionId");
        Double lat = (Double) request.get("lat");
        Double lng = (Double) request.get("lng");

        log.info("更新位置：sessionId={}, lat={}, lng={}", sessionId, lat, lng);

        try {
            agentService.updateUserLocation(sessionId, lat, lng);
            return Result.success(null);
        } catch (Exception e) {
            log.error("更新位置失败", e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }
}
