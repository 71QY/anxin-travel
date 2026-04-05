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

        log.debug("智能搜索请求：sessionId={}, keyword={}", sessionId, keyword);

        try {
            // 直接获取统一的 AgentResponse 响应
            AgentResponse response = agentService.processIntention(sessionId, userId, keyword, lat, lng);
            
            log.debug("搜索成功，找到 {} 个地点", response.getPlaces() != null ? response.getPlaces().size() : 0);
            return Result.success(response);
        } catch (Exception e) {
            log.error("搜索失败", e);
            return Result.error("搜索失败：" + e.getMessage());
        }
    }

    /**
     * 确认选择接口（严格按前端文档返回）
     * 前端调用示例：
     * POST /api/agent/confirm
     * Body: {
     *   "sessionId": "xxx",
     *   "selectedPoiName": "韩山师范学院",
     *   "lat": 23.653927,
     *   "lng": 116.677026
     * }
     * 
     * 响应格式（成功）：
     * {
     *   "code": 200,
     *   "data": {
     *     "type": "ORDER",
     *     "message": "已确认目的地，正在创建订单",
     *     "poi": {...},
     *     "route": {...}
     *   }
     * }
     * 
     * 响应格式（失败）：
     * {
     *   "code": 500,
     *   "data": {
     *     "type": "ERROR",
     *     "message": "具体错误信息"
     *   }
     * }
     */
    @PostMapping("/confirm")
    public Result<Object> confirmSelection(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", required = false) Long userIdFromHeader) {

        String sessionId = (String) request.get("sessionId");
        String selectedPoiName = (String) request.get("selectedPoiName");
        Double lat = (Double) request.get("lat");
        Double lng = (Double) request.get("lng");
        
        // 优先从请求头获取 userId，如果没有则从 UserContext 获取（兼容两种模式）
        Long userId = userIdFromHeader;
        if (userId == null) {
            try {
                userId = com.anxin.travel.common.util.UserContext.getUserId();
                log.debug("从 UserContext 获取 userId: {}", userId);
            } catch (Exception e) {
                log.warn("无法从 UserContext 获取 userId，使用默认值");
                userId = 1L; // 默认用户 ID（测试用）
            }
        }

        log.debug("确认选择请求：sessionId={}, selectedPoiName={}", sessionId, selectedPoiName);

        // 参数校验（按前端文档要求的顺序）
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.error(400, "会话已过期");
        }
        
        if (selectedPoiName == null || selectedPoiName.trim().isEmpty()) {
            return Result.error(400, "POI 名称不能为空");
        }
        
        if (lat == null || lng == null) {
            return Result.error(400, "位置信息缺失");
        }
        
        // 验证坐标范围
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return Result.error(400, "经纬度坐标超出有效范围");
        }

        try {
            // 调用服务层，返回符合前端期望的 data 结构
            Object responseData = agentService.confirmSelection(sessionId, userId, selectedPoiName, lat, lng);

            log.debug("确认成功");
            
            // 按照前端文档返回完整结构
            // 注意：Result.success() 会自动将对象包装在 data 字段中
            return Result.success(responseData);

        } catch (IllegalArgumentException e) {
            // 参数校验失败，返回 400
            log.error("参数校验失败：{}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("确认失败：{}", e.getMessage());
            // 直接返回错误，让前端显示具体错误信息
            return Result.error(500, "处理失败：" + e.getMessage());
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

        log.debug("图片识别请求：sessionId={}, imageLength={}",
                sessionId, imageBase64 != null ? imageBase64.length() : 0);

        try {
            AgentResponse response = agentService.processImage(sessionId, userId, imageBase64, lat, lng);

            log.debug("图片识别成功");
            return Result.success(response);

        } catch (IllegalArgumentException e) {
            // 参数校验失败（图片格式、大小等）
            log.warn("图片参数校验失败：{}", e.getMessage());
            return Result.error(400, e.getMessage());
            
        } catch (RuntimeException e) {
            // 业务异常（API 调用失败、识别失败等）
            log.error("图片识别业务异常：{}", e.getMessage());
            
            // 根据错误信息判断具体错误类型
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("API")) {
                // API 服务异常
                return Result.error(503, "图片识别服务暂时不可用，请稍后重试");
            } else if (errorMsg != null && errorMsg.contains("未识别到")) {
                // 识别结果为空
                return Result.error(422, errorMsg);
            } else {
                // 其他业务异常
                return Result.error(500, "图片识别失败：" + errorMsg);
            }
            
        } catch (Exception e) {
            // 未知异常
            log.error("图片识别发生未知异常", e);
            return Result.error(500, "图片识别失败：系统异常，请稍后重试");
        }
    }

    /**
     * 清理会话接口
     */
    @PostMapping("/cleanup")
    public Result<Void> cleanupSession(
            @RequestBody Map<String, Object> request) {

        String sessionId = (String) request.get("sessionId");

        log.debug("清理会话：sessionId={}", sessionId);

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

        log.debug("更新位置：sessionId={}, lat={}, lng={}", sessionId, lat, lng);

        try {
            agentService.updateUserLocation(sessionId, lat, lng);
            return Result.success(null);
        } catch (Exception e) {
            log.error("更新位置失败", e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }
}
