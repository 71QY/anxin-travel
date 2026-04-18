package com.anxin.travel.module.user.service;

import com.anxin.travel.module.user.entity.UserFavoriteLocation;
import java.util.List;

public interface UserFavoriteLocationService {
    List<UserFavoriteLocation> getFavorites(Long userId);
    void addFavorite(UserFavoriteLocation location);
    void updateFavorite(UserFavoriteLocation location);
    void deleteFavorite(Long id, Long userId);
    
    /**
     * 获取收藏地点详情
     */
    UserFavoriteLocation getFavoriteById(Long id, Long userId);
    
    /**
     * 分享收藏地点给亲友
     */
    void shareFavorite(Long elderUserId, Long favoriteId, Long guardianUserId);
    
    /**
     * 确认到达目的地
     */
    void confirmArrival(Long userId, Long orderId, Long favoriteId);
    
    /**
     * 分享收藏给长辈（亲友→长辈，直接添加到长辈收藏列表）
     */
    void shareToElder(Long guardianUserId, Long favoriteId, Long elderUserId, Boolean saveAsNew);
}
