# 行程实时追踪 - 前端对接技术文档（完整版）

## 📋 文档概述

本文档说明**叫车订单创建后，行程实时追踪功能**的前后端对接规范。包括：
- 所有订单相关接口
- WebSocket 实时推送消息格式
- 前端需要实现的UI效果和交互逻辑

---

## 一、通用响应格式

**所有接口返回统一格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

**错误响应**：
```json
{
  "code": 500,
  "message": "错误描述",
  "data": null
}
```

---

## 二、订单相关接口

### 2.1 自己叫车 - 创建订单

**接口地址**: `POST /api/order/create`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json
```

**请求参数**:
```json
{
  "destName": "目的地名称",
  "destLat": 23.7000,
  "destLng": 116.7000
}
```

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 123,
    "orderNo": "AX202604151234567890",
    "userId": 7,
    "driverId": null,
    "startLat": 23.6533,
    "startLng": 116.6772,
    "destLat": 23.7000,
    "destLng": 116.7000,
    "destAddress": "目的地名称",
    "poiName": "目的地名称",
    "status": 0,
    "platformUsed": "gaode",
    "estimatePrice": 25.50,
    "actualPrice": null,
    "createTime": "2026-04-15T12:34:56",
    
    // ⭐ 司机信息（立即返回，用于前端立即显示）
    "driverName": "李师傅",
    "driverPhone": "13812345678",
    "driverAvatar": "https://api.dicebear.com/7.x/avataaars/svg?seed=李师傅",
    "carNo": "京A 8D231",
    "carType": "大众朗逸",
    "carColor": "白色",
    "rating": 4.9,
    "driverLat": 23.6583,
    "driverLng": 116.6822
  }
}
```

**前端处理逻辑**:
1. ✅ **收到响应后立即跳转到行程页面**
2. ✅ **使用返回的司机信息显示司机卡片**
3. ✅ **使用 driverLat/driverLng 在地图上显示小车初始位置**
4. ✅ **建立 WebSocket 连接，监听后续推送**

---

### 2.2 查询订单详情

**接口地址**: `GET /api/order/{id}`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
```

**路径参数**:
- `id`: 订单ID

**响应数据**: 同创建订单响应（包含司机信息）

**使用场景**:
- 刷新页面时重新获取订单信息
- 从订单列表点击进入详情

---

### 2.3 查询订单列表

**接口地址**: `GET /api/order/list`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
```

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 否 | 订单状态筛选（0-待确认, 1-已确认, 2-已接单, 3-行程中, 4-已完成, 5-已取消, 6-已拒绝） |
| page | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页数量，默认10 |

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 123,
        "orderNo": "AX202604151234567890",
        "poiName": "目的地名称",
        "destAddress": "目的地名称",
        "status": 2,
        "estimatePrice": 25.50,
        "createTime": "2026-04-15T12:34:56",
        
        // 如果有司机信息
        "driverName": "李师傅",
        "carNo": "京A 8D231",
        "carType": "大众朗逸",
        "carColor": "白色"
      }
    ],
    "total": 10,
    "page": 1,
    "size": 10
  }
}
```

---

### 2.4 查询当前进行中的订单

**接口地址**: `GET /api/order/current`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
```

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    // 订单信息（同订单详情）
    "id": 123,
    "status": 2,
    "driverName": "李师傅",
    ...
  }
}
```

**如果没有进行中的订单**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**使用场景**:
- 长辈模式首页显示当前行程
- 亲友查看代叫车状态

---

### 2.5 取消订单

**接口地址**: `POST /api/order/{id}/cancel`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
```

**路径参数**:
- `id`: 订单ID

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**限制条件**:
- 只能取消状态为 0（待确认）的订单
- 只能取消自己的订单

---

### 2.6 确认订单

**接口地址**: `POST /api/order/{id}/confirm`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
```

**路径参数**:
- `id`: 订单ID

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**限制条件**:
- 只能确认状态为 1（已确认/待接单）的订单
- 确认后订单状态变为 3（行程中）

---

## 三、代叫车相关接口

### 3.1 亲友代叫车

**接口地址**: `POST /api/guard/proxy-order`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json
```

**请求参数**:
```json
{
  "elderId": 5,
  "startLat": 23.6533,
  "startLng": 116.6772,
  "destLat": 23.7000,
  "destLng": 116.7000,
  "destAddress": "目的地名称",
  "needConfirm": false
}
```

**响应数据**: 同自己叫车（但司机信息由WebSocket推送）

