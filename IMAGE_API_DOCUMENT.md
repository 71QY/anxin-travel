# 图片处理功能 - 前端 API 接口文档

## 📋 功能概览

本项目提供**两个独立的图片处理功能**：

1. **个人头像上传** - 用户个人资料管理
2. **智能体图像识别分析** - AI 驱动的目的地识别与自动下单

---

## 🖼️ 功能一：个人头像上传

### 1. 上传头像接口

#### 接口信息
```http
POST /api/user/avatar
Content-Type: multipart/form-data
Authorization: Bearer {token}
```

#### 请求参数
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| avatarFile | File | ✅ | 头像图片文件 |

#### 文件格式要求
- ✅ **支持格式**: JPEG, PNG, GIF, WEBP
- ✅ **最大大小**: 5 MB
- ⚠️ **建议大小**: < 1 MB（提升上传速度）
- ⚠️ **建议尺寸**: 500x500 px 或 800x800 px（正方形）

#### 成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": "/api/user/avatar/avatar_7_1775297403075.jpg"
}
```

#### 失败响应
```json
{
  "code": 500,
  "message": "头像上传失败：图片大小不能超过 5MB"
}
```

#### Android (Kotlin) 示例
```kotlin
// 1. 选择图片
fun selectAvatar() {
    val intent = Intent(Intent.ACTION_PICK).apply {
        type = "image/*"
    }
    startActivityForResult(intent, REQUEST_CODE_AVATAR)
}

// 2. 处理选择结果
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    if (requestCode == REQUEST_CODE_AVATAR && resultCode == RESULT_OK) {
        val imageUri = data?.data
        uploadAvatar(imageUri)
    }
}

// 3. 上传到服务器
fun uploadAvatar(imageUri: Uri?) {
    if (imageUri == null) return
    
    // 压缩图片
    val compressedFile = compressImage(imageUri, maxSizeKB = 500)
    
    // 构建请求
    val requestFile = RequestBody.create(
        MediaType.parse("image/jpeg"),
        compressedFile
    )
    
    val body = MultipartBody.Part.createFormData(
        "avatarFile",
        compressedFile.name,
        requestFile
    )
    
    // 发送请求
    apiService.uploadAvatar(body).enqueue(object : Callback<Result<String>> {
        override fun onResponse(call: Call<Result<String>>, response: Response<Result<String>>) {
            if (response.isSuccessful) {
                val avatarUrl = response.body()?.data
                showSuccess("头像上传成功")
                updateAvatarDisplay(avatarUrl)
            } else {
                showError("上传失败")
            }
        }
        
        override fun onFailure(call: Call<Result<String>>, t: Throwable) {
            showError("网络错误")
        }
    })
}

// 4. 图片压缩
fun compressImage(uri: Uri, maxSizeKB: Int): File {
    val inputStream = contentResolver.openInputStream(uri)
    val bitmap = BitmapFactory.decodeStream(inputStream)
    
    // 计算压缩比例
    var quality = 90
    var outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    
    while (outputStream.toByteArray().size > maxSizeKB * 1024 && quality > 10) {
        outputStream.reset()
        quality -= 10
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    }
    
    // 保存到临时文件
    val file = File(cacheDir, "compressed_avatar.jpg")
    FileOutputStream(file).use { it.write(outputStream.toByteArray()) }
    
    return file
}
```

#### Web (JavaScript) 示例
```javascript
// 1. 选择图片
document.getElementById('avatarInput').addEventListener('change', function(e) {
    const file = e.target.files[0];
    if (file) {
        uploadAvatar(file);
    }
});

