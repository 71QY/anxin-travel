package com.anxin.travel.module.user.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.entity.UserFavoriteLocation;
import com.anxin.travel.module.user.service.UserFavoriteLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
                .filter(f -> f.getId().equals(location.getId()))
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
}
