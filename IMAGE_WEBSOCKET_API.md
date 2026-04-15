# 图片识别 WebSocket API 对接文档

## 📋 概述

本文档说明智能出行助手的**图片识别功能**的 WebSocket 接口规范，包括单图识别、批量图片识别的请求格式和响应格式。

---

## 🔌 WebSocket 连接

### 连接地址
```
ws://your-domain/ws/agent?token={JWT_TOKEN}
```

### 认证方式
- URL 参数 `token`：JWT Token（必需）
- 认证成功后返回 `connected` 消息

---

## 📤 请求格式

### 1. 单张图片识别

```json
{
  "type": "image",
  "sessionId": "user_1",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "lat": 23.654891139742194,
  "lng": 116.67468406914627,
  "timestamp": 1776257614120,
  "content": "可选的文字说明"
}
```

**字段说明**：
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | ✅ | 固定值 `"image"` |
| sessionId | String | ✅ | 会话 ID（建议使用用户 ID） |
| imageBase64 | String | ✅ | Base64 编码的图片（包含 data:image/jpeg;base64, 前缀） |
| lat | Number | ❌ | 纬度（用于位置相关搜索） |
| lng | Number | ❌ | 经度（用于位置相关搜索） |
| timestamp | Number | ❌ | 时间戳 |
| content | String | ❌ | 可选的文字说明 |

---

### 2. 批量图片识别（最多 3 张）

```json
{
  "type": "image",
  "sessionId": "user_1",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "lat": 23.654891139742194,
  "lng": 116.67468406914627,
  "timestamp": 1776257614120,
  "content": "可选的文字说明",
  "additionalImages": [
    "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
    "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
  ],
  "imageCount": 3
}
```

**新增字段说明**：
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| additionalImages | Array\<String\> | ❌ | 额外图片数组（第 2、3 张） |
| imageCount | Number | ❌ | 图片总数（包括主图） |

**注意事项**：
- ✅ `imageBase64` 是第 1 张图片
- ✅ `additionalImages[0]` 是第 2 张图片
- ✅ `additionalImages[1]` 是第 3 张图片
- ⚠️ 最多支持 3 张图片（1 张主图 + 2 张额外图片）
- ⚠️ 后端会**一次性处理所有图片**，让 AI 理解图片间的关系

---

## 📥 响应格式

### 1. 连接成功响应

```json
{
  "type": "connected",
  "success": true,
  "message": "欢迎使用智能出行助手！",
  "timestamp": 1776258882068
}
```

---

### 2. 图片识别成功响应（非地址类图片）

```json
{
  "sessionId": "user_1",
  "timestamp": 1776259960000,
  "type": "image_result",
  "success": true,
  "message": "我看到了这两张图片展示了同一个动漫角色的不同场景。第一张图中角色拿着冰棍，第二张图是在沙漠中战斗...",
  
  "data": {
    "ocrText": "图片1的OCR文本\n\n--- 图片2 ---\n图片2的OCR文本",
    "imageDescription": "图片1的描述\n\n--- 图片2 ---\n图片2的描述",
    "message": "AI 回复内容",
    "imageCount": 2
  },
  
  "ocrText": "图片1的OCR文本\n\n--- 图片2 ---\n图片2的OCR文本"
}
```

**字段说明**：
| 字段 | 类型 | 说明 |
|------|------|------|
| type | String | 固定值 `"image_result"`（小写+下划线） |
| success | Boolean | 是否成功 |
| message | String | AI 生成的自然语言回复（综合理解所有图片） |
| data | Object | 详细数据对象 |
| data.ocrText | String | 合并后的 OCR 文本（多张图片用 `\n\n--- 图片N ---\n` 分隔） |
| data.imageDescription | String | 合并后的图片描述 |
| data.message | String | AI 回复内容（与顶层 message 相同） |
| data.imageCount | Number | **仅在批量模式下存在**，表示图片数量 |
| ocrText | String | **顶层字段**，与 data.ocrText 相同（方便前端访问） |

---

### 3. 图片识别成功响应（地址类图片）

当图片包含地址信息时，后端会自动搜索并返回 POI 列表：

```json
{
  "sessionId": "user_1",
  "timestamp": 1776259960000,
  "type": "image_result",
  "success": true,
  "message": "图片识别成功",
  
  "data": {
    "ocrText": "潮州市湘桥区太平路",
    "places": [
      {
        "id": "B000A7BD6I",
        "name": "太平路",
        "address": "潮州市湘桥区太平路",
        "lat": 23.665432,
        "lng": 116.651234,
        "distance": 1200
      }
    ]
  },
  
  "ocrText": "潮州市湘桥区太平路",
  "places": [
    {
      "id": "B000A7BD6I",
      "name": "太平路",
      "address": "潮州市湘桥区太平路",
      "lat": 23.665432,
      "lng": 116.651234,
      "distance": 1200
    }
  ]
}
```

**字段说明**：
| 字段 | 类型 | 说明 |
|------|------|------|
| data.places | Array | POI 地点列表（按匹配度排序） |
| places | Array | **顶层字段**，与 data.places 相同（方便前端访问） |

