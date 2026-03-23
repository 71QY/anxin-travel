package com.anxin.travel.module.map.client;

import com.anxin.travel.agent.model.CandidateDestination;
import com.anxin.travel.module.map.dto.RouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AmapClient {

    /**
     * 模拟 POI 搜索（后续替换为真实高德 API）
     * @param keyword 搜索关键词
     * @param lat 用户当前纬度
     * @param lng 用户当前经度
     */
    public List<CandidateDestination> searchPoi(String keyword, double lat, double lng) {
        log.info("搜索POI: keyword={}, lat={}, lng={}", keyword, lat, lng);
        List<CandidateDestination> list = new ArrayList<>();
        CandidateDestination dest = new CandidateDestination();
        dest.setName(keyword);
        dest.setLat(lat);
        dest.setLng(lng);
        dest.setAddress(keyword + "附近");
        list.add(dest);
        return list;
    }

    /**
     * 模拟路线规划（后续替换为真实高德 API）
     */
    public RouteResult getRoute(String origin, String destination, String mode) {
        log.info("路线规划: origin={}, destination={}, mode={}", origin, destination, mode);
        RouteResult route = new RouteResult();
        route.setMode(mode);
        route.setDuration(1800);   // 30分钟
        route.setDistance(5000);   // 5公里
        route.setPrice(25.0);
        return route;
    }
}