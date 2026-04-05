# 图片识别接口完整 API 文档

> **更新日期**: 2026-04-05  
> **版本**: v2.0 (修复数据传递问题)  
> **状态**: ✅ 生产就绪

---

## 📋 概述

本文档详细说明**图片识别功能**的前后端交互接口，包括：
- ✅ HTTP REST 接口（备选方案）
- ✅ WebSocket 实时交互（⭐ 推荐）
- ✅ 完整的请求/响应字段说明
- ✅ 前端调用示例（Android Kotlin + Web JavaScript）

---

## 🔌 方式 1：WebSocket 实时交互（⭐ 推荐）

### 1.1 连接地址

```
ws://192.168.1.106:8080/ws/agent?token=YOUR_TOKEN
```

**参数说明**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| token | String | ✅ | JWT Token（从登录接口获取） |

### 1.2 发送图片消息

#### 请求格式

```json
{
  "type": "image",
  "sessionId": "unique-session-id-123",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "lat": 23.6533,
  "lng": 116.6772
}
```

#### 请求参数说明

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| type | String | ✅ | 固定为 `"image"` |
| sessionId | String | ✅ | 会话 ID（UUID 格式，建议前端生成后复用） |
| imageBase64 | String | ✅ | Base64 编码的图片数据 |
| lat | Number | ✅ | 用户当前位置纬度 |
| lng | Number | ✅ | 用户当前位置经度 |

#### 图片格式要求

- ✅ **支持格式**: JPEG, PNG, BMP, WEBP
- ✅ **最大大小**: 10 MB（建议压缩到 500KB - 1MB）
- ⚠️ **建议分辨率**: 1920x1080 以内
- ✅ **Base64 格式**: 
  - 带前缀：`data:image/jpeg;base64,...`
  - 不带前缀：`/9j/4AAQ...`（系统自动添加）

---

### 1.3 响应格式

#### ✅ 成功响应（识别到地址并搜索到地点）

```json
{
  "sessionId": "unique-session-id-123",
  "timestamp": 1775280220155,
  "type": "image_recognition",
  "success": true,
  "message": "图片识别成功",
  "ocrText": "潮州市中心医院",
  "places": [
    {
      "id": "poi_001",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区东山路99号",
      "lat": 23.666123,
      "lng": 116.676392,
      "distance": 3116.0,
      "duration": 625,
      "price": 17.79,
      "score": 0.95
    },
    {
      "id": "poi_002",
      "name": "潮州市人民医院",
      "address": "广东省潮州市XX路",
      "lat": 23.654321,
      "lng": 116.665432,
      "distance": 4500.0,
      "duration": 780,
      "price": 22.50,
      "score": 0.88
    }
  ],
  "data": {
    "ocrText": "潮州市中心医院",
    "places": [
      {
        "id": "poi_001",
        "name": "潮州市中心医院",
        "address": "广东省潮州市湘桥区东山路99号",
        "lat": 23.666123,
        "lng": 116.676392,
        "distance": 3116.0,
        "duration": 625,
        "price": 17.79,
        "score": 0.95
      }
    ]
  }
}
```

#### ✅ 成功响应（直接下单）

```json
{
  "sessionId": "unique-session-id-123",
  "timestamp": 1775280220155,
  "type": "image_recognition",
  "success": true,
  "message": "图片识别成功",
  "ocrText": "北京大学",
  "data": {
    "ocrText": "北京大学",
    "order": {
      "poi": {
        "id": "poi_001",
        "name": "北京大学",
        "address": "北京市海淀区颐和园路5号",
        "lat": 39.986951,
        "lng": 116.305713
      },
      "route": {
        "distance": 15000.0,
        "duration": 1800,
        "price": 45.00
      }
    }
  }
}
```

#### ⚠️ 失败响应（未识别到地址）

```json
{
  "sessionId": "unique-session-id-123",
  "timestamp": 1775280220155,
  "type": "image_recognition",
  "success": false,
  "message": "图片识别失败：未识别到有效地址信息",
  "ocrText": "",
  "data": {
    "ocrText": "",
    "message": "识别失败：未识别到有效地址信息"
  }
}
```

