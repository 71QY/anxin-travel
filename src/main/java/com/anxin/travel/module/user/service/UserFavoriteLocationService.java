package com.anxin.travel.module.user.service;

import com.anxin.travel.module.user.entity.UserFavoriteLocation;
import java.util.List;

public interface UserFavoriteLocationService {
    List<UserFavoriteLocation> getFavorites(Long userId);
    void addFavorite(UserFavoriteLocation location);
    void updateFavorite(UserFavoriteLocation location);
    void deleteFavorite(Long id, Long userId);
}
