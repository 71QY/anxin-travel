package com.anxin.travel.module.user.service;

import com.anxin.travel.module.user.entity.TravelRecord;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;

/**
 * 出行记录服务接口
 */
public interface TravelRecordService {
    
    /**
     * 获取用户的出行记录列表（分页）
     */
    Page<TravelRecord> getTravelRecords(Long userId, Integer page, Integer size, 
                                        LocalDateTime startDate, LocalDateTime endDate);
}