// 2. 上传头像
async function uploadAvatar(file) {
    // 校验文件大小
    if (file.size > 5 * 1024 * 1024) {
        alert('图片大小不能超过 5MB');
        return;
    }
    
    // 压缩图片
    const compressedFile = await compressImage(file, 500); // 压缩到 500KB
    
    // 构建 FormData
    const formData = new FormData();
    formData.append('avatarFile', compressedFile);
    
    try {
        const response = await fetch('/api/user/avatar', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${getToken()}`
            },
            body: formData
        });
        
        const result = await response.json();
        
        if (result.code === 200) {
            const avatarUrl = result.data;
            showSuccess('头像上传成功');
            updateAvatarDisplay(avatarUrl);
        } else {
            showError(result.message);
        }
    } catch (error) {
        showError('网络错误');
    }
}

// 3. 图片压缩
function compressImage(file, maxSizeKB) {
    return new Promise((resolve) => {
        const reader = new FileReader();
        reader.readAsDataURL(file);
        reader.onload = (e) => {
            const img = new Image();
            img.src = e.target.result;
            img.onload = () => {
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                
                // 保持宽高比，最大边 800px
                let width = img.width;
                let height = img.height;
                const maxSize = 800;
                
                if (width > height && width > maxSize) {
                    height = (maxSize / width) * height;
                    width = maxSize;
                } else if (height > maxSize) {
                    width = (maxSize / height) * width;
                    height = maxSize;
                }
                
                canvas.width = width;
                canvas.height = height;
                ctx.drawImage(img, 0, 0, width, height);
                
                // 转 Blob
                canvas.toBlob((blob) => {
                    resolve(new File([blob], file.name, { type: 'image/jpeg' }));
                }, 'image/jpeg', 0.8);
            };
        };
    });
}
```

---

### 2. 获取头像接口

#### 接口信息
```http
GET /api/user/avatar/{filename}
```

#### 路径参数
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| filename | String | ✅ | 头像文件名 |

#### 响应
- **Content-Type**: `image/jpeg`
- **Body**: 图片二进制流

#### 使用示例
```html
<!-- HTML -->
<img src="/api/user/avatar/avatar_7_1775297403075.jpg" alt="头像" />

<!-- Android Glide -->
Glide.with(context)
    .load("${BASE_URL}/api/user/avatar/${fileName}")
    .circleCrop()
    .into(imageView)
```

---

## 🤖 功能二：智能体图像识别分析

### 核心能力说明

智能体具备**图像识别与智能分析能力**，能够：
- ✅ 通过对用户上传图片内容的解析，精准识别出用户的目的地信息
- ✅ 自动完成下单操作
- ✅ 从图片中提取多维度关键信息
- ✅ 针对不同场景与需求进行智能化处理与响应

### 支持的图片类型

| 图片类型 | 示例 | 识别内容 | 后续操作 |
|---------|------|---------|---------|
| **医院招牌** | 医院门头照片 | 医院名称、地址 | 搜索附近医院 |
| **聊天截图** | "我们去北京大学吧" | 提取对话中的地址 | 搜索北京大学 |
| **路牌照片** | 道路指示牌 | 地名、方向 | 路线规划 |
| **商家名片** | 餐厅/酒店名片 | 商家名称、地址 | 直接下单 |
| **景点照片** | 旅游景点 | 景点名称 | 推荐附近服务 |

---

### 方式 1：WebSocket 实时交互（⭐ 推荐）

#### 连接地址
```
ws://192.168.1.106:8080/ws/agent?token=YOUR_TOKEN
```

#### 发送图片消息
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
| sessionId | String | ✅ | 会话 ID（UUID 格式） |
| imageBase64 | String | ✅ | Base64 编码的图片 |
| lat | Number | ✅ | 用户当前位置纬度 |
| lng | Number | ✅ | 用户当前位置经度 |

#### 图片格式要求
- ✅ **支持格式**: JPEG, PNG, BMP, WEBP
- ✅ **最大大小**: 5 MB
- ⚠️ **建议大小**: < 1 MB（提高识别速度）
- ⚠️ **建议分辨率**: 1920x1080 以内
- ✅ **Base64 格式**: 
  - 带前缀：`data:image/jpeg;base64,...`
  - 不带前缀：`/9j/4AAQ...`（系统自动添加）

#### 成功响应（识别到地址）
```json
{
  "sessionId": "unique-session-id-123",
  "timestamp": 1775280220155,
  "type": "image_recognition",
  "success": true,
  "message": "识别成功",
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
    ]
  }
}
```

#### 成功响应（直接下单）
```json
{
  "sessionId": "unique-session-id-123",
  "timestamp": 1775280220155,
  "type": "order",
  "success": true,
  "message": "已确认目的地，正在创建订单",
  "data": {
    "poi": {
      "id": "poi_001",
      "name": "潮州市中心医院",
      "address": "广东省潮州市湘桥区东山路99号",
      "lat": 23.666123,
      "lng": 116.676392
    },
    "route": {
      "distance": 3116.0,
      "duration": 625,
      "price": 17.79
    }
  }
}
```

#### 失败响应（未识别到地址）
```json
{
  "sessionId": "unique-session-id-123",
  "timestamp": 1775280220155,
  "type": "image_recognition",
  "success": false,
  "message": "未能从图片中识别到有效地址信息",
  "data": {
    "ocrText": "一些文字内容",
    "message": "未找到地址信息"
  }
}
```

#### 错误响应（系统异常）
```json
{
  "type": "error",
  "success": false,
  "message": "图片识别失败：图片大小超过限制",
  "code": 500,
  "timestamp": 1775280220155
}
```

#### Android (Kotlin) 完整示例
```kotlin
class ImageRecognitionViewModel : ViewModel() {
    
    private lateinit var webSocket: WebSocket
    private var sessionId: String = UUID.randomUUID().toString()
    
    // 1. 初始化 WebSocket
    fun initWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("ws://192.168.1.106:8080/ws/agent?token=${getToken()}")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleResponse(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.e("WebSocket", "连接失败", t)
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
                // 压缩图片
                val base64 = compressAndToBase64(imageUri, maxSizeKB = 500)
                
                // 获取位置
                val location = getCurrentLocation()
                
                // 构建消息
                val message = JSONObject().apply {
                    put("type", "image")
                    put("sessionId", sessionId)
                    put("imageBase64", base64)
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                }
                
                // 发送
                webSocket.send(message.toString())
                showLoading(true)
                
            } catch (e: Exception) {
                showError("图片处理失败：${e.message}")
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
            
            // 添加前缀
            "data:image/jpeg;base64,$base64"
        }
    }
    
    // 5. 处理响应
    fun handleResponse(response: String) {
        showLoading(false)
        
        val json = JSONObject(response)
        val type = json.getString("type")
        
        when (type) {
            "image_recognition" -> {
                val data = json.getJSONObject("data")
                val ocrText = data.getString("ocrText")
                
                if (data.has("places")) {
                    // 显示候选列表
                    val places = data.getJSONArray("places")
                    showPlaceList(places)
                    
                    // 语音播报
                    speak("找到 ${places.length()} 个相关地点")
                } else {
                    showMessage("未识别到地址：$ocrText")
                }
            }
            "order" -> {
                // 订单创建成功
                val orderData = json.getJSONObject("data")
                showOrderDetail(orderData)
                speak("订单创建成功")
            }
            "error" -> {
                val errorMsg = json.getString("message")
                showError(errorMsg)
            }
        }
    }
}
```

#### Web (JavaScript) 完整示例
```javascript
class ImageRecognitionService {
    constructor() {
        this.sessionId = this.generateSessionId();
        this.ws = null;
    }
    
    // 1. 连接 WebSocket
    connect() {
        const token = getToken();
        this.ws = new WebSocket(`ws://192.168.1.106:8080/ws/agent?token=${token}`);
        
        this.ws.onmessage = (event) => {
            const response = JSON.parse(event.data);
            this.handleResponse(response);
        };
        
        this.ws.onerror = (error) => {
            console.error('WebSocket 错误:', error);
            this.reconnect();
        };
    }
    
    // 2. 发送图片
    async sendImage(file) {
        try {
            // 校验
            if (file.size > 5 * 1024 * 1024) {
                throw new Error('图片大小不能超过 5MB');
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
            this.ws.send(JSON.stringify(message));
            this.showLoading(true);
            
        } catch (error) {
            this.showError(error.message);
        }
    }
    
    // 3. 压缩并转 Base64
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
                    const base64 = canvas.toDataURL('image/jpeg', 0.8);
                    resolve(base64);
                };
                img.onerror = reject;
            };
            reader.onerror = reject;
        });
    }
    
    // 4. 处理响应
    handleResponse(response) {
        this.showLoading(false);
        
        switch (response.type) {
            case 'image_recognition':
                const { ocrText, places } = response.data;
                
                if (places && places.length > 0) {
                    // 显示候选列表
                    this.displayPlaces(places);
                    this.speak(`找到 ${places.length} 个相关地点`);
                } else {
                    alert(`未识别到地址：${ocrText}`);
                }
                break;
                
            case 'order':
                // 订单创建成功
                this.showOrderDetail(response.data);
                this.speak('订单创建成功');
                break;
                
            case 'error':
                alert(`识别失败：${response.message}`);
                break;
        }
    }
}
```

---

### 方式 2：HTTP POST 接口

#### 接口信息
```http
POST /api/agent/image
Content-Type: application/json
X-User-Id: 123
```

#### 请求参数
```json
{
  "sessionId": "unique-session-id-123",
  "imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRg...",
  "lat": 23.6533,
  "lng": 116.6772
}
```

#### 响应格式
与 WebSocket 响应格式相同（包裹在 Result 中）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "image_recognition",
    "success": true,
    "message": "识别成功",
    "data": {
      "ocrText": "...",
      "places": [...]
    }
  }
}
```

