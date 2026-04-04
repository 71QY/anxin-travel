# 智能出行助手 - 语音功能 API 接口文档

## 📋 概述

当前后端**不直接提供语音识别（ASR）服务**，而是采用**前端语音转文字 + 后端文本处理**的架构方案。

### 技术方案
```
用户语音 → 前端语音识别(ASR) → 文本 → WebSocket/HTTP → 后端AI处理 → 返回结果
```

**优势**：
- ✅ 降低后端压力
- ✅ 前端可选择最佳 ASR 服务商
- ✅ 后端专注于业务逻辑和 AI 意图识别

---

## 🎤 前端语音识别推荐方案

### 方案 1：Android 原生语音识别（推荐）

```kotlin
// Android Kotlin 示例
import android.speech.RecognizerIntent
import android.content.Intent

private fun startVoiceRecognition() {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                 RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")  // 中文
        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出目的地")
    }
    
    try {
        startActivityForResult(intent, VOICE_REQUEST_CODE)
    } catch (e: Exception) {
        showToast("您的设备不支持语音识别")
    }
}

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK) {
        val results = data?.getStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS
        )
        
        if (!results.isNullOrEmpty()) {
            val recognizedText = results[0]  // 最可能的结果
            log.d("Voice", "识别结果：$recognizedText")
            
            // 发送到后端
            sendToBackend(recognizedText)
        }
    }
}
```

### 方案 2：百度语音识别 SDK

```kotlin
// 百度语音识别 SDK
import com.baidu.speech.VoiceRecognitionService

val voiceService = VoiceRecognitionService(this)
voiceService.startListening(object : RecognitionListener {
    override fun onResults(results: ArrayList<RecognitionResult>) {
        val text = results.joinToString("") { it.bestResult }
        sendToBackend(text)
    }
    
    override fun onError(error: SpeechError) {
        log.e("Voice", "识别失败：${error.errorDescription}")
    }
})
```

### 方案 3：讯飞语音识别 SDK

```kotlin
// 讯飞语音识别
import com.iflytek.cloud.SpeechRecognizer

val recognizer = SpeechRecognizer.createRecognizer(context, initListener)
recognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
recognizer.startListening(recognizerListener)
```

---

## 🔌 后端接收接口

### 方式 1：WebSocket 实时交互（⭐ 推荐）

#### 连接地址
```
ws://192.168.1.106:8080/ws/agent?token=YOUR_TOKEN
```

#### 发送语音识别后的文本
```json
{
  "type": "user_message",
  "sessionId": "unique-session-id-123",
  "content": "我要去潮州市中心医院",
  "lat": 23.6533,
  "lng": 116.6772
}
```

#### 响应格式
```json
{
  "sessionId": "unique-session-id-123",
  "timestamp": 1775280220155,
  "type": "search",
  "success": true,
  "message": "找到 5 个相关地点",
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
```

#### 完整流程示例
```kotlin
class VoiceChatViewModel : ViewModel() {
    private lateinit var webSocket: WebSocket
    
    // 1. 开始语音识别
    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        }
        activity.startActivityForResult(intent, VOICE_CODE)
    }
    
    // 2. 收到识别结果后发送到 WebSocket
    fun onVoiceResult(recognizedText: String) {
        val location = getCurrentLocation()
        
        val message = JSONObject().apply {
            put("type", "user_message")
            put("sessionId", sessionId)
            put("content", recognizedText)  // 语音识别的文本
            put("lat", location.latitude)
            put("lng", location.longitude)
        }
        
        webSocket.send(message.toString())
    }
    
    // 3. 处理后端响应
    fun handleWebSocketResponse(response: String) {
        val json = JSONObject(response)
        when (json.getString("type")) {
            "search" -> {
                val places = json.getJSONArray("places")
                showPlaceList(places)
            }
            "order" -> {
                showMessage("订单创建成功")
            }
        }
    }
}
```

---

### 方式 2：HTTP POST 接口

#### 接口地址
```
POST /api/agent/search
Content-Type: application/json
X-User-Id: 123
```

#### 请求参数
```json
{
  "sessionId": "unique-session-id-123",
  "keyword": "我要去潮州市中心医院",
  "lat": 23.6533,
  "lng": 116.6772
}
```

#### 响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "SEARCH",
    "success": true,
    "message": "找到 5 个相关地点",
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

---

## 🗣️ 支持的语音指令类型

### 1. 搜索类指令
| 语音输入 | 后端识别 | 说明 |
|---------|---------|------|
| "我要去医院" | SEARCH | 搜索附近医院 |
| "带我去北京大学" | SEARCH | 搜索北京大学 |
| "找附近的餐厅" | SEARCH | 搜索附近餐厅 |
| "帮我找个酒店" | SEARCH | 搜索附近酒店 |

### 2. 确认类指令
| 语音输入 | 后端识别 | 说明 |
|---------|---------|------|
| "第一个" | CONFIRM | 选择第一个 POI |
| "就这个吧" | CONFIRM | 确认当前选择 |
| "下单" | ORDER | 直接创建订单 |

### 3. 图片类指令
| 语音输入 | 后端识别 | 说明 |
|---------|---------|------|
| "识别这张图片" | IMAGE | 触发图片识别 |

