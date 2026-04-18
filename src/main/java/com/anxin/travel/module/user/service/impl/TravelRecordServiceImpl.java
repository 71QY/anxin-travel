package com.anxin.travel.module.user.service.impl;

import com.anxin.travel.module.user.entity.TravelRecord;
import com.anxin.travel.module.user.mapper.TravelRecordMapper;
import com.anxin.travel.module.user.service.TravelRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 出行记录服务实现
 */
@Slf4j
@Service
public class TravelRecordServiceImpl implements TravelRecordService {

    @Autowired
    private TravelRecordMapper travelRecordMapper;

    @Override
    public Page<TravelRecord> getTravelRecords(Long userId, Integer page, Integer size,
                                               LocalDateTime startDate, LocalDateTime endDate) {
        log.info("【查询出行记录】userId={}, page={}, size={}, startDate={}, endDate={}", 
            userId, page, size, startDate, endDate);
        
        Page<TravelRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TravelRecord> wrapper = new LambdaQueryWrapper<TravelRecord>()
            .eq(TravelRecord::getUserId, userId)
            .ge(startDate != null, TravelRecord::getStartTime, startDate)
            .le(endDate != null, TravelRecord::getStartTime, endDate)
            .orderByDesc(TravelRecord::getStartTime);
        
        Page<TravelRecord> result = travelRecordMapper.selectPage(pageParam, wrapper);
        log.info("✅ 查询到 {} 条出行记录", result.getTotal());
        
        return result;
    }
}