#### ❌ 错误响应（系统异常）

```json
{
  "type": "error",
  "success": false,
  "message": "图片识别失败：图片大小超过限制",
  "code": 500,
  "timestamp": 1775280220155,
  "sessionId": "unique-session-id-123"
}
```

---

### 1.4 响应字段说明

#### 顶层字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| sessionId | String | 会话 ID（与请求一致） |
| timestamp | Number | 服务器时间戳（毫秒） |
| type | String | 响应类型：`"image_recognition"` |
| success | Boolean | 是否成功 |
| message | String | 响应消息 |
| ocrText | String | OCR 识别的文字内容（顶层字段，方便访问） |
| places | Array | POI 列表数组（如果有搜索结果，顶层字段） |
| data | Object | 完整的数据对象（包含所有字段） |

#### data 对象字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ocrText | String | OCR 识别的文字内容 |
| places | Array | POI 列表（如果识别到地址并搜索到地点） |
| order | Object | 订单数据（如果直接下单成功） |
| message | String | 附加消息（如果没有 places 或 order） |

#### places 数组元素字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | String | POI 唯一标识 |
| name | String | 地点名称 |
| address | String | 详细地址 |
| lat | Number | 纬度 |
| lng | Number | 经度 |
| distance | Number | 距离（米） |
| duration | Number | 预计时长（秒） |
| price | Number | 预估价格（元） |
| score | Number | 匹配得分（0-1） |

---

### 1.5 Android (Kotlin) 完整示例

```kotlin
class ImageRecognitionViewModel : ViewModel() {
    
    private lateinit var webSocket: WebSocket
    private var sessionId: String = UUID.randomUUID().toString()
    
    // 1. 初始化 WebSocket
    fun initWebSocket() {
        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)  // 心跳间隔
            .build()
        
        val request = Request.Builder()
            .url("ws://192.168.1.106:8080/ws/agent?token=${getToken()}")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "连接成功")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleResponse(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "连接失败", t)
                reconnectAfterDelay(3000)
            }
        })
    }
    
    // 2. 选择并发送图片
    fun selectAndSendImage() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        activity.startActivityForResult(intent, REQUEST_CODE_IMAGE)
    }
    
    // 3. 处理图片选择
    fun onImageSelected(imageUri: Uri?) {
        if (imageUri == null) return
        
        viewModelScope.launch {
            try {
                showLoading(true)
                
                // ✅ 压缩图片到 500KB 以内
                val base64 = compressAndToBase64(imageUri, maxSizeKB = 500)
                
                // ✅ 获取位置
                val location = getCurrentLocation()
                
                // ✅ 构建消息
                val message = JSONObject().apply {
                    put("type", "image")
                    put("sessionId", sessionId)
                    put("imageBase64", base64)
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                }
                
                // ✅ 检查 WebSocket 状态并发送
                if (webSocket.readyState() == 1) { // OPEN
                    webSocket.send(message.toString())
                    Log.d("ImageRecognition", "图片已发送")
                } else {
                    showError("WebSocket 未连接")
                }
                
            } catch (e: Exception) {
                showError("图片处理失败：${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    // 4. 压缩并转 Base64
    suspend fun compressAndToBase64(uri: Uri, maxSizeKB: Int): String {
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            // 压缩
            var quality = 90
            var outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            while (outputStream.toByteArray().size > maxSizeKB * 1024 && quality > 10) {
                outputStream.reset()
                quality -= 10
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
            
            // 转 Base64
            val byteArray = outputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            
            // 添加 data URI 前缀
            "data:image/jpeg;base64,$base64"
        }
    }
    
    // 5. 处理响应
    private fun handleResponse(response: String) {
        runOnUiThread {
            showLoading(false)
            
            try {
                val json = JSONObject(response)
                val type = json.getString("type")
                val success = json.getBoolean("success")
                
                when (type) {
                    "image_recognition" -> {
                        if (success) {
                            // ✅ 识别成功
                            val ocrText = json.optString("ocrText", "")
                            
                            // 优先从顶层获取 places
                            if (json.has("places")) {
                                val placesArray = json.getJSONArray("places")
                                showPlacesList(placesArray)
                            } 
                            // 其次从 data 中获取
                            else if (json.has("data")) {
                                val data = json.getJSONObject("data")
                                if (data.has("places")) {
                                    val placesArray = data.getJSONArray("places")
                                    showPlacesList(placesArray)
                                } else if (data.has("order")) {
                                    val orderData = data.getJSONObject("order")
                                    showOrderConfirmation(orderData)
                                } else {
                                    val message = data.optString("message", "未找到相关地点")
                                    showToast(message)
                                }
                            }
                        } else {
                            // ❌ 识别失败
                            val message = json.getString("message")
                            showToast("识别失败：$message")
                        }
                    }
                    
                    "error" -> {
                        // ❌ 系统错误
                        val message = json.getString("message")
                        showToast("错误：$message")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("ImageRecognition", "解析响应失败", e)
                showToast("响应解析失败")
            }
        }
    }
    
    // 6. 显示地点列表
    private fun showPlacesList(placesArray: JSONArray) {
        val places = mutableListOf<PoiItem>()
        for (i in 0 until placesArray.length()) {
            val poi = placesArray.getJSONObject(i)
            places.add(PoiItem(
                id = poi.getString("id"),
                name = poi.getString("name"),
                address = poi.getString("address"),
                lat = poi.getDouble("lat"),
                lng = poi.getDouble("lng"),
                distance = poi.getDouble("distance"),
                duration = poi.getInt("duration"),
                price = poi.getDouble("price"),
                score = poi.getDouble("score")
            ))
        }
        
        // 显示地点列表 UI
        navigateToPlacesList(places)
    }
    
    // 7. 显示订单确认
    private fun showOrderConfirmation(orderData: JSONObject) {
        val poi = orderData.getJSONObject("poi")
        val route = orderData.getJSONObject("route")
        
        val confirmation = OrderConfirmation(
            poiName = poi.getString("name"),
            address = poi.getString("address"),
            distance = route.getDouble("distance"),
            duration = route.getInt("duration"),
            price = route.getDouble("price")
        )
        
        // 显示订单确认对话框
        showConfirmDialog(confirmation)
    }
}
```

