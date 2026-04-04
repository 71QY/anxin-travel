# /api/agent/confirm 接口注意事项

## 📋 接口信息
- **路径**: `POST /api/agent/confirm`
- **用途**: 用户确认选择目的地后，创建订单
- **重要性**: ⭐⭐⭐⭐⭐ (核心下单接口)

---

## ⚠️ **关键问题说明**

### **当前问题现象**
前端调用接口时一直返回：
```json
{
  "code": 500,
  "message": "系统繁忙，请稍后再试",
  "success": false
}
```

### **根本原因分析**
前端请求参数中**已包含完整的位置信息**（lat 和 lng），但后端可能：
1. ❌ 没有正确接收或解析位置参数
2. ❌ 使用位置参数计算路线时发生异常
3. ❌ 异常处理不当，返回了模糊的"系统繁忙"错误

---

## 🔧 **请求参数规范**

### ✅ **正确的请求示例**
```json
{
  "sessionId": "e33f98ad-69c5-43a0-86b9-8c25816b4a18",
  "selectedPoiName": "潮州市中心医院",
  "lat": 23.65392678909576,
  "lng": 116.67702550971906
}
```

### ❌ **错误的请求示例（缺少位置）**
```json
{
  "sessionId": "e33f98ad-69c5-43a0-86b9-8c25816b4a18",
  "selectedPoiName": "潮州市中心医院"
  // ❌ 缺少 lat 和 lng，导致无法计算路线
}
```

---

## 📊 **必需参数说明**

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| sessionId | String | ✅ | 会话 ID（必须与之前搜索时保持一致） | "e33f98ad-69c5-43a0-86b9-8c25816b4a18" |
| selectedPoiName | String | ✅ | 选择的 POI 名称（必须与搜索结果中的 name 完全匹配） | "潮州市中心医院" |
| lat | Double | ✅ | **用户当前纬度**（用于计算起点到终点的路线） | 23.65392678909576 |
| lng | Double | ✅ | **用户当前经度**（用于计算起点到终点的路线） | 116.67702550971906 |

---

## 💡 **后端处理建议**

### **1. 参数校验顺序**
```java
// 推荐校验顺序
if (sessionId == null || sessionId.isBlank()) {
    return error("会话 ID 不能为空");
}

if (selectedPoiName == null || selectedPoiName.isBlank()) {
    return error("POI 名称不能为空");
}

if (lat == null || lng == null) {
    return error("位置信息缺失，无法计算路线");  // ⭐ 明确的错误提示
}

if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    return error("经纬度坐标超出有效范围");
}
```

### **2. 路线计算异常处理**
```java
try {
    // 调用地图 API 计算路线
    RouteInfo route = mapService.calculateRoute(userLat, userLng, poiLat, poiLng);
    
    if (route == null) {
        return error("无法规划路线，请检查起点和终点是否有效");  // ⭐ 具体错误原因
    }
    
    // 创建订单逻辑...
} catch (Exception e) {
    log.error("路线计算失败", e);
    return error("路线计算失败：" + e.getMessage());  // ⭐ 避免返回"系统繁忙"
}
```

### **3. 会话校验**
```java
// 验证 sessionId 是否有效
Session session = sessionRepository.findById(sessionId);
if (session == null || session.isExpired()) {
    return error("会话已过期，请重新搜索");  // ⭐ 明确提示
}
```

---

## 📝 **响应格式规范**

### ✅ **成功响应（期望返回）**
```json
{
  "code": 200,
  "message": "确认成功",
  "success": true,
  "data": {
    "type": "ORDER",
    "message": "已为您创建订单",
    "poi": {
      "id": "poi_123",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区某某路",
      "lat": 23.658,
      "lng": 116.625
    },
    "route": {
      "distance": 5200,        // 单位：米
      "duration": 720,         // 单位：秒
      "price": 25.5            // 单位：元
    }
  },
  "timestamp": 1717516800000
}
```

### ❌ **不推荐的错误响应（太模糊）**
```json
{
  "code": 500,
  "message": "系统繁忙",
  "success": false
}
```

### ✅ **推荐的错误响应（具体原因）**
```json
{
  "code": 400,
  "message": "位置信息缺失，无法计算路线",
  "success": false
}
```

或者：
```json
{
  "code": 400,
  "message": "会话已过期，请重新搜索",
  "success": false
}
```

---

## 🐛 **常见问题排查清单**

