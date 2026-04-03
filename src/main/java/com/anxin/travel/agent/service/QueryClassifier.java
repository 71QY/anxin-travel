package com.anxin.travel.agent.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * POI 搜索查询分类器
 * 根据用户输入的关键词判断搜索类型，从而选择最佳搜索策略
 */
@Slf4j
public class QueryClassifier {

    // 类别关键词集合（用于识别通用类别查询）
    // 【关键修复】只包含纯类别词，不包含地标后缀
    private static final Set<String> CATEGORY_KEYWORDS = Set.of(
            "医院", "药店", "诊所", "卫生所",
            "餐厅", "饭店", "饭馆", "咖啡", "奶茶", "小吃", "快餐", "烧烤", "火锅",
            "酒店", "宾馆", "旅馆", "住宿",
            "超市", "商场", "便利店", "小卖部",
            "理发", "美容", "美发", "化妆",
            "银行", "ATM", "信用社",
            "厕所", "卫生间", "洗手间",
            "加油站", "充电桩",
            "地铁", "公交站", "火车站", "机场",
            "公园", "景点", "风景区",
            "图书馆", "博物馆", "美术馆", "电影院", "KTV", "网吧",
            "学校", "幼儿园", "培训机构"
            // 【关键删除】"大学"、"学院"、"大厦"、"广场"、"中心" 等是地标后缀，不是类别
    );

    /**
     * 查询类型分类
     * 【关键优化】调整分类顺序：ADDRESS → FUZZY → CATEGORY → LANDMARK
     * @param keyword 用户输入的关键词
     * @return 查询类型
     */
    public static QueryType classify(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return QueryType.FUZZY;
        }

        String cleanKeyword = keyword.trim().toLowerCase();

        // 【关键修复】Step 1: 地址类（最优先，因为特征最明显）
        if (isAddress(cleanKeyword)) {
            log.debug("📍 识别为地址类查询：{}", keyword);
            return QueryType.ADDRESS;
        }

        // Step 2: 模糊/口语类（优先于类别和地标）
        if (isFuzzy(cleanKeyword)) {
            log.debug("❓ 识别为模糊类查询：{}", keyword);
            return QueryType.FUZZY;
        }

        // Step 3: 纯类别词（排除"附近医院"这种情况）
        if (isPureCategory(cleanKeyword)) {
            log.debug("🏷️ 识别为类别类查询：{}", keyword);
            return QueryType.CATEGORY;
        }

        // Step 4: 默认地标类
        log.debug("🏛️ 识别为地标类查询（默认）：{}", keyword);
        return QueryType.LANDMARK;
    }

    /**
     * 判断是否为纯类别词（如：医院、餐厅，而不是"北京大学"）
     * 【关键优化】只匹配短的纯类别词，避免误判
     */
    private static boolean isPureCategory(String keyword) {
        // 纯类别词通常是 2-3 个字，且不包含地名特征
        if (keyword.length() > 3) {
            return false;
        }
        
        for (String cat : CATEGORY_KEYWORDS) {
            if (keyword.equals(cat)) {  // 【关键修复】完全匹配，不是包含
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为地址类查询
     */
    private static boolean isAddress(String keyword) {
        // 包含数字且有地址特征词
        if (!keyword.matches(".*\\d+.*")) {
            return false;
        }
        
        String[] addressPatterns = {"路", "街", "道", "巷", "号", "小区", "大厦", "楼", "单元"};
        for (String pattern : addressPatterns) {
            if (keyword.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为类别类查询
     */
    private static boolean isCategory(String keyword) {
        for (String cat : CATEGORY_KEYWORDS) {
            if (keyword.contains(cat)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为模糊/口语类查询
     */
    private static boolean isFuzzy(String keyword) {
        String[] fuzzyPatterns = {"附近", "最近", "哪里", "哪儿", "什么地方", "哪里有", "帮我找", "我要去"};
        for (String pattern : fuzzyPatterns) {
            if (keyword.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
