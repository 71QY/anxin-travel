package com.anxin.travel.agent.service;

/**
 * POI 搜索查询类型枚举
 */
public enum QueryType {
    /**
     * 地标类（如：北京大学、天安门）
     */
    LANDMARK,
    
    /**
     * 类别类（如：医院、餐厅）
     */
    CATEGORY,
    
    /**
     * 地址类（如：中关村大街 27 号）
     */
    ADDRESS,
    
    /**
     * 模糊/口语类（如：附近吃饭）
     */
    FUZZY
}