---

### 4. 图片识别失败响应

```json
{
  "sessionId": "user_1",
  "timestamp": 1776259960000,
  "type": "image_result",
  "success": false,
  "message": "图片识别失败：网络超时",
  
  "data": {
    "ocrText": "",
    "message": "识别失败：网络超时"
  }
}
```

---

## 🎯 关键注意事项

### 1. 消息类型大小写
- ⚠️ 后端返回的 `type` 字段是**小写+下划线**：`"image_result"`
- ❌ 不是大写：`"IMAGE_RESULT"` 或 `"IMAGE_RECOGNITION"`

### 2. 批量图片的上下文理解
- ✅ 后端会**一次性处理所有图片**，让 AI 理解图片间的关系
- ✅ AI 会生成综合回复，而不是简单拼接每张图片的结果
- 示例：
  ```
  前端发送 2 张动漫图片
  → AI 回复："我看到这两张图片展示了同一个角色的不同场景..."
  ```

### 3. OCR 文本格式
- 单张图片：直接返回 OCR 文本
- 多张图片：用分隔符区分
  ```
  图片1的OCR文本
  
  --- 图片2 ---
  图片2的OCR文本
  
  --- 图片3 ---
  图片3的OCR文本
  ```

### 4. 顶层字段冗余
为了前端访问方便，以下字段同时存在于 `data` 对象和顶层：
- `ocrText`
- `places`（如果存在）

**建议前端优先使用顶层字段**，兼容性更好。

### 5. imageCount 字段
- ✅ **仅在批量模式下存在**（imageCount >= 2）
- ❌ 单张图片识别时，`data` 中**不包含** `imageCount` 字段
- 前端判断逻辑：
  ```javascript
  if (response.data && response.data.imageCount) {
    console.log(`批量识别了 ${response.data.imageCount} 张图片`);
  } else {
    console.log('单张图片识别');
  }
  ```

### 6. 地址类 vs 非地址类图片
后端会自动判断图片类型：

**地址类图片**（包含地址关键词如"路"、"街"、"号"等）：
- 自动执行 POI 搜索
- 返回 `places` 数组
- `message` = "图片识别成功"

**非地址类图片**（风景、人物、商品等）：
- 调用 AI 生成自然语言描述
- 不返回 `places`
- `message` = AI 生成的详细描述

---

## 💡 前端解析示例

```javascript
// WebSocket 消息处理
ws.onmessage = (event) => {
  const response = JSON.parse(event.data);
  
  // 忽略连接确认消息
  if (response.type === 'connected') {
    console.log('WebSocket 连接成功');
    return;
  }
  
  // 处理图片识别结果
  if (response.type === 'image_result') {
    if (response.success) {
      // 成功
      const ocrText = response.ocrText || response.data?.ocrText || '';
      const places = response.places || response.data?.places || [];
      const aiMessage = response.message;
      const imageCount = response.data?.imageCount || 1;
      
      console.log(`识别了 ${imageCount} 张图片`);
      console.log('OCR 文本:', ocrText);
      console.log('AI 回复:', aiMessage);
      
      if (places.length > 0) {
        console.log('找到地点:', places);
        // 显示地点列表供用户选择
      } else {
        // 显示 AI 的自然语言回复
        showAIMessage(aiMessage);
      }
    } else {
      // 失败
      console.error('识别失败:', response.message);
      showError(response.message);
    }
  }
};
```

---

## 🧪 测试用例

### 测试 1：单张地址图片
```json
// 请求
{
  "type": "image",
  "sessionId": "test_user",
  "imageBase64": "data:image/jpeg;base64,...",
  "lat": 23.654891,
  "lng": 116.674684
}

// 期望响应
{
  "type": "image_result",
  "success": true,
  "message": "图片识别成功",
  "data": {
    "ocrText": "潮州市湘桥区太平路",
    "places": [...]
  },
  "places": [...]
}
```

### 测试 2：批量非地址图片（2张）
```json
// 请求
{
  "type": "image",
  "sessionId": "test_user",
  "imageBase64": "data:image/jpeg;base64,...",
  "additionalImages": ["data:image/jpeg;base64,..."],
  "imageCount": 2
}

// 期望响应
{
  "type": "image_result",
  "success": true,
  "message": "我看到了这两张图片...",
  "data": {
    "ocrText": "图片1的OCR\n\n--- 图片2 ---\n图片2的OCR",
    "imageDescription": "...",
    "message": "AI 回复",
    "imageCount": 2
  },
  "ocrText": "图片1的OCR\n\n--- 图片2 ---\n图片2的OCR"
}
```

### 测试 3：识别失败
```json
// 期望响应
{
  "type": "image_result",
  "success": false,
  "message": "图片识别失败：网络超时",
  "data": {
    "ocrText": "",
    "message": "识别失败：网络超时"
  }
}
```

---

## 📞 联系方式

如有问题，请联系后端开发团队。

**文档版本**：v1.0  
**最后更新**：2026-04-15
