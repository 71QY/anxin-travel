package com.anxin.travel.module.user.service.impl;

import com.anxin.travel.module.user.entity.UserFavoriteLocation;
import com.anxin.travel.module.user.mapper.UserFavoriteLocationMapper;
import com.anxin.travel.module.user.service.UserFavoriteLocationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserFavoriteLocationServiceImpl implements UserFavoriteLocationService {

    @Autowired
    private UserFavoriteLocationMapper mapper;

    @Override
    public List<UserFavoriteLocation> getFavorites(Long userId) {
        LambdaQueryWrapper<UserFavoriteLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavoriteLocation::getUserId, userId);
        wrapper.orderByDesc(UserFavoriteLocation::getUpdatedAt);
        return mapper.selectList(wrapper);
    }

    @Override
    public void addFavorite(UserFavoriteLocation location) {
        // 1. 校验数量限制 (50个)
        Long count = mapper.selectCount(new LambdaQueryWrapper<UserFavoriteLocation>()
                .eq(UserFavoriteLocation::getUserId, location.getUserId()));
        if (count >= 50) {
            throw new RuntimeException("收藏数量已达上限(50个)");
        }

        // 2. 校验重复 (name + lat + lng)
        Long existCount = mapper.selectCount(new LambdaQueryWrapper<UserFavoriteLocation>()
                .eq(UserFavoriteLocation::getUserId, location.getUserId())
                .eq(UserFavoriteLocation::getName, location.getName())
                .eq(UserFavoriteLocation::getLatitude, location.getLatitude())
                .eq(UserFavoriteLocation::getLongitude, location.getLongitude()));
        if (existCount > 0) {
            throw new RuntimeException("该地点已存在收藏中");
        }

        location.setCreatedAt(LocalDateTime.now());
        location.setUpdatedAt(LocalDateTime.now());
        if (location.getType() == null || location.getType().isEmpty()) {
            location.setType("CUSTOM");
        }
        mapper.insert(location);
    }

    @Override
    public void updateFavorite(UserFavoriteLocation location) {
        location.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(location);
    }

    @Override
    public void deleteFavorite(Long id, Long userId) {
        // 确保只能删除自己的收藏
        UserFavoriteLocation fav = mapper.selectById(id);
        if (fav != null && fav.getUserId().equals(userId)) {
            mapper.deleteById(id);
        }
    }
}