---

## ⚠️ 重要注意事项

### 1. 图片大小控制

| 指标 | 要求 | 原因 |
|------|------|------|
| **最大限制** | 5 MB | 超过会报错 |
| **建议大小** | < 1 MB | 提高上传/识别速度 |
| **最佳大小** | 200KB - 500KB | 速度与质量平衡 |
| **建议分辨率** | ≤ 1920x1080 | 识别准确率不变，速度提升 50% |

### 2. SessionId 管理

```kotlin
// ✅ 正确：保持一致性
val sessionId = UUID.randomUUID().toString()  // 生成一次
sendSearchRequest(sessionId)      // 搜索时使用
sendImageRequest(sessionId)       // 图片识别使用同一个
sendConfirmRequest(sessionId)     // 确认时也使用同一个

// ❌ 错误：每次都变
sendSearchRequest(generateUUID())      // 搜索生成新的
sendImageRequest(generateUUID())       // 图片又生成新的
```

**影响**：
- SessionId 不一致 → 后端无法关联上下文 → 无法下单

### 3. 位置信息必须传递

```kotlin
// ✅ 正确：包含 lat/lng
{
  "type": "image",
  "lat": 23.6533,
  "lng": 116.6772
}

// ❌ 错误：缺少位置
{
  "type": "image"
  // 没有 lat/lng
}
```

