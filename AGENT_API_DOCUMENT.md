# 智能体（Agent）API 完整接口文档

## 📋 目录
- [1. WebSocket 实时通信](#1-websocket-实时通信)
- [2. HTTP RESTful API](#2-http-restful-api)
- [3. 语音相关接口](#3-语音相关接口)
- [4. 核心功能详解](#4-核心功能详解)
- [5. 数据模型定义](#5-数据模型定义)
- [6. 注意事项与最佳实践](#6-注意事项与最佳实践)

---

## 基础信息

**服务器地址**: `http://10.241.75.80:8080` (局域网 IP，根据实际网络环境调整)

**认证方式**: 
- HTTP 请求：通过请求头 `X-User-Id` 传递用户 ID（或使用 JWT Token + UserContext）
- WebSocket：通过 URL 参数 `token` 传递 JWT Token

---

## 1. WebSocket 实时通信

### 1.1 连接建立

**WebSocket 地址**: `ws://10.241.75.80:8080/ws/agent?token={JWT_TOKEN}`

**连接流程**:
1. 前端生成 UUID 作为 `sessionId`（每个会话唯一）
2. 使用登录后的 JWT Token 建立 WebSocket 连接
3. 服务端验证 Token，成功后返回欢迎消息
4. 开始发送消息进行交互

**认证成功响应**:
```json
{
  "type": "connected",
  "success": true,
  "message": "欢迎使用智能出行助手！",
  "timestamp": 1713225600000
}
```

**认证失败响应**:
```json
{
  "type": "auth_failed",
  "success": false,
  "message": "认证已过期，请重新登录",
  "code": 401,
  "timestamp": 1713225600000
}
```

**重复连接拒绝**:
```json
{
  "type": "rejected",
  "success": false,
  "message": "检测到重复连接，请关闭其他窗口",
  "code": 409,
  "timestamp": 1713225600000
}
```

---

### 1.2 消息类型与格式

#### 1.2.1 文本消息（普通对话/搜索）

**请求格式**:
```json
{
  "type": "user_message",
  "sessionId": "uuid-generated-session-id",
  "content": "帮我找附近的医院",
  "lat": 23.653927,
  "lng": 116.677026,
  "dialectType": "mandarin"
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 是 | 消息类型，固定为 `"user_message"` |
| sessionId | String | 是 | 会话 ID，由前端生成 UUID |
| content | String | 是 | 用户输入的文本内容 |
| lat | Double | 否 | 用户当前纬度（不传则使用缓存或IP定位） |
| lng | Double | 否 | 用户当前经度 |
| dialectType | String | 否 | 方言类型，默认 `"mandarin"`（普通话），支持 `"cantonese"`（粤语）、`"sichuan"`（四川话）、`"henan"`（河南话）、`"hunan"`（湖南话）、`"minnan"`（闽南语）、`"shaanxi"`（陕西话）、`"dongbei"`（东北话）等 |

**方言翻译处理流程**:
1. 前端传递 `dialectType` 字段
2. 后端调用 `DialectTranslationService` 将方言翻译成普通话
3. AI 基于翻译后的普通话进行意图识别
4. 翻译失败时自动降级使用原文，不阻断流程

**示例**:
- 粤语："我想去北京路行下街" → 翻译后："我想去北京路逛街"
- 四川话："我要切春熙路耍哈儿" → 翻译后："我要去春熙路玩一会儿"

**响应格式 - 搜索结果（带引导信息）**:
```json
{
  "type": "search",
  "sessionId": "uuid-generated-session-id",
  "success": true,
  "message": "为你找到以下地点：\n\n1. 潮州市中心医院\n   距离：1.2 公里，预计费用：15 元\n\n2. 潮州市人民医院\n   距离：2.5 公里，预计费用：25 元\n\n您可以：\n• 回复'第一个'/'第二个'选择地点\n• 或直接说'帮我叫车'下单\n• 也可以说'换一个'重新搜索",
  "places": [
    {
      "id": "B000A8BDU6",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区新春路",
      "lat": 23.661234,
      "lng": 116.648765,
      "distance": 1250,
      "type": "医疗保健服务;综合医院",
      "duration": 300,
      "price": 15.5,
      "score": 0.95
    }
  ],
  "needConfirm": true,
  "timestamp": 1713225600000
}
```

**重要说明**: 
- 搜索结果返回后，系统会**等待用户确认**，不会自动下单
- 用户需要明确说"第一个"、"第二个"或直接说"下单"才会创建订单
- 这是**两次确认机制**，防止误操作

**响应格式 - 聊天回复**:
```json
{
  "type": "chat",
  "sessionId": "uuid-generated-session-id",
  "success": true,
  "message": "你好！我是小安，你的智能出行助手。我可以帮你查找地点、规划路线或叫车出行。有什么可以帮你的吗？",
  "timestamp": 1713225600000
}
```

**响应格式 - 订单创建成功**:
```json
{
  "type": "order",
  "sessionId": "uuid-generated-session-id",
  "success": true,
  "message": "订单创建成功",
  "data": {
    "id": 123,
    "orderNo": "ORD202604160001",
    "startAddress": "当前位置",
    "endAddress": "潮州市中心医院",
    "startLat": 23.653927,
    "startLng": 116.677026,
    "endLat": 23.661234,
    "endLng": 116.648765,
    "estimatedPrice": 15.5,
    "estimatedDuration": 300,
    "status": "PENDING"
  },
  "timestamp": 1713225600000
}
```

---

#### 1.2.2 图片识别消息

**请求格式**:
```json
{
  "type": "image",
  "sessionId": "uuid-generated-session-id",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "additionalImages": [
    "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
    "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
  ],
  "imageCount": 3,
  "lat": 23.653927,
  "lng": 116.677026
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 是 | 消息类型，固定为 `"image"` |
| sessionId | String | 是 | 会话 ID |
| imageBase64 | String | 是 | 主图片的 Base64 编码（包含 data:image/jpeg;base64, 前缀） |
| additionalImages | Array | 否 | 额外图片数组，用于批量识别 |
| imageCount | Integer | 否 | 图片总数 |
| lat | Double | 否 | 用户当前纬度 |
| lng | Double | 否 | 用户当前经度 |

**响应格式 - 图片识别成功（带 POI 列表）**:
```json
{
  "type": "image_result",
  "sessionId": "uuid-generated-session-id",
  "success": true,
  "message": "图片识别成功",
  "ocrText": "潮州市中心医院",
  "places": [
    {
      "id": "B000A8BDU6",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区新春路",
      "lat": 23.661234,
      "lng": 116.648765,
      "distance": 1250,
      "type": "医疗保健服务;综合医院",
      "duration": 300,
      "price": 15.5,
      "score": 0.98
    }
  ],
  "needConfirm": true,
  "timestamp": 1713225600000
}
```

**响应格式 - 图片识别成功（纯文本描述）**:
```json
{
  "type": "image_result",
  "sessionId": "uuid-generated-session-id",
  "success": true,
  "message": "我看到这张图片：这是一张医院的照片，门口有急诊标识和救护车",
  "data": {
    "ocrText": "潮州市中心医院\n联系电话：0768-12345678",
    "imageDescription": "这是一张医院的照片，门口有急诊标识和救护车",
    "message": "我看到这张图片：这是一张医院的照片，门口有急诊标识和救护车"
  },
  "timestamp": 1713225600000
}
```

**批量图片识别**:
- 支持最多 5 张图片同时上传
- AI 会理解多张图片的关系（如：第一张是名片，第二张是地图截图）
- 请求格式中通过 `additionalImages` 数组传递额外图片

---

#### 1.2.3 确认选择消息

**请求格式**:
```json
{
  "type": "confirm",
  "sessionId": "uuid-generated-session-id",
  "content": "潮州市中心医院",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 是 | 消息类型，固定为 `"confirm"` |
| sessionId | String | 是 | 会话 ID |
| content | String | 是 | 用户选择的 POI 名称（必须与之前返回的 places 中的 name 完全一致） |
| lat | Double | 是 | 用户当前纬度 |
| lng | Double | 是 | 用户当前经度 |

**响应格式 - 确认选择成功（返回路线信息，等待用户下单）**:
```json
{
  "type": "order",
  "sessionId": "uuid-generated-session-id",
  "success": true,
  "message": "已选择 潮州市中心医院，地址：广东省潮州市湘桥区新春路\n距离您 1.2 公里，预计费用 15 元\n\n需要帮您叫车吗？请回复'下单'或'叫车'",
  "data": {
    "selectedPoi": {
      "id": "B000A8BDU6",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区新春路",
      "lat": 23.661234,
      "lng": 116.648765,
      "distance": 1250,
      "price": 15.5
    },
    "message": "已选择 潮州市中心医院，地址：广东省潮州市湘桥区新春路\n距离您 1.2 公里，预计费用 15 元\n\n需要帮您叫车吗？请回复'下单'或'叫车'"
  },
  "timestamp": 1713225600000
}
```

**重要说明**: 
- 确认后**不会立即创建订单**，而是返回路线信息和确认提示
- 用户需要再次明确说"下单"或"叫车"才会真正创建订单
- 这是为了防止误操作的**两次确认机制**

**响应格式 - 确认失败**:
```json
{
  "type": "error",
  "sessionId": "uuid-generated-session-id",
  "success": false,
  "message": "未找到该地点，请重新选择",
  "timestamp": 1713225600000
}
```

---

#### 1.2.4 心跳消息

**请求格式**:
```json
{
  "type": "ping",
  "sessionId": "uuid-generated-session-id",
  "timestamp": 1713225600000
}
```

**响应格式**:
```json
{
  "type": "pong",
  "sessionId": "uuid-generated-session-id",
  "timestamp": 1713225600000
}
```

**注意**: 建议每 30 秒发送一次心跳，保持连接活跃。

---

#### 1.2.5 错误响应

**通用错误格式**:
```json
{
  "type": "error",
  "success": false,
  "message": "具体错误信息",
  "code": 500,
  "sessionId": "uuid-generated-session-id",
  "timestamp": 1713225600000
}
```

---

## 2. HTTP RESTful API

### 2.1 智能搜索接口

**接口地址**: `POST /api/agent/search`

**请求头**:
```
Content-Type: application/json
X-User-Id: 123
```

**请求体**:
```json
{
  "sessionId": "uuid-generated-session-id",
  "keyword": "医院",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话 ID |
| keyword | String | 是 | 搜索关键词 |
| lat | Double | 否 | 用户纬度（不传则使用 IP 定位） |
| lng | Double | 否 | 用户经度 |

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "SEARCH",
    "success": true,
    "message": "为您找到以下医院",
    "places": [
      {
        "id": "B000A8BDU6",
        "name": "潮州市中心医院",
        "address": "广东省潮州市湘桥区新春路",
        "lat": 23.661234,
        "lng": 116.648765,
        "distance": 1250,
        "type": "医疗保健服务;综合医院",
        "duration": 300,
        "price": 15.5,
        "score": 0.95
      }
    ]
  }
}
```

**失败响应**:
```json
{
  "code": 500,
  "message": "搜索失败：具体错误信息",
  "data": null
}
```

---

### 2.2 确认选择接口

**接口地址**: `POST /api/agent/confirm`

**请求头**:
```
Content-Type: application/json
X-User-Id: 123
```

**请求体**:
```json
{
  "sessionId": "uuid-generated-session-id",
  "selectedPoiName": "潮州市中心医院",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话 ID |
| selectedPoiName | String | 是 | 选中的 POI 名称 |
| lat | Double | 是 | 用户纬度 |
| lng | Double | 是 | 用户经度 |

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "ORDER",
    "message": "已确认目的地，正在创建订单",
    "poi": {
      "id": "B000A8BDU6",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区新春路",
      "lat": 23.661234,
      "lng": 116.648765
    },
    "route": {
      "mode": "driving",
      "duration": 300,
      "distance": 2500,
      "price": 15.5
    }
  }
}
```

**失败响应 - 参数校验错误**:
```json
{
  "code": 400,
  "message": "会话已过期",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "POI 名称不能为空",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "位置信息缺失",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "经纬度坐标超出有效范围",
  "data": null
}
```

**失败响应 - 业务错误**:
```json
{
  "code": 500,
  "message": "处理失败：未找到该地点，请重新选择",
  "data": null
}
```

---

### 2.3 图片识别接口

**接口地址**: `POST /api/agent/image`

**请求头**:
```
Content-Type: application/json
X-User-Id: 123
```

**请求体**:
```json
{
  "sessionId": "uuid-generated-session-id",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话 ID |
| imageBase64 | String | 是 | 图片 Base64 编码（必须包含 data:image/jpeg;base64, 前缀） |
| lat | Double | 否 | 用户纬度 |
| lng | Double | 否 | 用户经度 |

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "IMAGE_RESULT",
    "success": true,
    "message": "识别到地址：潮州市中心医院",
    "data": {
      "ocrText": "潮州市中心医院",
      "places": [...]
    }
  }
}
```

**失败响应 - 参数错误**:
```json
{
  "code": 400,
  "message": "图片格式不支持，仅支持 JPG/PNG 格式",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "图片大小超过限制（最大 5MB）",
  "data": null
}
```

**失败响应 - 识别失败**:
```json
{
  "code": 422,
  "message": "未识别到有效文字信息",
  "data": null
}
```

**失败响应 - 服务异常**:
```json
{
  "code": 503,
  "message": "图片识别服务暂时不可用，请稍后重试",
  "data": null
}
```

---

### 2.4 清理会话接口

**接口地址**: `POST /api/agent/cleanup`

**请求头**:
```
Content-Type: application/json
```

**请求体**:
```json
{
  "sessionId": "uuid-generated-session-id"
}
```

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 2.5 更新用户位置接口

**接口地址**: `POST /api/agent/location`

**请求头**:
```
Content-Type: application/json
```

**请求体**:
```json
{
  "sessionId": "uuid-generated-session-id",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 3. 语音相关接口

**重要说明**: 语音接口属于订单聊天模块（`OrderChatController`），不属于智能体模块。

### 3.1 语音转文字

**接口地址**: `POST /api/chat/voiceToText`

**请求头**:
```
Content-Type: application/json
Authorization: Bearer {JWT_TOKEN}  // 或使用 UserContext
```

**请求体**: 直接传递音频 Base64 字符串（不包含 JSON 对象包装）
```
"data:audio/wav;base64,UklGRi..."
```

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "text": "帮我找附近的医院",
    "audioUrl": "https://example.com/audio.mp3"
  }
}
```

---

### 3.2 文字转语音

**接口地址**: `GET /api/chat/textToSpeech?text=你好`

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | String | 是 | 要转换的文字 |

**成功响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "audioUrl": "https://example.com/tts/output.mp3",
    "text": "你好"
  }
}
```

---

## 4. 核心功能详解

### 4.1 意图识别系统

#### 4.1.1 AI 意图识别流程

```
用户输入 → 通义千问 AI 解析 → AgentIntent 对象 → 类型判断 → 执行对应逻辑
```

**支持的意图类型**:
| 类型 | 说明 | 示例 |
|------|------|------|
| SEARCH | 搜索地点 | "帮我找附近的医院" |
| ORDER | 直接下单 | "帮我叫车去北京大学" |
| CONFIRM | 确认选择 | "第一个" / "就这个" |
| CHAT | 普通对话 | "你好" / "今天天气怎么样" |

#### 4.1.2 Fallback 机制（AI 失败时的兜底）

如果 AI 解析失败，系统会自动使用本地关键词匹配：

**关键词匹配规则**:
- "医院" → SEARCH 意图
- "酒店"/"宾馆" → SEARCH 意图
- "餐"/"吃"/"饭店" → SEARCH 意图
- "超市"/"便利店" → SEARCH 意图
- "银行"/"atm" → SEARCH 意图
- "药店"/"药房" → SEARCH 意图
- "加油站"/"加油" → SEARCH 意图
- "商场"/"购物中心" → SEARCH 意图
- "地铁"/"火车站"/"高铁站" → SEARCH 意图
- 包含"去"字 → 提取目的地后 SEARCH

#### 4.1.3 知名地标强制识别

以下 50+ 知名地标会被强制识别为 SEARCH 意图，即使 AI 误判为 CHAT：

**著名景点**: 故宫、长城、天安门、东方明珠、西湖、黄山、九寨沟、兵马俑、布达拉宫、鼓浪屿、张家界、泰山、庐山、峨眉山、颐和园、天坛、鸟巢、水立方、外滩

**知名大学**: 北京大学、清华大学、复旦大学、上海交通大学、浙江大学、南京大学、中国人民大学、中国科学技术大学、武汉大学、华中科技大学、中山大学

**防污染校验**: 
- 去除口语杂质（"我想去"、"请问"、"怎么走"等）
- 检查关键词长度（超过 30 字符视为整句话）
- 验证关键词是否在原句中出现

---

### 4.2 搜索策略引擎（腾讯地图优先）

#### 4.2.1 搜索流程

```
关键词清洗 → 纠偏映射 → 腾讯地图搜索 → (结果不足)高德地图补充 → 过滤黑名单 → 智能排序 → 并行路线计算
```

#### 4.2.2 关键词处理

**1. 清洗**: 
- 去除括号内容
- 拼音缩写展开（hsfsxy → 韩山师范学院）

**2. 纠偏映射**（用户习惯称呼 → 标准名称）:
```java
老校区 → 韩山师范学院东区
新校区 → 韩山师范学院西区
本部 → 韩山师范学院
大裤衩 → CCTV 总部大楼
鸟蛋 → 国家大剧院
万达 → 万达广场
万象城 → 万象天地
太古里 → 三里屯太古里
北京站 → 北京火车站
上海站 → 上海火车站
广州南 → 广州南站
```

**3. 分类**:
- ADDRESS（地址类）
- CATEGORY（类别类）
- LANDMARK（地标类）
- FUZZY（模糊类）

#### 4.2.3 智能排序算法

```
最终得分 = (名称匹配度 × 权重 + 类型匹配度 × 权重 + 距离分数 × 权重) × 同城加成系数
```

**权重配置**:
- 知名地标：名称权重 0.8，类型权重 0.15，距离权重 0.05
- 普通地点：名称权重 0.5，类型权重 0.3，距离权重 0.2
- 同城加成：距离 < 100km 时，得分 × 1.2

#### 4.2.4 多地图策略

**腾讯地图优先**:
1. 先调用腾讯地图 API
2. 如果找到 ≥ 3 个结果，直接返回
3. 结果不足 3 个时，调用高德地图补充
4. 周边搜索无结果时，自动切换到全国搜索

**去重机制**:
- 使用 `name + lat + lng` 作为唯一标识
- 避免同一地点重复出现

**性能优化**:
- 腾讯地图结果充足时，跳过高德搜索（减少 API 调用）
- 并行路线计算（使用自定义线程池）
- 超时控制：10 秒

---

### 4.3 图片识别功能

#### 4.3.1 处理流程

```
接收 Base64 图片 → OCR 提取文字 → 图片内容描述 → 判断是否为地址类 → 执行搜索或对话回复
```

#### 4.3.2 地址类图片判断条件

满足以下任一条件即判定为地址类图片：

**1. OCR 文字包含地址关键词**:
- 路、街、号、市、区、省、县、镇、村、乡、道、巷

**2. AI 意图识别为 SEARCH 类型**:
- 知名地标（故宫、北京大学等）
- POI 名称（医院、餐厅、酒店等）

#### 4.3.3 批量图片识别

**支持最多 5 张图片同时上传**:
- AI 会理解多张图片的关系（如：第一张是名片，第二张是地图截图）
- 合并所有图片的 OCR 文字进行综合判断
- 拼接格式：
  ```
  --- 图片1 ---
  [OCR文字]
  
  --- 图片2 ---
  [OCR文字]
  ```

#### 4.3.4 图片规格要求

- **格式**: JPEG、PNG、BMP、WEBP
- **大小**: 单张不超过 10MB（建议压缩到 3MB 以内）
- **Base64 格式**: 必须包含 `data:image/jpeg;base64,` 前缀

#### 4.3.5 图片匹配分数算法

用于对搜索结果按 OCR 文字重新排序：

| 匹配情况 | 分数 |
|---------|------|
| 名称完全等于 OCR 文字 | 1000 |
| 名称包含 OCR 文字 | 800 |
| OCR 文字包含名称 | 700 |
| 地址包含 OCR 文字 | 600 |
| OCR 文字包含地址关键字 | 500 |
| 名称和地址都包含核心词 | 400 |
| 仅名称包含核心词 | 300 |
| 仅地址包含核心词 | 200 |
| 完全不匹配 | 0 |

---

### 4.4 下单功能

#### 4.4.1 两次确认机制

**完整流程**:
```
用户搜索 → 返回 POI 列表 → 用户说"第一个" → 返回确认信息 → 用户说"下单" → 创建订单
```

**第一次确认**（选择地点）:
- 用户说："第一个" 或 "潮州市中心医院"
- 系统返回：路线信息 + "需要帮您叫车吗？请回复'下单'或'叫车'"
- **此时不会创建订单**

**第二次确认**（明确下单）:
- 用户说："下单" 或 "叫车"
- 系统调用 `orderService.createOrder()` 创建订单
- 返回订单详情

**设计目的**: 防止误操作，给用户反悔机会

#### 4.4.2 HTTP 接口直接确认

也可以通过 HTTP 接口完成确认（仍需两次调用）:

**第一步：确认选择**
```javascript
POST /api/agent/confirm
{
  "sessionId": "xxx",
  "selectedPoiName": "潮州市中心医院",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**第二步：创建订单**
```javascript
POST /api/order/create
{
  "destLat": 23.661234,
  "destLng": 116.648765,
  "destName": "广东省潮州市湘桥区新春路"
}
```

#### 4.4.3 订单创建参数

- **userId**: 从请求头 `X-User-Id` 或 UserContext 获取
- **destLat/destLng**: 目的地坐标
- **destName**: 目的地名称（优先使用完整地址）

**响应数据**:
```json
{
  "id": 123,
  "orderNo": "ORD202604160001",
  "startAddress": "当前位置",
  "endAddress": "潮州市中心医院",
  "startLat": 23.653927,
  "startLng": 116.677026,
  "endLat": 23.661234,
  "endLng": 116.648765,
  "estimatedPrice": 15.5,
  "estimatedDuration": 300,
  "status": "PENDING"
}
```

---

### 4.5 方言翻译功能

#### 4.5.1 支持的方言类型

| 方言代码 | 方言名称 | 示例 |
|---------|---------|------|
| mandarin | 普通话（默认） | - |
| cantonese | 粤语 | "我想去北京路行下街" |
| sichuan | 四川话 | "我要切春熙路耍哈儿" |
| henan | 河南话 | - |
| hunan | 湖南话 | - |
| minnan | 闽南语 | - |
| shaanxi | 陕西话 | - |
| dongbei | 东北话 | - |

#### 4.5.2 翻译流程

```
方言文本 → 通义千问翻译 → 标准普通话 → AI 意图识别 → 返回结果
```

#### 4.5.3 翻译原则

1. 保持原意不变，仅转换表达方式
2. 地名、人名、专有名词保持不变
3. 去除方言特有的语气词和口头禅
4. **翻译失败时返回原文，不阻断流程**

#### 4.5.4 使用方式

在 WebSocket 消息中传递 `dialectType` 字段：
```json
{
  "type": "user_message",
  "sessionId": "xxx",
  "content": "我想去北京路行下街",
  "dialectType": "cantonese",
  "lat": 23.653927,
  "lng": 116.677026
}
```

---

### 4.6 位置查询功能

#### 4.6.1 触发关键词

检测到以下关键词时，自动触发位置查询：
- "我在哪里"
- "我现在在哪里"
- "我的位置"
- "我现在在哪"
- "位置信息"
- "当前定位"

#### 4.6.2 处理流程

```
检测位置查询 → 从缓存获取坐标 → 逆地理编码（高德 API） → 返回友好地址
```

#### 4.6.3 响应示例

```
您现在位于广东省潮州市湘桥区新春路附近。需要我帮您查找周边的医院、餐厅或其他地点吗？😊
```

---

### 4.7 AI 自然对话功能

#### 4.7.1 System Prompt 设计

**角色定义**: 智能出行助手"小安"

**核心能力**:
1. **出行服务**：查找地点、规划路线、预估价格、叫车下单
2. **日常聊天**：进行自然对话，分享观点和建议
3. **知识问答**：回答常见问题，但要注意事实准确性

**对话风格**:
- 保持友好、自然、亲切的语气，像朋友一样交流
- 回复要生动有趣，避免机械化的固定话术
- 根据用户情绪调整语气（开心时活泼，难过时安慰）
- 适当使用表情符号增加亲和力（如 😊 👍 🚗）
- 回复长度灵活控制，简单问题简短回答，复杂问题详细说明
- **不要过度冗长**，保持简洁但有温度

**重要原则 - 事实准确性**:
- **严禁编造不确定的信息**！如果不知道确切答案，诚实说明
- **对于实时数据**（如天气、新闻、股价等），由于无法访问实时信息，采取以下策略：
  * 天气问题：提供季节性建议和出行提示
  * 新闻问题：礼貌说明无法获取最新新闻，但可以聊聊相关话题
  * 其他实时数据：诚实说明限制，但尽量提供有用的替代建议
- 对于常识性问题（如历史、科学、文化），可以基于训练数据自信回答

#### 4.7.2 多轮对话上下文

**实现方式**:
- 保存最近 10 条历史消息
- System Prompt + History + Current Message 一起发送给 AI
- 支持上下文理解（如："第二个"、"换一个"、"远不远"）

**对话示例**:
```
用户：你好
AI：你好呀！😊 我是小安，很高兴认识你！今天有什么我可以帮你的吗？

用户：今天天气怎么样
AI：现在是春季，天气通常比较舒适温暖呢～ ☀️ 不过具体预报可能会有变化，建议你查看天气APP获取最准确的信息。如果要出门游玩或办事，我可以帮你规划路线或者叫车哦！

用户：我想去北京大学
AI：好的！我来帮你找一下北京大学的地点。🚗
```

---

### 4.8 MemoryService 记忆服务

#### 4.8.1 存储内容

1. **会话消息**: 最多 30 条（15 轮对话），用于多轮上下文理解
2. **用户位置**: lat/lng 坐标，用于路线计算和逆地理编码
3. **候选 POI**: Redis 缓存，30 分钟过期，最多保存 10 个地点
4. **待确认订单**: Redis 缓存，包含 selectedPoi 和确认消息

#### 4.8.2 Redis Key 命名规范

| Key 格式 | 说明 | 过期时间 |
|---------|------|----------|
| `agent:poi:{sessionId}` | 候选 POI 列表 | 30 分钟 |
| `agent:pending_order:{sessionId}` | 待确认订单信息 | 30 分钟 |

#### 4.8.3 内存结构

```java
ConcurrentHashMap<String, List<ChatMessage>> sessionMessages;  // 会话消息
ConcurrentHashMap<String, double[]> sessionLocations;          // 用户位置
```

#### 4.8.4 生命周期管理

- **会话超时**: 30 分钟（Redis 缓存自动过期）
- **消息数量限制**: 每个会话最多保存 30 条消息（15 轮对话）
- **主动清理**: 调用 `/api/agent/cleanup` 可提前清理
- **WebSocket 断开**: 自动清理会话数据

---

## 5. 数据模型定义

### 5.1 PoiDTO（兴趣点）

```json
{
  "id": "B000A8BDU6",
  "name": "潮州市中心医院",
  "address": "广东省潮州市湘桥区新春路",
  "lat": 23.661234,
  "lng": 116.648765,
  "distance": 1250,
  "type": "医疗保健服务;综合医院",
  "duration": 300,
  "price": 15.5,
  "score": 0.95
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | POI 唯一标识（高德地图 ID） |
| name | String | 地点名称 |
| address | String | 详细地址 |
| lat | Double | 纬度 |
| lng | Double | 经度 |
| distance | Double | 距离（米） |
| type | String | POI 类型（高德分类） |
| duration | Integer | 预计耗时（秒） |
| price | Double | 预估价格（元） |
| score | Double | 相关性评分（0-1，用于排序） |

---

### 5.2 RouteResult（路线结果）

```json
{
  "mode": "driving",
  "duration": 300,
  "distance": 2500,
  "price": 15.5
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| mode | String | 出行方式（driving/transit/walking） |
| duration | Integer | 预计耗时（秒） |
| distance | Double | 距离（米） |
| price | Double | 预估价格（元） |

---

### 5.3 AgentResponse（智能体统一响应）

```json
{
  "type": "SEARCH",
  "success": true,
  "message": "为您找到以下医院",
  "places": [...],
  "route": {...},
  "data": {...},
  "error": null
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| type | String | 响应类型（SEARCH/CHAT/ROUTE/ORDER/IMAGE_RESULT） |
| success | Boolean | 是否成功 |
| message | String | 回复消息 |
| places | Array | POI 列表（搜索时使用） |
| route | Object | 路线信息（路线规划时使用） |
| data | Object | 附加数据（订单/图片识别结果等） |
| error | String | 错误信息（失败时） |

---

### 5.4 AgentState（智能体状态枚举）

| 状态值 | 说明 | 触发条件 |
|--------|------|----------|
| INIT | 初始状态 | 用户首次发送消息 |
| INTENT_RECOGNIZED | 已识别叫车意图 | AI 解析出 SEARCH/ORDER 意图 |
| DEST_PARSED | 已解析目的地（多个候选） | 搜索完成，返回 POI 列表 |
| ROUTE_READY | 路线已就绪，等待确认 | 所有 POI 的路线计算完成 |
| WAIT_CONFIRM | 等待用户确认 | 显示搜索结果，等待用户选择 |
| ORDER_CREATED | 订单已创建 | 用户确认并下单成功 |
| IMAGE_RECOGNIZED | 图片识别成功 | OCR 提取文字完成 |
| ERROR | 异常状态 | 处理过程中发生错误 |

**状态流转图**:
```
INIT → INTENT_RECOGNIZED → DEST_PARSED → ROUTE_READY → WAIT_CONFIRM → ORDER_CREATED
                                              ↓
                                          (用户说"换一个")
                                              ↓
                                         重新搜索
```

---

### 5.5 SearchStrategyEngine（搜索策略引擎）

**核心逻辑**:
1. **腾讯地图优先**: 先调用腾讯地图 API，如果找到 >= 3 个结果直接返回
2. **高德地图补充**: 腾讯结果不足 3 个时，调用高德地图补充
3. **全国搜索降级**: 周边搜索无结果时，自动切换到全国搜索
4. **知名地标坐标缓存**: 50+ 知名地标预存坐标，搜索失败时使用

**去重机制**:
- 使用 `name + lat + lng` 作为唯一标识
- 避免同一地点重复出现

**性能优化**:
- 腾讯地图结果充足时，跳过高德搜索（减少 API 调用）
- 并行路线计算（使用自定义线程池）
- 超时控制：10 秒

---

### 5.6 ImageRecognitionService（图片识别服务）

**OCR 提取文字**:
- API: 通义千问 qwen-vl-plus
- 提示词：“请提取图片中的地址信息、地点名称。如果是聊天截图，请提取对话中提到的地址。只返回地址文字，不要其他内容。”
- 错误处理：API 失败时抛出 RuntimeException，前端捕获后提示重试

**图片内容描述**:
- API: 通义千问 qwen-vl-plus
- 提示词：“请简要描述这张图片的内容...控制在50字以内，只描述看到的内容。”
- 降级策略：失败时返回 "一张图片"，不阻断流程

**批量处理**:
- 循环处理每张图片，合并 OCR 文字和图片描述
- AI 会理解多张图片的关系（如：第一张是名片，第二张是地图）

---

## 6. 注意事项与最佳实践

### 6.1 SessionId 管理

1. **生成规则**: 使用 UUID v4 生成，例如：`550e8400-e29b-41d4-a716-446655440000`
2. **生命周期**: 
   - 每次打开应用或刷新页面时生成新的 sessionId
   - 会话超时时间：30 分钟（Redis 缓存自动过期）
   - 主动调用 `/api/agent/cleanup` 可提前清理
3. **用途**: 
   - 关联多轮对话上下文
   - 存储候选 POI 列表（最多保存 10 个）
   - 缓存用户位置信息
   - 保存待确认订单信息
4. **内存限制**: 每个会话最多保存 30 条消息（15 轮对话），超出后自动移除最早的消息

---

### 6.2 坐标系统

1. **坐标系**: GCJ-02（火星坐标系，高德/腾讯地图使用）
2. **格式要求**:
   - 纬度（lat）：-90 ~ 90
   - 经度（lng）：-180 ~ 180
3. **精度**: 建议保留 6 位小数（约 0.1 米精度）

**示例**:
```json
{
  "lat": 23.653927,
  "lng": 116.677026
}
```

---

### 6.3 图片上传规范

1. **格式支持**: JPG、PNG、BMP、WEBP
2. **大小限制**: 单张图片不超过 10MB（建议压缩到 3MB 以内）
3. **Base64 格式**: 必须包含前缀
   - ✅ 正确：`data:image/jpeg;base64,/9j/4AAQSkZJRg...`
   - ❌ 错误：`/9j/4AAQSkZJRg...`（缺少前缀）
4. **批量识别**: 最多支持 5 张图片同时识别

---

### 6.4 错误码说明

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 200 | 成功 | - |
| 400 | 参数校验失败 | 检查请求参数是否符合规范 |
| 401 | 认证失败 | Token 过期，需重新登录 |
| 409 | 冲突（重复连接） | 关闭其他 WebSocket 连接 |
| 422 | 识别结果为空 | 提示用户更换图片或重新输入 |
| 500 | 服务器内部错误 | 记录日志，联系后端排查 |
| 503 | 服务不可用 | 稍后重试 |

---

### 6.5 性能优化建议

1. **WebSocket 心跳**: 每 30 秒发送一次 ping，避免连接断开
2. **位置更新**: 用户移动超过 100 米时，主动调用 `/api/agent/location` 更新位置
3. **图片压缩**: 前端上传前压缩图片，建议分辨率不超过 1920x1080
4. **会话复用**: 同一用户短时间内多次搜索，可复用 sessionId
5. **POI 选择**: 用户点击 POI 后，立即调用 confirm 接口，避免缓存过期

---

### 6.6 安全注意事项

1. **Token 保护**: WebSocket 连接时，Token 通过 URL 参数传递，建议使用 WSS（加密 WebSocket）
2. **用户 ID 验证**: 所有 HTTP 请求必须携带 `X-User-Id` 请求头或使用 JWT Token
3. **防重放攻击**: sessionId 应使用时间戳 + 随机数生成，避免被猜测
4. **输入校验**: 后端已对所有输入进行严格校验，前端无需重复校验但可做用户体验优化

---

### 6.7 常见问题

**Q1: WebSocket 连接后立即断开？**
- A: 检查 Token 是否有效，确认服务器地址和端口是否正确

**Q2: 搜索结果为空？**
- A: 检查 lat/lng 是否传递，关键词是否过于模糊

**Q3: 确认选择时提示“未找到该地点”？**
- A: 确保 `selectedPoiName` 与之前返回的 `places[].name` 完全一致（包括空格和标点）

**Q4: 图片识别返回“未识别到有效文字”？**
- A: 检查图片清晰度，确保文字清晰可见，尝试更换图片

**Q5: 多轮对话上下文丢失？**
- A: 确保每次请求使用相同的 sessionId，且间隔不超过 30 分钟

**Q6: 方言翻译失败怎么办？**
- A: 系统会自动降级使用原文，不会阻断流程，可以检查方言代码是否正确

**Q7: 为什么确认后没有创建订单？**
- A: 系统设计为两次确认机制，确认后需要再次明确说“下单”或“叫车”才会创建订单

---

## 7. 完整交互流程示例

### 场景：用户通过文本搜索医院并下单

**步骤 1**: 建立 WebSocket 连接
```
ws://10.241.75.80:8080/ws/agent?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**步骤 2**: 发送搜索请求
```json
{
  "type": "user_message",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "帮我找附近的医院",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**步骤 3**: 接收搜索结果
```json
{
  "type": "search",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "message": "为你找到以下地点：\n\n1. 潮州市中心医院\n   距离：1.2 公里，预计费用：15 元\n\n您可以：\n• 回复'第一个'/'第二个'选择地点\n• 或直接说'帮我叫车'下单",
  "places": [
    {
      "id": "B000A8BDU6",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区新春路",
      "lat": 23.661234,
      "lng": 116.648765,
      "distance": 1250,
      "type": "医疗保健服务;综合医院",
      "duration": 300,
      "price": 15.5,
      "score": 0.95
    }
  ],
  "needConfirm": true,
  "timestamp": 1713225600000
}
```

**步骤 4**: 用户选择第一个医院，发送确认
```json
{
  "type": "confirm",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "潮州市中心医院",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**步骤 5**: 接收确认响应（等待用户下单）
```json
{
  "type": "order",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "message": "已选择 潮州市中心医院，地址：广东省潮州市湘桥区新春路\n距离您 1.2 公里，预计费用 15 元\n\n需要帮您叫车吗？请回复'下单'或'叫车'",
  "data": {
    "selectedPoi": {
      "id": "B000A8BDU6",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区新春路",
      "lat": 23.661234,
      "lng": 116.648765,
      "distance": 1250,
      "price": 15.5
    },
    "message": "已选择 潮州市中心医院，地址：广东省潮州市湘桥区新春路\n距离您 1.2 公里，预计费用 15 元\n\n需要帮您叫车吗？请回复'下单'或'叫车'"
  },
  "timestamp": 1713225600000
}
```

**步骤 6**: 用户明确说"下单"
```json
{
  "type": "user_message",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "下单",
  "lat": 23.653927,
  "lng": 116.677026
}
```

**步骤 7**: 接收订单创建结果
```json
{
  "type": "order",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "message": "订单创建成功",
  "data": {
    "id": 123,
    "orderNo": "ORD202604160001",
    "startAddress": "当前位置",
    "endAddress": "潮州市中心医院",
    "startLat": 23.653927,
    "startLng": 116.677026,
    "endLat": 23.661234,
    "endLng": 116.648765,
    "estimatedPrice": 15.5,
    "estimatedDuration": 300,
    "status": "PENDING"
  },
  "timestamp": 1713225600000
}
```

---

## 8. 技术栈说明

- **后端框架**: Spring Boot 3.x
- **WebSocket**: Jakarta WebSocket API (原生实现)
- **AI 引擎**: 通义千问（qwen-turbo / qwen-vl-plus）
- **地图服务**: 高德地图 Web API + 腾讯地图 Web API
- **图片识别**: 通义千问视觉模型 (OCR + 图像描述)
- **数据库**: MySQL 8.0 + Redis 7.x
- **序列化**: FastJSON 2.x
- **线程池**: 自定义业务线程池（并行路线计算）

---

## 9. 联系方式

如有问题，请联系后端开发团队。

**文档版本**: v2.0  
**最后更新**: 2026-04-16  
**更新说明**: 
- 补充完整的智能体核心功能详解
- 修正下单流程为两次确认机制
- 添加方言翻译、位置查询、AI对话等详细说明
- 完善图片识别批量处理逻辑
- 修正语音接口归属说明
- 删除重复内容，优化文档结构
