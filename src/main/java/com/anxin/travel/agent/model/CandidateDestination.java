package com.anxin.travel.agent.model;

import lombok.Data;

@Data
public class CandidateDestination {
    private String name;
    private double lat;      // 改为基本类型
    private double lng;      // 改为基本类型
    private String address;
}