### **Q1: 前端一直显示"系统繁忙"**
**排查步骤**：
1. ✅ 检查后端日志，查看具体异常堆栈
2. ✅ 确认接收到了完整的 4 个参数（特别是 lat 和 lng）
3. ✅ 验证 sessionId 是否在数据库中存在且未过期
4. ✅ 检查地图 API 密钥是否正确配置
5. ✅ 验证地图 API 服务是否正常

**可能原因**：
- 空指针异常（NPE）：使用了为 null 的 lat 或 lng
- 路线计算 API 调用失败
- 数据库查询超时

---

### **Q2: 响应中 route 字段为空**
**可能原因**：
- 起点（用户位置）或终点（POI 位置）坐标无效
- 地图 API 返回错误（如配额超限、密钥错误）
- 两点之间无法规划路线（如跨海、超出服务范围）

**解决方案**：
- 检查 lat/lng 是否在合理范围内
- 检查地图 API 账户余额和配额
- 添加降级策略（如返回预估距离）

---

### **Q3: POI 名称匹配失败**
**可能原因**：
- selectedPoiName 与数据库中存储的名称不完全一致
- 存在特殊字符、空格、繁简体差异

**解决方案**：
- 使用 POI ID 而非名称进行匹配（更可靠）
- 或者进行模糊匹配（忽略空格、大小写）

---

## 🎯 **前端期望的错误码映射**

| HTTP 状态码 | code | message 示例 | 前端处理方式 |
|------------|------|-------------|-------------|
| 200 | 200 | 确认成功 | 显示成功提示，跳转订单详情 |
| 400 | 400 | 会话已过期 | 引导用户重新搜索 |
| 400 | 400 | 位置信息缺失 | 检查前端是否传递了 lat/lng |
| 400 | 400 | 无法计算路线 | 提示用户更换目的地 |
| 400 | 400 | 未找到匹配的地点 | 重新搜索 |
| 401 | 401 | Token 无效 | 跳转登录页 |
| 500 | 500 | **具体的错误原因** | 根据具体错误提示用户 |
| 500 | 500 | ~~系统繁忙~~ | ❌ 避免这种模糊提示 |

---

## 📞 **调试建议**

### **1. 添加详细日志**
```java
log.info("=== 开始处理确认请求 ===");
log.info("sessionId={}", sessionId);
log.info("selectedPoiName={}", selectedPoiName);
log.info("lat={}, lng={}", lat, lng);  // ⭐ 关键：记录位置参数

try {
    // 业务逻辑...
} catch (Exception e) {
    log.error("处理确认请求失败", e);  // ⭐ 记录完整堆栈
    throw e;
}
```

### **2. 使用 Postman 测试**
```bash
# 测试用例 1：正常请求
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session-123",
    "selectedPoiName": "测试地点",
    "lat": 23.6539,
    "lng": 116.6770
  }'

# 测试用例 2：缺少位置参数（应该返回明确错误）
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session-123",
    "selectedPoiName": "测试地点"
  }'
```

### **3. 数据库检查**
```sql
-- 检查会话是否存在
SELECT * FROM sessions WHERE session_id = 'e33f98ad-69c5-43a0-86b9-8c25816b4a18';

-- 检查 POI 是否存在
SELECT * FROM pois WHERE name = '潮州市中心医院';
```

---

## 💬 **与前端协作建议**

1. **建立快速沟通渠道**
   - 拉前后端开发人员和产品经理到同一群组
   - 实时同步问题和进展

2. **共享调试工具**
   - 使用相同的 Postman 测试集合
   - 共享测试账号和 sessionId

3. **统一错误码文档**
   - 维护在线的错误码对照表
   - 及时更新新增的错误场景

4. **灰度发布**
   - 先在测试环境充分测试
   - 小流量验证后再全量发布

---

## 📌 **总结**

**核心诉求**：
1. ✅ 确保接口能正确接收和处理位置参数（lat、lng）
2. ✅ 提供明确的错误提示，避免"系统繁忙"等模糊描述
3. ✅ 完善异常处理和日志记录，便于问题排查
4. ✅ 保证路线计算服务的稳定性和可用性

**预期效果**：
- 前端能够根据明确的错误提示快速定位问题
- 减少前后端沟通成本
- 提升用户体验和下单成功率

---

**文档版本**: v1.0  
**更新时间**: 2026-04-04  
**联系人**: 前端开发团队
