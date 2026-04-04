# /api/agent/confirm 接口联调测试用例

## 接口信息
- **路径**: `POST /api/agent/confirm`
- **用途**: 用户确认选择目的地后，创建订单
- **环境**: Java 17 + Spring Boot

---

## ✅ 测试用例 1：正常请求（期望成功）

### 请求参数
```bash
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": "test-session-123",
    "selectedPoiName": "潮州市中心医院",
    "lat": 23.65392678909576,
    "lng": 116.67702550971906
  }'
```

### 期望响应（严格按前端文档）
```json
{
  "code": 200,
  "message": "操作成功",
  "success": true,
  "data": {
    "type": "ORDER",
    "message": "订单创建成功",
    "poi": {
      "id": "潮州市中心医院",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区某某路",
      "lat": 23.658,
      "lng": 116.625
    },
    "route": {
      "distance": 5200,
      "duration": 720,
      "price": 25.5
    }
  },
  "timestamp": 1717516800000
}
```

### 验证点
- ✅ `code` = 200
- ✅ `success` = true
- ✅ `data.type` = "ORDER"
- ✅ `data.message` = "订单创建成功"
- ✅ `data.poi` 包含完整字段（id, name, address, lat, lng）
- ✅ `data.route` 包含完整字段（distance, duration, price）
- ✅ `distance` 单位是米
- ✅ `duration` 单位是秒
- ✅ `price` 单位是元

---

## ❌ 测试用例 2：缺少位置参数

### 请求参数
```bash
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": "test-session-123",
    "selectedPoiName": "潮州市中心医院"
  }'
```

### 期望响应
```json
{
  "code": 400,
  "message": "位置信息缺失",
  "success": false
}
```

### 验证点
- ✅ `code` = 400（不是 500）
- ✅ `message` = "位置信息缺失"（明确提示）
- ✅ `success` = false

---

## ❌ 测试用例 3：sessionId 缺失

### 请求参数
```bash
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "selectedPoiName": "潮州市中心医院",
    "lat": 23.65392678909576,
    "lng": 116.67702550971906
  }'
```

### 期望响应
```json
{
  "code": 400,
  "message": "会话已过期",
  "success": false
}
```

### 验证点
- ✅ `code` = 400
- ✅ `message` = "会话已过期"（引导用户重新搜索）

---

## ❌ 测试用例 4：POI 名称缺失

### 请求参数
```bash
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": "test-session-123",
    "lat": 23.65392678909576,
    "lng": 116.67702550971906
  }'
```

### 期望响应
```json
{
  "code": 400,
  "message": "POI 名称不能为空",
  "success": false
}
```

### 验证点
- ✅ `code` = 400
- ✅ `message` = "POI 名称不能为空"

---

## ❌ 测试用例 5：坐标超出范围

### 请求参数
```bash
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": "test-session-123",
    "selectedPoiName": "潮州市中心医院",
    "lat": 95.0,
    "lng": 116.67702550971906
  }'
```

### 期望响应
```json
{
  "code": 400,
  "message": "经纬度坐标超出有效范围",
  "success": false
}
```

### 验证点
- ✅ `code` = 400
- ✅ `message` = "经纬度坐标超出有效范围"

---

## ❌ 测试用例 6：会话不存在（需要实现会话校验）

### 请求参数
```bash
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": "non-existent-session",
    "selectedPoiName": "潮州市中心医院",
    "lat": 23.65392678909576,
    "lng": 116.67702550971906
  }'
```

### 期望响应
```json
{
  "code": 400,
  "message": "会话已过期，请重新搜索",
  "success": false
}
```

### 验证点
- ✅ `code` = 400
- ✅ `message` = "会话已过期，请重新搜索"

---

## ❌ 测试用例 7：无法计算路线（地点之间无法规划）

### 请求参数
```bash
curl -X POST http://localhost:8080/api/agent/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": "test-session-123",
    "selectedPoiName": "某个偏远地点",
    "lat": 23.65392678909576,
    "lng": 116.67702550971906
  }'
```

### 期望响应
```json
{
  "code": 500,
  "message": "处理失败：无法计算路线，请检查起点和终点位置是否有效",
  "success": false
}
```

### 验证点
- ✅ `code` = 500
- ✅ `message` 包含具体错误原因（不是"系统繁忙"）

---

## 📋 错误码对照表

| code | message | 触发场景 | 前端处理方式 |
|------|---------|----------|-------------|
| 200 | 操作成功 | 订单创建成功 | 显示成功，跳转订单详情 |
| 400 | 会话已过期 | sessionId 为空或不存在 | 引导用户重新搜索 |
| 400 | POI 名称不能为空 | selectedPoiName 缺失 | 检查前端表单验证 |
| 400 | 位置信息缺失 | lat 或 lng 为 null | 检查是否传递了位置参数 |
| 400 | 经纬度坐标超出有效范围 | lat > 90 或 lng > 180 | 提示用户位置异常 |
| 500 | 处理失败：XXX | 路线计算失败、系统异常 | 根据具体错误提示用户 |

---

## 🔍 调试技巧

### 1. 查看后端日志
```bash
# 实时查看日志
tail -f logs/travel.log | grep "确认选择"

# 筛选关键日志
grep "sessionId=.*lat=" logs/travel.log
```

### 2. 检查数据库
```sql
-- 检查会话是否存在
SELECT * FROM agent_state WHERE session_id = 'test-session-123';

-- 检查缓存的 POI
SELECT * FROM redis_cache WHERE key LIKE '%test-session-123%';
```

### 3. Postman 测试集合
导入以下 JSON 到 Postman：
```json
{
  "info": {
    "name": "Agent Confirm API Tests"
  },
  "item": [
    {
      "name": "正常请求",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Content-Type", "value": "application/json"},
          {"key": "X-User-Id", "value": "1"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"sessionId\": \"test-session-123\",\n  \"selectedPoiName\": \"潮州市中心医院\",\n  \"lat\": 23.65392678909576,\n  \"lng\": 116.67702550971906\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/agent/confirm"
        }
      }
    }
  ]
}
```

---

## ✅ 联调通过标准

1. ✅ 所有测试用例的响应格式符合前端文档
2. ✅ 错误码映射正确（400/500 区分清晰）
3. ✅ 错误提示明确，无"系统繁忙"等模糊描述
4. ✅ data 结构完整包含 type/message/poi/route
5. ✅ poi 和 route 字段嵌套在 data 中（不是在顶层）
6. ✅ 距离/时长/价格单位正确（米/秒/元）

---

**文档版本**: v1.0  
**创建时间**: 2026-04-04  
**适用环境**: 开发环境/测试环境/生产环境
