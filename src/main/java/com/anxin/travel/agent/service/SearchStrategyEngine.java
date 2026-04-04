package com.anxin.travel.agent.service;

import com.anxin.travel.module.map.client.AmapClient;
import com.anxin.travel.module.map.client.TencentMapClient;
import com.anxin.travel.module.map.dto.PoiDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 搜索策略引擎（最终稳定版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchStrategyEngine {

    private final AmapClient amapClient;
    private final TencentMapClient tencentMapClient; // 注入腾讯地图客户端
    
    // 知名地标坐标缓存（与 AgentService 保持同步）
    private static final Map<String, double[]> LANDMARK_COORDINATES = new HashMap<>();
    static {
        LANDMARK_COORDINATES.put("故宫", new double[]{39.916345, 116.397155});
        LANDMARK_COORDINATES.put("天安门", new double[]{39.908720, 116.397479});
        LANDMARK_COORDINATES.put("广州塔", new double[]{23.109, 113.319});
        LANDMARK_COORDINATES.put("东方明珠", new double[]{31.239689, 121.499755});
        LANDMARK_COORDINATES.put("西湖", new double[]{30.259661, 120.144490});
        LANDMARK_COORDINATES.put("韩山师范学院", new double[]{23.6705, 116.6547});
    }

    /**
     * 多路召回搜索引擎（腾讯地图优先）
     */
    public List<PoiDTO> search(String keyword, double lat, double lng) {
        log.info("🔍 多路召回启动：keyword={}, location=({}, {})", keyword, lat, lng);
        
        Set<String> uniqueKey = new HashSet<>();
        List<PoiDTO> allResults = new ArrayList<>();

        // ================== 1. 腾讯地图优先搜索 ==================
        log.info("🚀 路径 1: 腾讯地图优先搜索（主搜索源）");
        try {
            List<PoiDTO> tencentResults = tencentMapClient.searchNearbyPlaces(keyword, lat, lng, 50000);
            
            if (tencentResults != null && !tencentResults.isEmpty()) {
                addUnique(tencentResults, allResults, uniqueKey);
                log.info("✅ 腾讯地图找到 {} 个结果", tencentResults.size());
                
                // 【关键修改】如果腾讯地图找到>=3 个结果，直接返回，不再使用高德
                if (tencentResults.size() >= 3) {
                    log.info("✅ 腾讯地图结果充足（{} 个），直接返回，不调用高德 API", tencentResults.size());
                } else {
                    log.info("⚠️ 腾讯地图结果较少 ({})，继续高德地图补充", tencentResults.size());
                }
            } else {
                log.warn("⚠️ 腾讯地图无结果，切换到高德地图搜索");
            }
        } catch (Exception e) {
            log.error("❌ 腾讯地图搜索失败", e);
            log.warn("⚠️ 腾讯地图异常，切换到高德地图搜索");
        }

        // ================== 2. 高德地图多路召回（腾讯地图不足时的补充） ==================
        // 【关键修改】只有当腾讯地图结果 < 3 个时，才触发高德搜索
        if (allResults.size() < 3) {
            log.info("🗺️ 开始高德地图多路召回...");
            
            // 2.1 地理编码优先（如果 keyword 像地址）
            if (isAddressLike(keyword)) {
                try {
                    log.info("📍 路径 2.1: 地理编码优先");
                    List<PoiDTO> geocodeResults = amapClient.searchByGeocode(keyword, lat, lng);
                    addUnique(geocodeResults, allResults, uniqueKey);
                    if (!allResults.isEmpty()) {
                        log.info("✅ 地理编码召回 {} 个结果", allResults.size());
                    }
                } catch (Exception e) {
                    log.error("❌ 地理编码失败", e);
                }
            }
            
            // 2.2 本地城市精确搜索
            String adcode = amapClient.getAdcodeByLocation(lat, lng);
            if (adcode != null && allResults.size() < 3) {
                try {
                    log.info("🏙️ 路径 2.2: 本地城市精确搜索（adcode={})", adcode);
                    List<PoiDTO> localResults = amapClient.searchNearbyPlaces(
                            keyword, lat, lng, 1, 20, true, false, 10000, true, null);
                    addUnique(localResults, allResults, uniqueKey);
                    log.info("✅ 本地搜索召回 {} 个结果，累计 {} 个", 
                            localResults == null ? 0 : localResults.size(), allResults.size());
                } catch (Exception e) {
                    log.error("❌ 本地搜索失败", e);
                }
            }

            // 2.3 扩大半径到 50km
            if (allResults.size() < 3) {
                try {
                    log.info("🚀 路径 2.3: 扩大半径到 50km");
                    List<PoiDTO> radiusResults = amapClient.searchNearbyPlaces(
                            keyword, lat, lng, 1, 20, true, false, 50000, true, null);
                    addUnique(radiusResults, allResults, uniqueKey);
                    log.info("✅ 扩大半径召回 {} 个结果，累计 {} 个", 
                            radiusResults == null ? 0 : radiusResults.size(), allResults.size());
                } catch (Exception e) {
                    log.error("❌ 扩大半径失败", e);
                }
            }

            // 2.4 周边城市搜索
            if (allResults.size() < 3) {
                String nearbyAdcode = amapClient.getAdcodeByLocation(lat, lng);
                if (nearbyAdcode != null) {
                    try {
                        log.info("🏙️ 路径 2.4: 周边城市搜索");
                        List<String> nearbyAdcodes = getNearbyAdcodes(nearbyAdcode);
                        for (String nearbyAdcodeValue : nearbyAdcodes) {
                            double[] cityCenter = getCityCenter(nearbyAdcodeValue);
                            if (cityCenter != null) {
                                List<PoiDTO> nearbyResults = amapClient.searchNearbyPlaces(
                                        keyword, cityCenter[0], cityCenter[1], 1, 20, 
                                        true, false, 50000, true, null);
                                addUnique(nearbyResults, allResults, uniqueKey);
                                if (allResults.size() >= 15) break;
                            }
                        }
                        log.info("✅ 周边城市召回，累计 {} 个结果", allResults.size());
                    } catch (Exception e) {
                        log.error("❌ 周边城市搜索失败", e);
                    }
                }
            }

            // 2.5 省级搜索
            if (allResults.size() < 3) {
                try {
                    log.info("🏞️ 路径 2.5: 省级搜索（fallback）");
                    List<PoiDTO> provinceResults = amapClient.searchNearbyPlaces(
                            keyword, lat, lng, 1, 20, true, true, 50000, true, null);
                    addUnique(provinceResults, allResults, uniqueKey);
                    log.info("✅ 省级搜索召回 {} 个结果，累计 {} 个", 
                            provinceResults == null ? 0 : provinceResults.size(), allResults.size());
                } catch (Exception e) {
                    log.error("❌ 省级搜索失败", e);
                }
            }

            // 2.6 全国搜索（最终兜底）+ 质量门判断
            if (allResults.size() < 3) {
                try {
                    log.info("🇨🇳 路径 2.6: 全国搜索（最终兜底）+");
                    List<PoiDTO> nationwideResults = amapClient.searchByKeywordNationwide(keyword, 1, 20);
                    
                    // 【关键质量门】过滤距离过远的不相关结果（阈值：500km）
                    if (nationwideResults != null && !nationwideResults.isEmpty()) {
                        final double MAX_ACCEPTABLE_DISTANCE = 500_000; // 500km
                        List<PoiDTO> filteredResults = new ArrayList<>();
                        
                        for (PoiDTO poi : nationwideResults) {
                            double distance = calculateDistance(lat, lng, poi.getLat(), poi.getLng());
                            poi.setDistance(distance);
                            
                            // 只保留 500km 内的结果
                            if (distance <= MAX_ACCEPTABLE_DISTANCE) {
                                filteredResults.add(poi);
                                log.info("✅ 全国搜索结果可接受：name={}, distance={:.1f}km", poi.getName(), distance / 1000);
                            } else {
                                log.warn("❌ 过滤过远结果：name={}, distance={:.1f}km（>{:.0f}km）", 
                                    poi.getName(), distance / 1000, MAX_ACCEPTABLE_DISTANCE / 1000);
                            }
                        }
                        
                        // 应用质量门判断
                        if (isResultQualityAcceptable(filteredResults, keyword)) {
                            addUnique(filteredResults, allResults, uniqueKey);
                            log.info("✅ 全国搜索召回 {} 个有效结果，累计 {} 个", 
                                    filteredResults.size(), allResults.size());
                        } else {
                            log.warn("⚠️ 全国搜索结果质量不达标，已丢弃所有结果");
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ 全国搜索失败", e);
                }
            }
        } else {
            log.info("✅ 腾讯地图已找到足够结果，跳过高德地图搜索");
        }

        // ================== 统一排序 ==================
        if (!allResults.isEmpty()) {
            log.info("📊 所有路径完成，累计 {} 个结果，开始统一排序...", allResults.size());
            
            // 计算每个 POI 的距离（如果已有则跳过）
            for (PoiDTO poi : allResults) {
                if (poi.getDistance() <= 0) {
                    double dist = calculateDistance(lat, lng, poi.getLat(), poi.getLng());
                    poi.setDistance(dist);
                }
            }
            
            // 排序：使用 computeRelevanceScore
            allResults.sort((a, b) -> {
                double scoreA = computeRelevanceScore(a, keyword, lat, lng);
                double scoreB = computeRelevanceScore(b, keyword, lat, lng);
                return Double.compare(scoreB, scoreA);
            });
            
            // 取前 20 个
            List<PoiDTO> finalResults = allResults.stream().limit(20).collect(Collectors.toList());
            
            log.info("✅ 多路召回完成，最终返回 {} 个结果", finalResults.size());
            return finalResults;
        }

        log.warn("⚠️ 所有召回路径均无结果");
        return new ArrayList<>();
    }
    
    /**
     * 添加不重复的 POI 到结果列表
     */
    private void addUnique(List<PoiDTO> source, List<PoiDTO> target, Set<String> uniqueKey) {
        if (source == null || source.isEmpty()) return;
        for (PoiDTO poi : source) {
            String key = poi.getName() + "_" + poi.getAddress();
            if (uniqueKey.add(key)) {
                target.add(poi);
            }
        }
    }
    
    /**
     * 判断关键词是否像地址
     */
    private boolean isAddressLike(String keyword) {
        // 简单判断：包含省/市/区/镇/乡/村/路/街/大道/巷/号/栋/楼等字眼
        return keyword.matches(".*(省 | 市 | 区 | 县 | 镇 | 乡 | 村 | 路 | 街 | 大道 | 巷 | 号 | 栋 | 楼).*");
    }
    
    /**
     * 获取周边城市 adcode 列表
     */
    private List<String> getNearbyAdcodes(String adcode) {
        // 实际应调用高德行政区域查询接口动态获取，这里简化示例
        Map<String, List<String>> nearbyMap = new HashMap<>();
        // 潮州 (445100) 周边：汕头 (440500)、揭阳 (445200)
        nearbyMap.put("445100", Arrays.asList("440500", "445200"));
        // 可扩展更多
        List<String> result = nearbyMap.getOrDefault(adcode, Collections.emptyList());
        log.debug("🏙️ 获取周边城市 adcode: {}, 结果：{}", adcode, result);
        return result;
    }
    
    /**
     * 获取城市中心坐标
     */
    private double[] getCityCenter(String adcode) {
        // 通过高德行政区域查询获取城市中心坐标，这里返回近似值
        Map<String, double[]> cityCenterMap = new HashMap<>();
        cityCenterMap.put("440500", new double[]{23.3535, 116.6819}); // 汕头
        cityCenterMap.put("445200", new double[]{23.5707, 116.3655}); // 揭阳
        double[] result = cityCenterMap.get(adcode);
        if (result != null) {
            log.debug("📍 获取城市中心：adcode={}, coords=({}, {})", adcode, result[0], result[1]);
        }
        return result;
    }
    
    /**
     * 综合相关性评分（对标高德地图排序算法）
     */
    private double computeRelevanceScore(PoiDTO poi, String keyword, double lat, double lng) {
        double score = 0;
        
        // 1. 名称匹配权重（最高 50 分）
        if (poi.getName() != null) {
            // 完全匹配
            if (poi.getName().equals(keyword)) {
                score += 50;
            }
            // 包含匹配
            else if (poi.getName().contains(keyword)) {
                score += 30;
            }
            // 包含核心词
            else {
                String coreKeyword = keyword.replaceAll("大学 | 学院 | 校区 | 学校 | 医院 | 餐厅 | 酒店 | 超市 | 银行", "").trim();
                if (coreKeyword.length() >= 2 && poi.getName().contains(coreKeyword)) {
                    score += 20;
                }
            }
        }
        
        // 2. 距离权重（最高 30 分，越近分数越高）
        double distance = poi.getDistance();
        if (distance > 0) {
            if (distance < 1000) {
                score += 30;  // 1km 内
            } else if (distance < 5000) {
                score += 20;  // 5km 内
            } else if (distance < 10000) {
                score += 10;  // 10km 内
            } else {
                score += 5;   // 10km 外
            }
        }
        
        // 3. 地址相关性（最高 20 分）
        if (poi.getAddress() != null && poi.getAddress().contains(keyword)) {
            score += 20;
        }
        
        return score;
    }
    
    /**
     * 判断搜索结果质量是否可接受（防止脏数据）
     */
    private boolean isResultQualityAcceptable(List<PoiDTO> results, String keyword) {
        if (results == null || results.isEmpty()) return false;
        
        // 黑名单词（如果结果中大量包含这些词，说明是脏数据）
        String[] blacklist = {"理发", "美发", "美容", "烫发", "染发", "剪发", "造型", "形象设计",
                              "维修", "打印", "中介", "工作室", "宿舍", "停车场", "小卖部"};
        
        // 提取关键词的核心部分（去除后缀）
        String coreKeyword = keyword.replaceAll("大学 | 学院 | 校区 | 学校 | 医院 | 餐厅 | 酒店 | 超市 | 银行", "").trim();
        if (coreKeyword.length() < 2) coreKeyword = keyword; // 太短则用原词
        
        int badCount = 0;
        int relevantCount = 0;
        
        for (PoiDTO poi : results) {
            String name = poi.getName();
            if (name == null) continue;
            
            // 检查是否包含黑名单词
            boolean isBad = false;
            for (String bad : blacklist) {
                if (name.contains(bad)) {
                    isBad = true;
                    break;
                }
            }
            if (isBad) {
                badCount++;
            }
            
            // 检查是否与关键词相关（名称包含核心词）
            if (name.contains(coreKeyword) || name.contains(keyword)) {
                relevantCount++;
            }
        }
        
        // 判定规则：
        // 1. 如果超过 50% 的结果是黑名单脏数据 -> 不可接受
        // 2. 如果没有一个结果包含核心关键词 -> 不可接受
        // 3. 否则可接受
        boolean acceptable = !(badCount > results.size() * 0.5 || relevantCount == 0);
        
        log.info("📊 质量评估：总结果={}, 脏数据数={}, 相关结果数={}, 可接受={}", 
                 results.size(), badCount, relevantCount, acceptable);
        return acceptable;
    }
    
    /**
     * 获取备选坐标 POI（用于高德无结果时的兜底）
     */
    private PoiDTO getFallbackPoi(String keyword, double userLat, double userLng) {
        double[] coords = LANDMARK_COORDINATES.get(keyword);
        if (coords == null) return null;
        
        PoiDTO fallback = new PoiDTO();
        fallback.setName(keyword);
        fallback.setAddress(keyword + "（备选坐标，实际位置可能略有偏差）");
        fallback.setLat(coords[0]);
        fallback.setLng(coords[1]);
        fallback.setDistance(calculateDistance(userLat, userLng, coords[0], coords[1]));
        fallback.setType("风景名胜");
        return fallback;
    }
    
    /**
     * 计算两点距离（Haversine 公式）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
