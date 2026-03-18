package com.anxin.travel.agent.tool;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    Map<String, Object> execute(Map<String, Object> parameters);
}