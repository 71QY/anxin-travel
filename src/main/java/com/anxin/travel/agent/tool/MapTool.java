package com.anxin.travel.agent.tool;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class MapTool implements Tool {

    @Override
    public String getName() {
        return "query_route";
    }

    @Override
    public String getDescription() {
        return "查询从起点到终点的路线，返回多平台对比结果";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        // 模拟返回路线数据
        Map<String, Object> result = new HashMap<>();
        result.put("platform", "gaode");
        result.put("distance", "10km");
        result.put("duration", "30分钟");
        result.put("price", "25元");
        return result;
    }
}