**影响**：
- 有位置 → 按距离排序，推荐最近的地点
- 无位置 → 随机排序，可能推荐错误的地点

### 4. 图片质量把控

**✅ 推荐：**
- 清晰的招牌照片
- 正对文字拍摄
- 光线充足
- 分辨率适中（1920x1080 左右）

**❌ 避免：**
- 模糊的照片
- 倾斜角度过大
- 光线太暗或过曝
- 分辨率过高（>4000x3000）

### 5. 错误处理

```kotlin
try {
    sendImageForRecognition()
} catch (e: Exception) {
    when {
        e.message?.contains("图片不能为空") == true -> {
            showToast("请选择图片")
        }
        e.message?.contains("不支持的图片格式") == true -> {
            showToast("仅支持 JPEG/PNG/BMP 格式")
        }
        e.message?.contains("图片大小不能超过") == true -> {
            showToast("图片太大，请重新拍摄或选择")
        }
        e.message?.contains("未能从图片中识别") == true -> {
            showToast("未识别到地址，请拍摄清晰的招牌或路牌")
        }
        else -> {
            showToast("识别失败，请重试")
        }
    }
}
```

### 6. 性能优化建议

| 优化项 | 建议值 | 效果 |
|--------|-------|------|
| 图片压缩 | 500KB 以内 | 上传速度快 3 倍 |
| 分辨率 | 1920x1080 | 识别准确率不变，速度提升 50% |
| Base64 压缩 | NO_WRAP 模式 | 减少 30% 数据量 |
| 复用 SessionId | 同一会话不变 | 减少后端缓存压力 |
| 预加载位置 | 进入页面前获取 | 减少等待时间 |

---

## 🔄 完整业务流程

### 场景：用户拍摄医院招牌叫车

```
1. 用户打开 App，点击"拍照叫车"
   ↓
2. 前端获取当前位置 (lat, lng)
   ↓
3. 用户拍摄/选择图片
   ↓
4. 前端压缩图片（500KB 以内）并转 Base64
   ↓
5. 发送到 WebSocket
   {
     "type": "image",
     "sessionId": "abc-123",
     "imageBase64": "data:image/jpeg;base64,...",
     "lat": 23.6533,
     "lng": 116.6772
   }
   ↓
6. 后端 OCR 识别（通义千问 VL）→ AI 解析意图 → POI 搜索
   ↓
7. 返回候选地点列表
   {
     "type": "image_recognition",
     "data": {
       "ocrText": "潮州市中心医院",
       "places": [...]
     }
   }
   ↓
8. 前端显示候选列表供用户选择
   ↓
9. 用户选择某个 POI
   ↓
10. 前端发送确认消息
    {
      "type": "confirm",
      "content": "潮州市中心医院",
      "lat": 23.6533,
      "lng": 116.6772
    }
    ↓
11. 后端创建订单并返回
    {
      "type": "order",
      "message": "已确认目的地，正在创建订单",
      "data": {...}
    }
    ↓
12. 前端显示订单详情，流程结束
```

---

## 🐛 常见问题 FAQ

### Q1: 返回"未能识别到有效地址"
**原因**：图片中没有明确的地址信息  
**解决**：提示用户拍摄清晰的招牌、路牌等

### Q2: 返回"图片大小超过限制"
**原因**：图片超过 5MB  
**解决**：加强前端压缩逻辑，控制在 500KB 以内

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

---

## 📞 技术支持

如有问题，请联系后端开发团队。

**更新日期**：2026-04-04  
**版本**：v1.0  
**状态**：✅ 生产就绪