**长辈端接收 WebSocket 消息**:
```json
{
  "type": "ORDER_CREATED",
  "success": true,
  "message": "您的亲友张三为您叫了一辆车",
  "data": {
    "orderId": 123,
    "orderNo": "AX202604151234567890",
    "status": 1,
    "userId": 5,
    "guardianUserId": 7,
    "destLat": 23.7000,
    "destLng": 116.7000,
    "poiName": "目的地名称",
    "destAddress": "目的地名称",
    "estimatePrice": 0.0,
    "createTime": "2026-04-15T12:34:56",
    "requesterName": "张三",
    "destination": "目的地名称"
  }
}
```

**前端处理逻辑**:
1. ✅ **长辈收到 ORDER_CREATED 后跳转到行程页面**
2. ✅ **显示"亲友代叫"标识**
3. ✅ **等待 WebSocket 推送司机信息（ORDER_ACCEPTED）**

---

### 3.2 长辈确认代叫车

**接口地址**: `POST /api/guard/confirm-proxy-order/{orderId}`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json
```

**路径参数**:
- `orderId`: 订单ID

**请求参数**:
```json
{
  "confirmed": true,
  "rejectReason": ""  // 如果 confirmed=false，填写拒绝原因
}
```

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": 123,
    "status": "CONFIRMED"  // 或 "REJECTED"
  }
}
```

**确认后触发**:
- 订单状态更新为 1
- 触发司机分配（WebSocket推送 ORDER_ACCEPTED）
- 通知亲友确认结果

---

### 3.3 查询代叫车记录

**接口地址**: `GET /api/guard/proxy-orders`

**请求头**:
```
Authorization: Bearer {JWT_TOKEN}
```

**响应数据**: 订单列表数组

---

## 四、WebSocket 实时推送

### 4.1 连接方式

**WebSocket 地址**: `ws://your-domain/ws/agent?token={JWT_TOKEN}`

**连接成功后自动收到欢迎消息**:
```json
{
  "type": "connected",
  "success": true,
  "message": "欢迎使用智能出行助手！",
  "timestamp": 1713153296000
}
```

---

### 4.2 ORDER_ACCEPTED（司机已接单）

**触发时机**: 订单创建后 1-2 秒异步推送

**消息格式**:
```json
{
  "type": "ORDER_ACCEPTED",
  "orderId": 123,
  "success": true,
  "message": "司机已接单，正在赶来",
  
  // 司机完整信息
  "driverName": "李师傅",
  "driverPhone": "13812345678",
  "driverAvatar": "https://api.dicebear.com/7.x/avataaars/svg?seed=李师傅",
  "carNo": "京A 8D231",
  "carType": "大众朗逸",
  "carColor": "白色",
  "rating": 4.9,
  
  // 位置信息
  "driverLat": 23.6583,
  "driverLng": 116.6822,
  "startLat": 23.6533,
  "startLng": 116.6772,
  "destLat": 23.7000,
  "destLng": 116.7000
}
```

**前端实现效果**:
1. ✅ **顶部显示状态条**: "已接单 - 司机赶来中"
2. ✅ **显示司机信息卡片**:
   ```
   ┌─────────────────────────────┐
   │ 👤 李师傅  ⭐ 4.9          │
   │ 🚗 京A 8D231                │
   │ 🎨 白色 大众朗逸            │
   │ 📞 13812345678              │
   └─────────────────────────────┘
   ```
3. ✅ **在地图上绘制路线**: 起点 → 终点
4. ✅ **在 driverLat/driverLng 位置显示小车图标** 🚕
5. ✅ **开始倒计时**: "预计3分钟到达"

---

### 4.3 DRIVER_LOCATION（司机位置更新）

**触发时机**: 每 3 秒推送一次，共推送 5 次

**消息格式**:
```json
{
  "type": "DRIVER_LOCATION",
  "orderId": 123,
  "driverLat": 23.6550,
  "driverLng": 116.6790,
  "etaMinutes": 3
}
```

**推送序列示例**:
```
第1次: etaMinutes=4, 距离起点约400米
第2次: etaMinutes=3, 距离起点约300米
第3次: etaMinutes=2, 距离起点约200米
第4次: etaMinutes=1, 距离起点约100米
第5次: etaMinutes=0, 即将到达
```

**前端实现效果**:
1. ✅ **小车图标平滑移动**到新坐标（使用动画过渡，不要瞬移）
2. ✅ **更新倒计时文本**: "预计 X 分钟到达"
3. ✅ **可选**: 显示进度条或距离提示

**动画建议**:
```javascript
// 伪代码示例
marker.animateTo({
  lat: message.driverLat,
  lng: message.driverLng,
  duration: 2500,  // 2.5秒动画
  easing: 'linear'
});
```

---

### 4.4 DRIVER_ARRIVED（司机已到达）

