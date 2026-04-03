package com.anxin.travel.module.map.dto;

import lombok.Data;

/**
 * POI 统一数据传输对象（前后端标准协议）
 * 前端不再依赖 AMap SDK，所有 POI 数据来自后端
 */
@Data
public class PoiDTO {

    /**
     * POI 唯一标识（用于前端选择）
     */
    private String id;

    private String name;

    private String address;

    private double lat;

    private double lng;

    private double distance;

    private String type;

    /**
     * 路线耗时（秒）
     */
    private Integer duration;

    /**
     * 路线价格（元）
     */
    private Double price;
    
    /**
     * POI 相关性评分（用于排序）
     */
    private double score;
}
