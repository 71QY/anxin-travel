package com.anxin.travel.agent.model;

import lombok.Data;

@Data
public class CandidateDestination {
    private String name;
    private double lat;
    private double lng;
    private String address;
    private Integer distance;
}