**触发时机**: 5次位置更新完成后推送

**消息格式**:
```json
{
  "type": "DRIVER_ARRIVED",
  "orderId": 123,
  "message": "司机已到达上车点，请上车",
  "driverLat": 23.6533,
  "driverLng": 116.6772
}
```

**前端实现效果**:
1. ✅ **状态条更新**: "已到达 - 请上车"
2. ✅ **显示醒目提示**: 
   - Toast/弹窗: "🎉 司机已到达上车点，请上车"
   - 或者高亮显示司机卡片
3. ✅ **小车图标停在起点位置**
4. ✅ **可选**: 播放提示音

---

### 4.5 PROXY_ORDER_CONFIRMED（代叫车确认结果）

**触发时机**: 长辈确认或拒绝代叫车后

**消息格式（同意）**:
```json
{
  "type": "PROXY_ORDER_CONFIRMED",
  "orderId": 123,
  "elderUserId": 5,
  "confirmed": true,
  "confirmTime": "2026-04-15T12:35:00"
}
```

**消息格式（拒绝）**:
```json
{
  "type": "PROXY_ORDER_CONFIRMED",
  "orderId": 123,
  "elderUserId": 5,
  "confirmed": false,
  "rejectReason": "暂时不需要",
  "confirmTime": "2026-04-15T12:35:00"
}
```

**前端实现效果（亲友端）**:
- 同意：显示"长辈已确认，司机正在赶来"
- 拒绝：显示"长辈拒绝了代叫车，原因：XXX"

---

## 五、前端UI实现规范

### 5.1 行程页面布局

```
┌──────────────────────────────────┐
│  ← 返回        行程进行中         │
├──────────────────────────────────┤
│                                  │
│     【地图区域 - 占屏幕70%】      │
│                                  │
│  🚕 小车图标（动态移动）          │
│  📍 起点标记                      │
│  🏁 终点标记                      │
│  ———— 路线 polyline ————        │
│                                  │
├──────────────────────────────────┤
│  【状态条】                       │
│  ● 已接单  司机赶来中  预计3分钟  │
├──────────────────────────────────┤
│  【司机信息卡片】                 │
│  ┌────────────────────────────┐  │
│  │ 👤 李师傅       ⭐ 4.9     │  │
│  │ 🚗 京A 8D231               │  │
│  │ 🎨 白色 大众朗逸           │  │
│  │ 📞 13812345678  [拨打]     │  │
│  └────────────────────────────┘  │
├──────────────────────────────────┤
│  【操作按钮】                     │
│  [取消订单]  [联系司机]           │
└──────────────────────────────────┘
```

### 5.2 状态流转

| 订单状态 | 显示文本 | 颜色 | 说明 |
|---------|---------|------|------|
| 0 | 派单中 | 橙色 | 刚下单，等待接单 |
| 1 | 待接单 | 蓝色 | 已确认，等待司机接单 |
| 2 | 已接单 - 司机赶来中 | 蓝色 | 收到 ORDER_ACCEPTED |
| 3 | 已到达 - 请上车 | 绿色 | 收到 DRIVER_ARRIVED |
| 4 | 行程中 | 紫色 | 用户确认后（暂未实现） |
| 5 | 已取消 | 灰色 | 订单已取消 |
| 6 | 已拒绝 | 红色 | 代叫车被拒绝 |

---

## 六、前端关键技术点

### 6.1 WebSocket 消息监听

