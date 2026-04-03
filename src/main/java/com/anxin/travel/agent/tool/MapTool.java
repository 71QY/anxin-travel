package com.anxin.travel.agent.tool;

import com.anxin.travel.agent.model.CandidateDestination;
import com.anxin.travel.module.map.client.AmapClient;
import com.anxin.travel.module.map.dto.PoiDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MapTool implements Tool {

    private final AmapClient amapClient;

    @Override
    public String getName() {
        return "search_nearby_places";
    }

    @Override
    public String getDescription() {
        return "根据用户当前位置搜索附近地点，例如医院、餐厅、商场等";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String keyword = (String) parameters.get("keyword");
        Double latitude = convertToDouble(parameters.get("latitude"));
        Double longitude = convertToDouble(parameters.get("longitude"));
        
        Map<String, Object> result = new HashMap<>();
        
        if (keyword == null || latitude == null || longitude == null) {
            result.put("error", "缺少必要参数");
            return result;
        }
        
        try {
            // 修复：智能体工具也使用模糊匹配（fuzzyMatch=true），扩大搜索范围
            List<PoiDTO> poiResults = amapClient.searchNearbyPlaces(
                keyword, 
                latitude, 
                longitude, 
                1, 
                20, 
                true, 
                false, 
                5000,   // 半径扩大到 5000 米
                true    // 改为 true，允许模糊匹配
            );
            List<CandidateDestination> candidates = poiResults.stream()
                    .map(poi -> {
                        CandidateDestination dest = new CandidateDestination();
                        dest.setName(poi.getName());
                        dest.setLat(poi.getLat());
                        dest.setLng(poi.getLng());
                        dest.setAddress(poi.getAddress());
                        dest.setDistance(Double.valueOf(poi.getDistance()).intValue());
                        return dest;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            result.put("success", true);
            result.put("count", candidates.size());
            result.put("places", candidates);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    private Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}