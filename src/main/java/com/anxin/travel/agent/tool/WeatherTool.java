package com.anxin.travel.agent.tool;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class WeatherTool implements Tool {

    @Override
    public String getName() {
        return "query_weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        String city = (String) parameters.getOrDefault("city", "杭州");
        Map<String, Object> result = new HashMap<>();
        result.put("city", city);
        result.put("weather", "晴");
        result.put("temperature", "25°C");
        return result;
    }
}