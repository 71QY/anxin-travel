package com.anxin.travel.module.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateOrderRequest {
    @JsonProperty("destName")
    @JsonAlias({"poiName"})  // 兼容前端字段名
    private String destName;
    
    @JsonProperty("destLat")
    @JsonAlias({"poiLat"})  // 兼容前端字段名
    private Double destLat;
    
    @JsonProperty("destLng")
    @JsonAlias({"poiLng"})  // 兼容前端字段名
    private Double destLng;
    
    // ========== 代叫车相关字段 ==========
    private Long elderId;        // 长辈ID（为谁代叫车）
    private Double startLat;     // 起点纬度（长辈当前位置）
    private Double startLng;     // 起点经度（长辈当前位置）
}