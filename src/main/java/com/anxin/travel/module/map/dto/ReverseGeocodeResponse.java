package com.anxin.travel.module.map.dto;

import lombok.Data;

/**
 * 逆地理编码响应
 */
@Data
public class ReverseGeocodeResponse {
    private String address;        // 完整地址
    private String province;       // 省
    private String city;          // 市
    private String district;      // 区/县
    private String street;        // 街道
    private String streetNumber;  // 门牌号
    private Double lat;
    private Double lng;
}
