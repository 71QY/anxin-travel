package com.anxin.travel.module.user.controller;

import com.anxin.travel.common.result.PageResult;
import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.entity.TravelRecord;
import com.anxin.travel.module.user.service.TravelRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 出行记录控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/travel-records")
public class TravelRecordController {

    @Autowired
    private TravelRecordService travelRecordService;

    /**
     * 获取出行记录列表（分页）
     */
    @GetMapping
    public Result<PageResult<TravelRecord>> getTravelRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDateTime endDate) {
        
        Long userId = UserContext.getUserId();
        log.info("【查询出行记录】userId={}, page={}, size={}", userId, page, size);
        
        // 如果提供了日期，将结束日期设置为当天的 23:59:59
        if (endDate != null) {
            endDate = endDate.withHour(23).withMinute(59).withSecond(59);
        }
        
        Page<TravelRecord> recordPage = travelRecordService.getTravelRecords(userId, page, size, startDate, endDate);
        
        PageResult<TravelRecord> result = new PageResult<>();
        result.setList(recordPage.getRecords());
        result.setTotal(recordPage.getTotal());
        result.setPage(recordPage.getCurrent());
        result.setSize(recordPage.getSize());
        
        return Result.success(result);
    }
}
