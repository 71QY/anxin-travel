package com.anxin.travel.agent.service;

import com.anxin.travel.agent.tool.Tool;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ToolExecutorService {

    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();

    public ToolExecutorService(List<Tool> tools) {
        for (Tool tool : tools) {
            toolMap.put(tool.getName(), tool);
        }
    }

    public Map<String, Object> executeTool(String toolName, Map<String, Object> parameters) {
        Tool tool = toolMap.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("未知工具: " + toolName);
        }
        return tool.execute(parameters);
    }

    public List<Map<String, String>> getToolDescriptions() {
        return toolMap.values().stream().map(t -> {
            Map<String, String> desc = new ConcurrentHashMap<>();
            desc.put("name", t.getName());
            desc.put("description", t.getDescription());
            return desc;
        }).collect(Collectors.toList());
    }

}