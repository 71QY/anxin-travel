package com.anxin.travel.module.order.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {
    private String destName;
    private Double destLat;
    private Double destLng;
}