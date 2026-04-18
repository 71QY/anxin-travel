package com.anxin.travel.module.user.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.dto.ConfirmArrivalRequest;
import com.anxin.travel.module.user.dto.ShareFavoriteRequest;
import com.anxin.travel.module.user.dto.ShareToElderRequest;
import com.anxin.travel.module.user.entity.UserFavoriteLocation;
import com.anxin.travel.module.user.service.UserFavoriteLocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/favorites")
public class UserFavoriteLocationController {

    @Autowired
    private UserFavoriteLocationService favoriteService;

    /**
     * 获取用户收藏列表
     */
    @GetMapping
    public Result<List<UserFavoriteLocation>> getFavorites() {
        Long userId = UserContext.getUserId();
        return Result.success(favoriteService.getFavorites(userId));
    }

    /**
     * 添加收藏
     */
    @PostMapping
    public Result<UserFavoriteLocation> addFavorite(@RequestBody UserFavoriteLocation location) {
        Long userId = UserContext.getUserId();
        location.setUserId(userId);
        
        // 简单校验
        if (location.getName() == null || location.getAddress() == null || 
            location.getLatitude() == null || location.getLongitude() == null) {
            return Result.error(400, "请求参数错误");
        }
        
        try {
            favoriteService.addFavorite(location);
            return Result.success(location);
        } catch (RuntimeException e) {
            return Result.error(429, e.getMessage());
        }
    }

    /**
     * 更新收藏
     */
    @PutMapping
    public Result<UserFavoriteLocation> updateFavorite(@RequestBody UserFavoriteLocation location) {
        Long userId = UserContext.getUserId();
        if (location.getId() == null) {
            return Result.error(400, "缺少ID参数");
        }
        
        // 权限校验：确保是本人的收藏
        UserFavoriteLocation existing = favoriteService.getFavorites(userId).stream()
                .filter(f -> f.getId().equals(location  .getId()))
                .findFirst()
                .orElse(null);
                
        if (existing == null) {
            return Result.error(403, "无权操作此资源或记录不存在");
        }
        
        location.setUserId(userId); 
        favoriteService.updateFavorite(location);
        return Result.success(location);
    }

    /**
     * 删除收藏
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteFavorite(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        favoriteService.deleteFavorite(id, userId);
        return Result.success();
    }
    
    /**
     * 获取收藏地点详情（含电话和简介）
     */
    @GetMapping("/{id}")
    public Result<UserFavoriteLocation> getFavoriteById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("【获取收藏详情】userId={}, favoriteId={}", userId, id);
        try {
            UserFavoriteLocation favorite = favoriteService.getFavoriteById(id, userId);
            return Result.success(favorite);
        } catch (RuntimeException e) {
            log.error("❌ 获取收藏详情失败：{}", e.getMessage());
            return Result.error(403, e.getMessage());
        }
    }
    
    /**
     * 分享收藏地点给亲友
     * 使用 Form 表单格式，符合前端需求文档
     */
    @PostMapping("/share-to-guardian")
    public Result<Void> shareToGuardian(
            @RequestParam Long favoriteId,
            @RequestParam Long guardianUserId) {
        Long elderUserId = UserContext.getUserId();
        log.info("【分享收藏】elderUserId={}, favoriteId={}, guardianUserId={}", 
            elderUserId, favoriteId, guardianUserId);
        
        if (favoriteId == null || guardianUserId == null) {
            return Result.error(400, "参数错误：favoriteId 和 guardianUserId 不能为空");
        }
        
        try {
            favoriteService.shareFavorite(elderUserId, favoriteId, guardianUserId);
            return Result.success();
        } catch (RuntimeException e) {
            log.error("❌ 分享收藏失败：{}", e.getMessage());
            return Result.error(403, e.getMessage());
        }
    }
    
    /**
     * 确认到达目的地
     * 使用 Form 表单格式，符合前端需求文档
     * favoriteId 必填，orderId 可选
     */
    @PostMapping("/confirm-arrival-simple")
    public Result<Void> confirmArrivalSimple(
            @RequestParam Long favoriteId,
            @RequestParam(required = false) Long orderId) {
        Long userId = UserContext.getUserId();
        log.info("【确认到达】userId={}, favoriteId={}, orderId={}", 
            userId, favoriteId, orderId);
        
        if (favoriteId == null) {
            return Result.error(400, "参数错误：favoriteId 不能为空");
        }
        
        try {
            favoriteService.confirmArrival(userId, orderId, favoriteId);
            return Result.success();
        } catch (RuntimeException e) {
            log.error("❌ 确认到达失败：{}", e.getMessage());
            return Result.error(403, e.getMessage());
        }
    }
    
    /**
     * 分享收藏给长辈（亲友→长辈，直接添加到长辈收藏列表）
     * 使用 JSON 格式
     */
    @PostMapping("/share-to-elder")
    public Result<Void> shareToElder(@RequestBody ShareToElderRequest request) {
        Long guardianUserId = UserContext.getUserId();
        log.info("【分享收藏给长辈】guardianUserId={}, favoriteId={}, elderUserId={}", 
            guardianUserId, request.getFavoriteId(), request.getElderUserId());
        
        if (request.getFavoriteId() == null || request.getElderUserId() == null) {
            return Result.error(400, "参数错误：favoriteId 和 elderUserId 不能为空");
        }
        
        try {
            favoriteService.shareToElder(guardianUserId, request.getFavoriteId(), 
                request.getElderUserId(), request.getSaveAsNew());
            return Result.success();
        } catch (RuntimeException e) {
            log.error("❌ 分享收藏给长辈失败：{}", e.getMessage());
            if (e.getMessage().contains("不存在亲友关系")) {
                return Result.error(403, e.getMessage());
            } else if (e.getMessage().contains("收藏地点不存在")) {
                return Result.error(404, e.getMessage());
            } else if (e.getMessage().contains("已达上限")) {
                return Result.error(429, e.getMessage());
            }
            return Result.error(500, e.getMessage());
        }
    }
}
