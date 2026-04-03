package com.anxin.travel.module.map.dto;

import lombok.Data;

@Data
public class PoiResult {
    private String name;
    private String address;
    private Double lat;
    private Double lng;
    private Integer distance;

    public PoiResult() {}

    public PoiResult(String name, String address, Double lat, Double lng, Integer distance) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.distance = distance;
    }
}