---

## ⚙️ 语音识别优化建议

### 1. 识别准确率提升

```kotlin
// 添加上下文提示词
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
             RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
    
    // 关键：添加领域提示词，提高地名识别准确率
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)  // 返回多个候选
    putExtra(RecognizerIntent.EXTRA_PROMPT, 
             "请说出目的地名称，例如：医院、酒店、北京大学")
}
```

### 2. 降噪处理

```kotlin
// 使用 AudioRecord 进行预处理
val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,
    16000,  // 采样率
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize
)

// 应用噪声抑制算法
val processedAudio = applyNoiseSuppression(rawAudio)
```

### 3. 离线识别 fallback

```kotlin
// 网络不佳时使用离线识别
if (!isNetworkAvailable()) {
    useOfflineRecognition()
} else {
    useOnlineRecognition()
}
```

---

## 🔧 完整实现示例

### Android Activity 实现

```kotlin
class VoiceSearchActivity : AppCompatActivity() {
    
    private lateinit var webSocket: WebSocket
    private var sessionId: String = UUID.randomUUID().toString()
    
    companion object {
        private const val VOICE_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_search)
        
        // 初始化 WebSocket
        initWebSocket()
        
        // 绑定语音按钮
        btnVoice.setOnClickListener {
            startVoiceRecognition()
        }
    }
    
    // 1. 启动语音识别
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出目的地")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        
        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "语音识别不可用", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 2. 处理识别结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0]
                log.d("Voice", "识别结果：$recognizedText")
                
                // 显示识别结果
                tvRecognizedText.text = recognizedText
                
                // 发送到后端
                sendToBackend(recognizedText)
            }
        }
    }
    
    // 3. 发送到 WebSocket
    private fun sendToBackend(text: String) {
        val location = getCurrentLocation()
        
        val message = JSONObject().apply {
            put("type", "user_message")
            put("sessionId", sessionId)
            put("content", text)
            put("lat", location.latitude)
            put("lng", location.longitude)
        }
        
        webSocket.send(message.toString())
        showLoading(true)
    }
    
    // 4. 处理后端响应
    private fun handleResponse(response: String) {
        showLoading(false)
        
        val json = JSONObject(response)
        val type = json.getString("type")
        
        when (type) {
            "search" -> {
                val places = json.getJSONArray("places")
                showPlaceList(places)
                
                // 语音播报
                speak("找到 ${places.length()} 个相关地点")
            }
            "confirm" -> {
                val poi = json.getJSONObject("data").getJSONObject("poi")
                speak("已确认目的地：${poi.getString("name")}")
            }
            "order" -> {
                speak("订单创建成功")
                showOrderDetail(json.getJSONObject("data"))
            }
        }
    }
    
    // 5. 语音播报（TTS）
    private fun speak(text: String) {
        val tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.CHINESE
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
    
    // 6. 获取当前位置
    private fun getCurrentLocation(): Location {
        // 使用 FusedLocationProviderClient 获取位置
        // ...
    }
}
```

---

## ⚠️ 注意事项

### 1. 权限申请
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

```kotlin
// 运行时权限申请
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        PERMISSION_REQUEST_CODE
    )
}
```

### 2. 网络超时处理
```kotlin
// WebSocket 超时重连
webSocket.setListener(object : WebSocketListener() {
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        log.e("WebSocket", "连接失败", t)
        reconnectAfterDelay(3000)  // 3 秒后重连
    }
})
```

### 3. 识别错误处理
```kotlin
when (resultCode) {
    Activity.RESULT_OK -> {
        // 识别成功
    }
    RecognizerIntent.RESULT_AUDIO_ERROR -> {
        showToast("音频录制失败，请检查麦克风权限")
    }
    RecognizerIntent.RESULT_CLIENT_ERROR -> {
        showToast("客户端错误，请重试")
    }
    RecognizerIntent.RESULT_NETWORK_ERROR -> {
        showToast("网络错误，请检查网络连接")
    }
    RecognizerIntent.RESULT_NO_MATCH -> {
        showToast("未识别到语音，请再说一次")
    }
}
```

---

## 📊 性能指标

| 指标 | 数值 | 说明 |
|------|------|------|
| 语音识别时间 | 1-3s | 取决于网络和 ASR 服务商 |
| 后端处理时间 | < 2s | AI 意图识别 + POI 搜索 |
| 总响应时间 | < 5s | 从说话到显示结果 |
| 识别准确率 | > 90% | 清晰发音、安静环境 |

---

## 🚀 优化建议

### 短期优化
1. **添加语音反馈**：识别成功后语音播报"正在搜索..."
2. **显示置信度**：展示识别结果的置信度分数
3. **多候选选择**：允许用户从多个识别结果中选择

### 长期优化
1. **自定义唤醒词**：实现"小安小安"唤醒
2. **连续对话**：支持多轮语音交互
3. **方言支持**：增加粤语、四川话等方言识别

---

## 📞 技术支持

如有问题，请联系后端开发团队。

**更新日期**：2026-04-04  
**版本**：v1.0  
**状态**：✅ 生产就绪
