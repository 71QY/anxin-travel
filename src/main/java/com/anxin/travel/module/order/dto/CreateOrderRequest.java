package com.anxin.travel.module.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateOrderRequest {
    @JsonProperty({"destName", "poiName"})  // 兼容两种字段名
    private String destName;
    
    @JsonProperty({"destLat", "poiLat"})  // 兼容两种字段名
    private Double destLat;
    
    @JsonProperty({"destLng", "poiLng"})  // 兼容两种字段名
    private Double destLng;
}