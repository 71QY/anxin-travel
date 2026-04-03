package com.anxin.travel.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询改写器（Query Rewriter）
 * 将用户输入的口语化、简称、同义词转换为标准搜索词
 */
@Slf4j
@Component
public class QueryRewriter {

    // 同义词字典（简称 → 全称）
    private static final Map<String, String> SYNONYM_MAP = new HashMap<>();
    
    // 常见地名前缀/后缀（用于提取核心词）
    private static final Set<String> LOCATION_PREFIXES = Set.of(
            "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安", "南京"
    );
    
    static {
        // 学校类
        SYNONYM_MAP.put("北大", "北京大学");
        SYNONYM_MAP.put("清华", "清华大学");
        SYNONYM_MAP.put("人大", "中国人民大学");
        SYNONYM_MAP.put("北师大", "北京师范大学");
        SYNONYM_MAP.put("华师", "华南师范大学");
        SYNONYM_MAP.put("广工", "广东工业大学");
        SYNONYM_MAP.put("广大", "广州大学");
        
        // 医院类
        SYNONYM_MAP.put("协和", "北京协和医院");
        SYNONYM_MAP.put("301", "中国人民解放军总医院");
        SYNONYM_MAP.put("同仁", "北京同仁医院");
        
        // 地标类
        SYNONYM_MAP.put("故宫", "故宫博物院");
        SYNONYM_MAP.put("央视", "中央电视台");
        SYNONYM_MAP.put("鸟巢", "国家体育场");
        SYNONYM_MAP.put("水立方", "国家游泳中心");
        SYNONYM_MAP.put("国贸", "中国国际贸易中心");
        
        // 商圈类
        SYNONYM_MAP.put("万达", "万达广场");
        SYNONYM_MAP.put("太古里", "三里屯太古里");
        SYNONYM_MAP.put("新天地", "上海新天地");
        
        // 交通枢纽
        SYNONYM_MAP.put("北京站", "北京火车站");
        SYNONYM_MAP.put("上海站", "上海火车站");
        SYNONYM_MAP.put("广州南", "广州南站");
        SYNONYM_MAP.put("首都机场", "北京首都国际机场");
    }

    /**
     * 查询改写（主入口）
     * @param query 原始查询
     * @return 改写后的查询（如果没有改写，返回原词）
     */
    public String rewrite(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        String cleanQuery = query.trim().toLowerCase();

        log.info("📝 开始查询改写：{}", query);

        // Step 1: 同义词替换
        String synonymResult = rewriteBySynonym(cleanQuery);
        if (!synonymResult.equals(cleanQuery)) {
            log.info("✅ 同义词改写：{} -> {}", query, synonymResult);
            return synonymResult;
        }

        // Step 2: 去除口语化词汇
        String cleanedResult = removeFuzzyWords(cleanQuery);
        if (!cleanedResult.equals(cleanQuery)) {
            log.info("✅ 去除口语：{} -> {}", query, cleanedResult);
            return cleanedResult;
        }

        // Step 3: 提取核心词（去除修饰词）
        String coreResult = extractCoreWord(cleanQuery);
        if (!coreResult.equals(cleanQuery)) {
            log.info("✅ 提取核心词：{} -> {}", query, coreResult);
            return coreResult;
        }

        // 无改写
        log.info("⚠️ 无需改写：{}", query);
        return query;
    }

    /**
     * 同义词替换
     */
    private String rewriteBySynonym(String query) {
        // 完全匹配
        if (SYNONYM_MAP.containsKey(query)) {
            return SYNONYM_MAP.get(query);
        }

        // 包含匹配（如"北京大学"包含"北大"）
        for (Map.Entry<String, String> entry : SYNONYM_MAP.entrySet()) {
            if (query.contains(entry.getKey())) {
                String rewritten = query.replace(entry.getKey(), entry.getValue());
                log.debug("同义词替换：{} -> {}", entry.getKey(), entry.getValue());
                return rewritten;
            }
        }

        return query;
    }

    /**
     * 去除口语化词汇
     */
    private String removeFuzzyWords(String query) {
        String result = query;
        
        // 去除口语前缀
        String[] fuzzyPrefixes = {"我想去", "我要去", "带我去", "帮我找", "查找", "搜索"};
        for (String prefix : fuzzyPrefixes) {
            if (result.startsWith(prefix)) {
                result = result.substring(prefix.length()).trim();
            }
        }
        
        // 去除口语后缀
        String[] fuzzySuffixes = {"在哪里", "怎么走", "怎么去", "路线", "地址", "电话"};
        for (String suffix : fuzzySuffixes) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length()).trim();
            }
        }
        
        return result;
    }

    /**
     * 提取核心词（去除修饰词）
     */
    private String extractCoreWord(String query) {
        String result = query;
        
        // 去除程度副词
        String[] modifiers = {"最近的", "附近的", "最近的", "最好的", "最大的", "最小的"};
        for (String modifier : modifiers) {
            if (result.startsWith(modifier)) {
                result = result.substring(modifier.length()).trim();
            }
        }
        
        return result;
    }

    /**
     * 批量改写（用于多路召回）
     * @param query 原始查询
     * @return 改写版本列表（包含原词）
     */
    public List<String> rewriteMultiple(String query) {
        List<String> results = new ArrayList<>();
        
        // 添加原词
        results.add(query);
        
        // 同义词版本
        String synonymVersion = rewriteBySynonym(query);
        if (!synonymVersion.equals(query)) {
            results.add(synonymVersion);
        }
        
        // 去除口语版本
        String cleanedVersion = removeFuzzyWords(query);
        if (!cleanedVersion.equals(query)) {
            results.add(cleanedVersion);
        }
        
        // 去重
        return new ArrayList<>(new LinkedHashSet<>(results));
    }
}