```javascript
// 伪代码示例
const ws = new WebSocket(`ws://domain/ws/agent?token=${token}`);

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  switch (message.type) {
    case 'ORDER_ACCEPTED':
      handleOrderAccepted(message);
      break;
    case 'DRIVER_LOCATION':
      handleDriverLocation(message);
      break;
    case 'DRIVER_ARRIVED':
      handleDriverArrived(message);
      break;
    case 'ORDER_CREATED':
      handleOrderCreated(message);
      break;
    case 'PROXY_ORDER_CONFIRMED':
      handleProxyOrderConfirmed(message);
      break;
  }
};
```

### 6.2 小车平滑移动动画

**推荐方案**:
- **高德地图**: `marker.moveTo()` 或 CSS transition
- **百度地图**: `marker.setPosition()` + 自定义动画
- **腾讯地图**: `marker.setPosition()` + requestAnimationFrame

**关键**: 不要直接设置新坐标，要使用动画过渡（2-3秒）

### 6.3 路线绘制

```javascript
// 使用高德地图示例
const polyline = new AMap.Polyline({
  path: [
    [startLng, startLat],  // 注意：高德是 [lng, lat]
    [destLng, destLat]
  ],
  strokeColor: '#3366FF',
  strokeWeight: 5,
  strokeOpacity: 0.8
});
map.add(polyline);
```

### 6.4 倒计时计算

```javascript
// 根据 etaMinutes 更新倒计时
function updateCountdown(minutes) {
  if (minutes <= 0) {
    return '即将到达';
  }
  return `预计${minutes}分钟到达`;
}
```

---

## 七、异常处理

### 7.1 WebSocket 断线重连

```javascript
ws.onclose = () => {
  console.log('WebSocket断开，5秒后重连...');
  setTimeout(connectWebSocket, 5000);
};
```

### 7.2 超时处理

如果 10 秒内未收到 ORDER_ACCEPTED：
- 显示："派单中，请稍候..."
- 提供"刷新"按钮

### 7.3 错误提示

```javascript
if (message.success === false) {
  showToast(message.message || '操作失败');
}
```

---

## 八、测试用例

### 测试场景1：自己叫车
1. 调用 `/api/order/create`
2. 验证返回数据包含司机信息
3. 跳转到行程页
4. 等待 1-2 秒，收到 ORDER_ACCEPTED
5. 验证小车出现并开始移动
6. 每 3 秒收到 DRIVER_LOCATION，共 5 次
7. 最后收到 DRIVER_ARRIVED

### 测试场景2：亲友代叫（无需确认）
1. 亲友调用 `/api/guard/proxy-order` with `needConfirm=false`
2. 长辈收到 ORDER_CREATED
3. 跳转到行程页
4. 后续流程同场景1

### 测试场景3：亲友代叫（需确认）
1. 亲友调用 `/api/guard/proxy-order` with `needConfirm=true`
2. 长辈收到 ORDER_CREATED
3. 长辈调用确认接口
4. 收到 ORDER_ACCEPTED
5. 后续流程同场景1

### 测试场景4：刷新页面
1. 在行程页面刷新浏览器
2. 调用 `/api/order/{id}` 重新获取订单信息
3. 验证司机信息正确显示
4. WebSocket 重连后继续接收位置更新

---

## 九、注意事项

### ⚠️ 重要提醒

1. **坐标格式**: 
   - 后端返回的是 `{lat, lng}`
   - 高德地图 API 要求 `[lng, lat]`（注意顺序）
   - 务必转换！

2. **小车动画**:
   - 必须使用平滑动画，不能瞬移
   - 建议动画时长 2-3 秒
   - 与推送间隔（3秒）错开，避免冲突

3. **内存泄漏**:
   - 页面卸载时关闭 WebSocket
   - 清除定时器/动画

4. **性能优化**:
   - 只在行程页面监听 WebSocket
   - 离开页面后停止监听

5. **兼容性**:
   - iOS Safari 可能阻止自动播放音频
   - Android 某些浏览器 WebSocket 不稳定

6. **数据持久化**:
   - 司机信息已保存到数据库
   - 刷新页面后调用 `/api/order/{id}` 可重新获取
   - 无需本地缓存司机信息

---

## 十、联调检查清单

- [ ] 订单创建接口返回司机信息
- [ ] 订单详情接口返回司机信息
- [ ] 订单列表接口返回司机信息
- [ ] 当前订单接口返回司机信息
- [ ] WebSocket 连接成功
- [ ] 收到 ORDER_ACCEPTED 并显示司机卡片
- [ ] 地图绘制路线正确
- [ ] 小车图标出现在正确位置
- [ ] 收到 DRIVER_LOCATION 后小车平滑移动
- [ ] 倒计时文本正确更新
- [ ] 收到 DRIVER_ARRIVED 后显示到达提示
- [ ] WebSocket 断线能重连
- [ ] 页面刷新后司机信息不丢失
- [ ] 页面切换不崩溃

---

## 十一、接口汇总

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/order/create` | POST | 创建订单（自己叫车） |
| `/api/order/{id}` | GET | 查询订单详情 |
| `/api/order/list` | GET | 查询订单列表 |
| `/api/order/current` | GET | 查询当前进行中的订单 |
| `/api/order/{id}/cancel` | POST | 取消订单 |
| `/api/order/{id}/confirm` | POST | 确认订单 |
| `/api/guard/proxy-order` | POST | 亲友代叫车 |
| `/api/guard/confirm-proxy-order/{orderId}` | POST | 长辈确认代叫车 |
| `/api/guard/proxy-orders` | GET | 查询代叫车记录 |

---

## 十二、联系方式

如有问题，请联系后端开发团队。

**文档版本**: v2.0（完整版）  
**更新时间**: 2026-04-15  
**适用项目**: Anxin-Travel
