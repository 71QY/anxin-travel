package com.anxin.travel.module.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.anxin.travel.agent.controller.NativeWebSocket;
import com.anxin.travel.common.util.SpringContextUtil;
import com.anxin.travel.module.guard.mapper.FamilyGuardMapper;
import com.anxin.travel.module.order.entity.OrderInfo;
import com.anxin.travel.module.order.mapper.OrderMapper;
import com.anxin.travel.module.user.entity.FavoriteShare;
import com.anxin.travel.module.user.entity.TravelRecord;
import com.anxin.travel.module.user.entity.UserFavoriteLocation;
import com.anxin.travel.module.user.mapper.FavoriteShareMapper;
import com.anxin.travel.module.user.mapper.TravelRecordMapper;
import com.anxin.travel.module.user.mapper.UserFavoriteLocationMapper;
import com.anxin.travel.module.user.service.UserFavoriteLocationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserFavoriteLocationServiceImpl implements UserFavoriteLocationService {

    @Autowired
    private UserFavoriteLocationMapper mapper;
    
    @Autowired
    private FavoriteShareMapper favoriteShareMapper;
    
    @Autowired
    private TravelRecordMapper travelRecordMapper;
    
    @Autowired
    private FamilyGuardMapper familyGuardMapper;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private NativeWebSocket nativeWebSocket;

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
    
    @Override
    public UserFavoriteLocation getFavoriteById(Long id, Long userId) {
        UserFavoriteLocation fav = mapper.selectById(id);
        if (fav == null) {
            throw new RuntimeException("收藏地点不存在");
        }
        if (!fav.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此收藏地点");
        }
        return fav;
    }
    
    @Override
    @Transactional
    public void shareFavorite(Long elderUserId, Long favoriteId, Long guardianUserId) {
        log.info("【分享收藏】elderUserId={}, favoriteId={}, guardianUserId={}", 
            elderUserId, favoriteId, guardianUserId);
        
        // 1. 验证收藏地点是否存在且属于长辈
        UserFavoriteLocation favorite = mapper.selectById(favoriteId);
        if (favorite == null) {
            throw new RuntimeException("收藏地点不存在");
        }
        if (!favorite.getUserId().equals(elderUserId)) {
            throw new RuntimeException("无权分享此收藏地点");
        }
        
        // 2. 验证亲友关系（查询 guardians 表）
        boolean isGuardian = familyGuardMapper.selectCount(
            new LambdaQueryWrapper<com.anxin.travel.module.guard.entity.FamilyGuard>()
                .eq(com.anxin.travel.module.guard.entity.FamilyGuard::getElderId, elderUserId)
                .eq(com.anxin.travel.module.guard.entity.FamilyGuard::getGuardianId, guardianUserId)
                .eq(com.anxin.travel.module.guard.entity.FamilyGuard::getStatus, 1)
        ) > 0;
        
        if (!isGuardian) {
            throw new RuntimeException("该用户不是您的亲友，无法分享");
        }
        
        // 3. 创建分享记录
        FavoriteShare share = new FavoriteShare();
        share.setFavoriteId(favoriteId);
        share.setElderUserId(elderUserId);
        share.setGuardianUserId(guardianUserId);
        share.setStatus(0); // 待处理
        share.setSharedAt(LocalDateTime.now());
        favoriteShareMapper.insert(share);
        
        // 4. WebSocket 推送给亲友
        try {
            pushFavoriteSharedMessage(favorite, elderUserId, guardianUserId);
        } catch (Exception e) {
            log.error("❌ WebSocket 推送 FAVORITE_SHARED 失败", e);
        }
        
        log.info("✅ 收藏地点分享成功：favoriteId={}, guardianUserId={}", favoriteId, guardianUserId);
    }
    
    @Override
    @Transactional
    public void confirmArrival(Long userId, Long orderId, Long favoriteId) {
        log.info("【确认到达】userId={}, orderId={}, favoriteId={}", userId, orderId, favoriteId);
        
        // 1. 验证收藏地点是否存在且属于当前用户
        UserFavoriteLocation favorite = mapper.selectById(favoriteId);
        if (favorite == null) {
            throw new RuntimeException("收藏地点不存在");
        }
        if (!favorite.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此收藏地点");
        }
        
        OrderInfo order = null;
        
        // 2. 如果提供了 orderId，验证并更新订单状态
        if (orderId != null) {
            order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new RuntimeException("订单不存在");
            }
            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("无权操作此订单");
            }
            
            // 更新订单状态为 6（已完成）
            order.setStatus(6);
            if (order.getActualPrice() == null) {
                order.setActualPrice(order.getEstimatePrice());
            }
            orderMapper.updateById(order);
            log.info("✅ 订单{}状态更新为已完成", orderId);
        }
        
        // 3. 创建出行记录
        if (order != null) {
            // 如果有关联订单，从订单获取信息
            createTravelRecord(userId, order.getId(), favorite, order);
        } else {
            // 如果没有订单，只基于收藏地点创建简单记录
            createSimpleTravelRecord(userId, favorite);
        }
        
        // 4. WebSocket 推送给所有亲友
        try {
            if (order != null) {
                pushArrivalConfirmedMessage(order, favoriteId);
            } else {
                pushArrivalConfirmedMessageSimple(userId, favorite);
            }
        } catch (Exception e) {
            log.error("❌ WebSocket 推送 ARRIVAL_CONFIRMED 失败", e);
        }
        
        log.info("✅ 到达确认完成：favoriteId={}", favoriteId);
    }
    
    /**
     * 推送 FAVORITE_SHARED 消息给亲友
     */
    private void pushFavoriteSharedMessage(UserFavoriteLocation favorite, Long elderUserId, Long guardianUserId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "FAVORITE_SHARED");
        message.put("userId", guardianUserId);  // ⭐ 新增：顶层 userId 字段（前端期望）
        message.put("favoriteId", favorite.getId());
        message.put("favoriteName", favorite.getName());
        message.put("favoriteAddress", favorite.getAddress());
        message.put("favoriteLat", favorite.getLatitude());
        message.put("favoriteLng", favorite.getLongitude());
        message.put("favoritePhone", favorite.getPhone());
        message.put("favoriteDescription", favorite.getDescription());
        message.put("elderUserId", elderUserId);
        
        // 获取长辈姓名（从 user 表）
        com.anxin.travel.module.user.entity.User elder = 
            SpringContextUtil.getBean(com.anxin.travel.module.user.mapper.UserMapper.class)
                .selectById(elderUserId);
        message.put("elderName", elder != null ? elder.getNickname() : "长辈");
        
        // ⭐ 新增：获取长辈实时位置（从 Redis 缓存读取）
        try {
            com.anxin.travel.agent.service.MemoryService memoryService = 
                SpringContextUtil.getBean(com.anxin.travel.agent.service.MemoryService.class);
            double[] location = memoryService.getLocation("user_" + elderUserId);
            
            if (location != null && location.length >= 2) {
                message.put("elderCurrentLat", location[0]);
                message.put("elderCurrentLng", location[1]);
                message.put("elderLocationTimestamp", System.currentTimeMillis());
                log.info("✅ 已附加长辈实时位置：lat={}, lng={}", location[0], location[1]);
            } else {
                log.warn("⚠️ 未找到长辈{}的位置信息，将使用默认起点", elderUserId);
            }
        } catch (Exception e) {
            log.error("❌ 获取长辈位置失败", e);
        }
        
        message.put("timestamp", System.currentTimeMillis());
        
        String messageJson = JSON.toJSONString(message);
        nativeWebSocket.sendMessageToUser(guardianUserId, messageJson);
        log.info("✅ 已向亲友 userId={} 推送 FAVORITE_SHARED", guardianUserId);
    }
    
    /**
     * 推送 ARRIVAL_CONFIRMED 消息给所有亲友
     */
    private void pushArrivalConfirmedMessage(OrderInfo order, Long favoriteId) {
        // 查询所有绑定的亲友
        List<com.anxin.travel.module.guard.entity.FamilyGuard> guardians = 
            familyGuardMapper.selectActiveGuardsByElderId(order.getUserId());
        
        if (guardians.isEmpty()) {
            log.info("⚠️ 长辈{}没有绑定的亲友，跳过推送", order.getUserId());
            return;
        }
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ARRIVAL_CONFIRMED");
        message.put("orderId", order.getId());
        message.put("favoriteId", favoriteId);
        message.put("destinationName", order.getDestAddress());
        message.put("destinationAddress", order.getDestAddress());
        message.put("arriveTime", LocalDateTime.now().toString());
        message.put("elderUserId", order.getUserId());
        
        // 获取长辈姓名
        com.anxin.travel.module.user.entity.User elder = 
            SpringContextUtil.getBean(com.anxin.travel.module.user.mapper.UserMapper.class)
                .selectById(order.getUserId());
        message.put("elderName", elder != null ? elder.getNickname() : "长辈");
        message.put("timestamp", System.currentTimeMillis());
        
        String messageJson = JSON.toJSONString(message);
        
        // 推送给所有亲友
        for (com.anxin.travel.module.guard.entity.FamilyGuard guard : guardians) {
            nativeWebSocket.sendMessageToUser(guard.getGuardianId(), messageJson);
            log.info("✅ 已向亲友 userId={} 推送 ARRIVAL_CONFIRMED", guard.getGuardianId());
        }
    }
    
    /**
     * 创建出行记录
     */
    private void createTravelRecord(Long userId, Long orderId, UserFavoriteLocation favorite, OrderInfo order) {
        TravelRecord record = new TravelRecord();
        record.setUserId(userId);
        record.setOrderId(orderId);
        record.setFavoriteId(favorite.getId());
        record.setDestinationName(favorite.getName());
        record.setDestinationAddress(favorite.getAddress());
        record.setDestinationLat(favorite.getLatitude());
        record.setDestinationLng(favorite.getLongitude());
        record.setStartTime(order.getCreateTime() != null ? order.getCreateTime() : LocalDateTime.now());
        record.setArriveTime(LocalDateTime.now());
        record.setStatus(1); // 已完成
        
        // 计算行程时长（分钟）
        if (order.getCreateTime() != null) {
            long minutes = java.time.Duration.between(order.getCreateTime(), LocalDateTime.now()).toMinutes();
            record.setDurationMinutes((int) minutes);
        }
        
        travelRecordMapper.insert(record);
        log.info("✅ 出行记录创建成功：recordId={}, orderId={}, destination={}", 
            record.getId(), orderId, favorite.getName());
    }
    
    /**
     * 创建简单出行记录（没有订单时）
     */
    private void createSimpleTravelRecord(Long userId, UserFavoriteLocation favorite) {
        TravelRecord record = new TravelRecord();
        record.setUserId(userId);
        record.setOrderId(null); // 没有关联订单
        record.setFavoriteId(favorite.getId());
        record.setDestinationName(favorite.getName());
        record.setDestinationAddress(favorite.getAddress());
        record.setDestinationLat(favorite.getLatitude());
        record.setDestinationLng(favorite.getLongitude());
        record.setStartTime(LocalDateTime.now());
        record.setArriveTime(LocalDateTime.now());
        record.setStatus(1); // 已完成
        record.setDurationMinutes(0);
        record.setDistanceMeters(0);
        
        travelRecordMapper.insert(record);
        log.info("✅ 简单出行记录创建成功：recordId={}, destination={}", 
            record.getId(), favorite.getName());
    }
    
    /**
     * 推送 ARRIVAL_CONFIRMED 消息（简单版，没有订单时）
     */
    private void pushArrivalConfirmedMessageSimple(Long userId, UserFavoriteLocation favorite) {
        // 查询所有绑定的亲友
        List<com.anxin.travel.module.guard.entity.FamilyGuard> guardians = 
            familyGuardMapper.selectActiveGuardsByElderId(userId);
        
        if (guardians.isEmpty()) {
            log.info("⚠️ 长辈{}没有绑定的亲友，跳过推送", userId);
            return;
        }
        
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ARRIVAL_CONFIRMED");
        message.put("orderId", null);
        message.put("favoriteId", favorite.getId());
        message.put("destinationName", favorite.getName());
        message.put("destinationAddress", favorite.getAddress());
        message.put("arriveTime", LocalDateTime.now().toString());
        message.put("elderUserId", userId);
        
        // 获取长辈姓名
        com.anxin.travel.module.user.entity.User elder = 
            SpringContextUtil.getBean(com.anxin.travel.module.user.mapper.UserMapper.class)
                .selectById(userId);
        message.put("elderName", elder != null ? elder.getNickname() : "长辈");
        message.put("timestamp", System.currentTimeMillis());
        
        String messageJson = JSON.toJSONString(message);
        
        // 推送给所有亲友
        for (com.anxin.travel.module.guard.entity.FamilyGuard guard : guardians) {
            nativeWebSocket.sendMessageToUser(guard.getGuardianId(), messageJson);
            log.info("✅ 已向亲友 userId={} 推送 ARRIVAL_CONFIRMED", guard.getGuardianId());
        }
    }
    
    @Override
    @Transactional
    public void shareToElder(Long guardianUserId, Long favoriteId, Long elderUserId, Boolean saveAsNew) {
        log.info("【分享收藏给长辈】guardianUserId={}, favoriteId={}, elderUserId={}", 
            guardianUserId, favoriteId, elderUserId);
        
        // 1. 验证亲友关系：检查 guardianUserId 是否是 elderUserId 的亲友
        boolean isGuardian = familyGuardMapper.selectCount(
            new LambdaQueryWrapper<com.anxin.travel.module.guard.entity.FamilyGuard>()
                .eq(com.anxin.travel.module.guard.entity.FamilyGuard::getElderId, elderUserId)
                .eq(com.anxin.travel.module.guard.entity.FamilyGuard::getGuardianId, guardianUserId)
                .eq(com.anxin.travel.module.guard.entity.FamilyGuard::getStatus, 1)
        ) > 0;
        
        if (!isGuardian) {
            throw new RuntimeException("您与该用户不存在亲友关系");
        }
        
        // 2. 验证收藏归属：确认 favoriteId 属于当前用户（guardianUserId）
        UserFavoriteLocation sourceFavorite = mapper.selectById(favoriteId);
        if (sourceFavorite == null) {
            throw new RuntimeException("收藏地点不存在");
        }
        if (!sourceFavorite.getUserId().equals(guardianUserId)) {
            throw new RuntimeException("无权操作此收藏地点");
        }
        
        // 3. 检查长辈收藏数量限制（50个）
        Long elderFavoriteCount = mapper.selectCount(
            new LambdaQueryWrapper<UserFavoriteLocation>()
                .eq(UserFavoriteLocation::getUserId, elderUserId)
        );
        if (elderFavoriteCount >= 50) {
            throw new RuntimeException("长辈收藏数量已达上限(50个)");
        }
        
        // 4. 为长辈创建新收藏
        UserFavoriteLocation newFavorite = new UserFavoriteLocation();
        newFavorite.setUserId(elderUserId);
        newFavorite.setName(sourceFavorite.getName());
        newFavorite.setAddress(sourceFavorite.getAddress());
        newFavorite.setLatitude(sourceFavorite.getLatitude());
        newFavorite.setLongitude(sourceFavorite.getLongitude());
        newFavorite.setType(sourceFavorite.getType() != null ? sourceFavorite.getType() : "CUSTOM");
        newFavorite.setPhone(sourceFavorite.getPhone());
        newFavorite.setDescription(sourceFavorite.getDescription());
        newFavorite.setCreatedAt(LocalDateTime.now());
        newFavorite.setUpdatedAt(LocalDateTime.now());
        
        mapper.insert(newFavorite);
        log.info("✅ 成功为长辈{}添加收藏：{}", elderUserId, sourceFavorite.getName());
        
        // 5. （可选）WebSocket 推送 FAVORITE_ADDED 消息给长辈
        try {
            pushFavoriteAddedMessage(newFavorite, guardianUserId, elderUserId);
        } catch (Exception e) {
            log.error("❌ WebSocket 推送 FAVORITE_ADDED 失败", e);
        }
    }
    
    /**
     * 推送 FAVORITE_ADDED 消息给长辈
     */
    private void pushFavoriteAddedMessage(UserFavoriteLocation newFavorite, Long guardianUserId, Long elderUserId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "FAVORITE_ADDED");
        message.put("timestamp", System.currentTimeMillis());
        message.put("favoriteId", newFavorite.getId());
        message.put("locationName", newFavorite.getName());
        
        // 获取分享者姓名
        com.anxin.travel.module.user.entity.User guardian = 
            SpringContextUtil.getBean(com.anxin.travel.module.user.mapper.UserMapper.class)
                .selectById(guardianUserId);
        message.put("sharedByName", guardian != null ? guardian.getNickname() : "亲友");
        message.put("message", (guardian != null ? guardian.getNickname() : "亲友") + "为您添加了一个收藏地点");
        
        String messageJson = JSON.toJSONString(message);
        nativeWebSocket.sendMessageToUser(elderUserId, messageJson);
        log.info("✅ 已向长辈 userId={} 推送 FAVORITE_ADDED", elderUserId);
    }
}
