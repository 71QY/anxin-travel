package com.anxin.travel.agent.tool;

import com.anxin.travel.module.map.client.AmapClient;
import com.anxin.travel.module.map.dto.RouteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteTool implements Tool {

    private final AmapClient amapClient;

    @Override
    public String getName() {
        return "get_route";
    }

    @Override
    public String getDescription() {
        return "规划从起点到终点的路线，支持驾车、步行、公交等方式，返回距离、时间、预估价格";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String origin = (String) parameters.get("origin");
        String destination = (String) parameters.get("destination");
        String mode = (String) parameters.getOrDefault("mode", "driving");

        Map<String, Object> result = new HashMap<>();

        if (origin == null || origin.isEmpty() || destination == null || destination.isEmpty()) {
            result.put("success", false);
            result.put("error", "缺少起点或终点坐标");
            return result;
        }

        try {
            log.info("执行路线规划工具：origin={}, destination={}, mode={}", origin, destination, mode);
            RouteResult route = amapClient.getRoute(origin, destination, mode);

            result.put("success", true);
            result.put("route", route);
            result.put("distance", route.getDistance());
            result.put("duration", route.getDuration());
            result.put("price", route.getPrice());
            result.put("mode", route.getMode());

            log.info("路线规划成功：距离={}m, 时长={}s, 价格={}元",
                    route.getDistance(), route.getDuration(), route.getPrice());

        } catch (Exception e) {
            log.error("路线规划失败", e);
            result.put("success", false);
            result.put("error", "路线规划失败：" + e.getMessage());
        }

        return result;
    }
}