---

### 1.6 Web (JavaScript) 完整示例

```javascript
class ImageRecognitionService {
    constructor() {
        this.sessionId = this.generateSessionId();
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
    }
    
    // 1. 连接 WebSocket
    connect() {
        const token = getToken();
        this.ws = new WebSocket(`ws://192.168.1.106:8080/ws/agent?token=${token}`);
        
        this.ws.onopen = () => {
            console.log('WebSocket 连接成功');
            this.reconnectAttempts = 0;
        };
        
        this.ws.onmessage = (event) => {
            const response = JSON.parse(event.data);
            this.handleResponse(response);
        };
        
        this.ws.onerror = (error) => {
            console.error('WebSocket 错误:', error);
        };
        
        this.ws.onclose = () => {
            console.warn('WebSocket 连接关闭');
            this.reconnect();
        };
    }
    
    // 2. 重连机制
    reconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
            console.log(`${delay}ms 后尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            setTimeout(() => this.connect(), delay);
        } else {
            console.error('达到最大重连次数，请刷新页面');
        }
    }
    
    // 3. 发送图片
    async sendImage(file) {
        try {
            // 校验
            if (file.size > 10 * 1024 * 1024) {
                throw new Error('图片大小不能超过 10MB');
            }
            
            // 压缩并转 Base64
            const base64 = await this.compressAndToBase64(file, 500);
            
            // 获取位置
            const location = await this.getCurrentLocation();
            
            // 构建消息
            const message = {
                type: "image",
                sessionId: this.sessionId,
                imageBase64: base64,
                lat: location.latitude,
                lng: location.longitude
            };
            
            // 发送
            if (this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify(message));
                this.showLoading(true);
            } else {
                throw new Error('WebSocket 未连接');
            }
            
        } catch (error) {
            this.showError(error.message);
        }
    }
    
    // 4. 压缩并转 Base64
    compressAndToBase64(file, maxSizeKB) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onload = (e) => {
                const img = new Image();
                img.src = e.target.result;
                img.onload = () => {
                    const canvas = document.createElement('canvas');
                    const ctx = canvas.getContext('2d');
                    
                    // 压缩到 1920x1080
                    let width = img.width;
                    let height = img.height;
                    const maxWidth = 1920;
                    const maxHeight = 1080;
                    
                    if (width > maxWidth) {
                        height = (maxWidth / width) * height;
                        width = maxWidth;
                    }
                    if (height > maxHeight) {
                        width = (maxHeight / height) * width;
                        height = maxHeight;
                    }
                    
                    canvas.width = width;
                    canvas.height = height;
                    ctx.drawImage(img, 0, 0, width, height);
                    
                    // 转 Base64（JPEG 格式，质量 0.8）
                    let quality = 0.8;
                    let base64 = canvas.toDataURL('image/jpeg', quality);
                    
                    // 如果仍然太大，继续降低质量
                    while (base64.length > maxSizeKB * 1024 * 1.37 && quality > 0.1) {
                        quality -= 0.1;
                        base64 = canvas.toDataURL('image/jpeg', quality);
                    }
                    
                    resolve(base64);
                };
                img.onerror = reject;
            };
            reader.onerror = reject;
        });
    }
    
    // 5. 处理响应
    handleResponse(response) {
        this.showLoading(false);
        
        switch (response.type) {
            case 'image_recognition':
                if (response.success) {
                    this.handleImageRecognitionSuccess(response);
                } else {
                    this.showError(response.message || '识别失败');
                }
                break;
                
            case 'error':
                this.showError(response.message || '系统错误');
                break;
                
            default:
                console.warn('未知响应类型:', response.type);
        }
    }
    
    // 6. 处理识别成功
    handleImageRecognitionSuccess(response) {
        console.log('OCR 识别结果:', response.ocrText);
        
        // 优先从顶层获取 places
        if (response.places && response.places.length > 0) {
            this.showPlacesList(response.places);
        } 
        // 其次从 data 中获取
        else if (response.data) {
            if (response.data.places && response.data.places.length > 0) {
                this.showPlacesList(response.data.places);
            } else if (response.data.order) {
                this.showOrderConfirmation(response.data.order);
            } else {
                this.showToast(response.data.message || '未找到相关地点');
            }
        }
    }
    
    // 7. 显示地点列表
    showPlacesList(places) {
        console.log('找到以下地点:', places);
        
        // 渲染地点列表 UI
        const html = places.map(poi => `
            <div class="poi-item" onclick="selectPoi('${poi.id}')">
                <h3>${poi.name}</h3>
                <p>${poi.address}</p>
                <p>距离: ${(poi.distance / 1000).toFixed(2)}km</p>
                <p>预计时长: ${Math.floor(poi.duration / 60)}分钟</p>
                <p>价格: ¥${poi.price.toFixed(2)}</p>
            </div>
        `).join('');
        
        document.getElementById('places-list').innerHTML = html;
    }
    
    // 8. 显示订单确认
    showOrderConfirmation(orderData) {
        const { poi, route } = orderData;
        
        const html = `
            <div class="order-confirmation">
                <h2>确认订单</h2>
                <p>目的地: ${poi.name}</p>
                <p>地址: ${poi.address}</p>
                <p>距离: ${(route.distance / 1000).toFixed(2)}km</p>
                <p>预计时长: ${Math.floor(route.duration / 60)}分钟</p>
                <p>价格: ¥${route.price.toFixed(2)}</p>
                <button onclick="confirmOrder()">确认下单</button>
            </div>
        `;
        
        document.getElementById('order-modal').innerHTML = html;
    }
    
    // 工具方法
    generateSessionId() {
        return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    }
    
    getCurrentLocation() {
        return new Promise((resolve, reject) => {
            if (!navigator.geolocation) {
                reject(new Error('浏览器不支持地理定位'));
                return;
            }
            
            navigator.geolocation.getCurrentPosition(
                (position) => {
                    resolve({
                        latitude: position.coords.latitude,
                        longitude: position.coords.longitude
                    });
                },
                (error) => {
                    reject(new Error('获取位置失败: ' + error.message));
                }
            );
        });
    }
    
    showLoading(show) {
        document.getElementById('loading').style.display = show ? 'block' : 'none';
    }
    
    showError(message) {
        alert('错误: ' + message);
    }
    
    showToast(message) {
        // 显示 Toast 提示
        console.log('Toast:', message);
    }
}

// 使用示例
const imageService = new ImageRecognitionService();
imageService.connect();

// 文件选择事件
document.getElementById('image-input').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
        imageService.sendImage(file);
    }
});
```

---

## 🌐 方式 2：HTTP REST 接口（备选方案）

### 2.1 接口信息

```http
POST /api/agent/image
Content-Type: application/json
X-User-Id: 123
```

### 2.2 请求体

```json
{
  "sessionId": "unique-session-id-123",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "lat": 23.6533,
  "lng": 116.6772
}
```

### 2.3 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "IMAGE_RECOGNITION",
    "success": true,
    "message": "图片识别成功",
    "ocrText": "潮州市中心医院",
    "places": [
      {
        "id": "poi_001",
        "name": "潮州市中心医院",
        "address": "广东省潮州市湘桥区东山路99号",
        "lat": 23.666123,
        "lng": 116.676392,
        "distance": 3116.0,
        "duration": 625,
        "price": 17.79,
        "score": 0.95
      }
    ],
    "data": {
      "ocrText": "潮州市中心医院",
      "places": [...]
    }
  }
}
```

### 2.4 前端调用示例

```javascript
async function sendImageViaHttp(file) {
    try {
        // 1. 压缩图片
        const base64 = await compressAndToBase64(file, 500);
        
        // 2. 获取位置
        const location = await getCurrentLocation();
        
        // 3. 构建请求体
        const requestBody = {
            sessionId: sessionId,
            imageBase64: base64,
            lat: location.latitude,
            lng: location.longitude
        };
        
        // 4. 发送请求
        const response = await fetch('/api/agent/image', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': getUserId()  // ⚠️ 必须添加
            },
            body: JSON.stringify(requestBody)
        });
        
        const result = await response.json();
        
        if (result.code === 200) {
            const agentResponse = result.data;
            
            // 处理识别结果
            if (agentResponse.success) {
                if (agentResponse.places && agentResponse.places.length > 0) {
                    showPlacesList(agentResponse.places);
                } else if (agentResponse.data && agentResponse.data.order) {
                    showOrderConfirmation(agentResponse.data.order);
                }
            } else {
                alert(`识别失败：${agentResponse.message}`);
            }
        } else {
            alert(`请求失败：${result.message}`);
        }
        
    } catch (error) {
        alert(`网络错误：${error.message}`);
    }
}
```

---

## 🐛 常见问题 FAQ

### Q1: 返回"未能识别到有效地址"
**原因**：图片中没有明确的地址信息  
**解决**：提示用户拍摄清晰的招牌、路牌等

### Q2: 返回"图片大小超过限制"
**原因**：图片超过 10MB  
**解决**：加强前端压缩逻辑，控制在 500KB - 1MB 以内

### Q3: 识别速度慢（>5 秒）
**原因**：图片太大或网络差  
**解决**：
- 压缩图片到 200-500KB
- 使用 WiFi 或 4G/5G 网络
- 降低图片分辨率到 1920x1080

### Q4: 识别结果不准确
**原因**：图片模糊或角度倾斜  
**解决**：提示用户重新拍摄，确保文字清晰可见

### Q5: WebSocket 连接失败
**原因**：Token 过期或网络问题  
**解决**：
- 检查 Token 是否有效
- 重新登录获取新 Token
- 检查网络连接

### Q6: 前端收不到响应消息
**原因**：之前后端返回格式不一致  
**解决**：✅ 已修复！现在后端会返回完整的字段：
- `type`: "image_recognition"
- `success`: true/false
- `message`: 响应消息
- `ocrText`: OCR 识别的文字（顶层字段）
- `places`: POI 列表数组（顶层字段）
- `data`: 完整数据对象（包含所有字段）

---

## 📞 技术支持

如有问题，请联系后端开发团队。

**更新日期**：2026-04-05  
**版本**：v2.0  
**状态**：✅ 生产就绪
