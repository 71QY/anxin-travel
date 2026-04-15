# 亲情守护 - 私聊功能 API 接口文档

## 📌 基础信息

- **服务地址**: `http://localhost:8080` (开发环境)
- **认证方式**: JWT Token (Header: `Authorization: Bearer {token}`)
- **数据格式**: JSON
- **字符编码**: UTF-8

---

## 🔗 接口列表

### 1. 获取私聊历史记录

**接口地址**: `GET /api/chat/private/history/{targetUserId}`

**功能说明**: 查询与指定亲友/长辈的聊天历史记录

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| targetUserId | Long | 是 | 对方用户ID（亲友或长辈） |

**请求示例**:
```
GET /api/chat/private/history/2
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "senderId": 1,
      "receiverId": 2,
      "messageType": 1,
      "content": "你好，最近怎么样？",
      "isRead": 1,
      "createdAt": "2026-04-09T10:30:00"
    },
    {
      "id": 2,
      "senderId": 2,
      "receiverId": 1,
      "messageType": 2,
      "content": "https://example.com/audio.mp3",
      "isRead": 0,
      "createdAt": "2026-04-09T10:31:00"
    }
  ]
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 消息ID |
| senderId | Long | 发送者用户ID |
| receiverId | Long | 接收者用户ID |
| messageType | Integer | 消息类型：1文字 2语音 3图片 |
| content | String | 消息内容（文字内容或语音/图片URL） |
| isRead | Integer | 是否已读：0未读 1已读 |
| createdAt | String | 发送时间（ISO 8601格式） |

**错误响应**:
```json
{
  "code": 403,
  "message": "您与该用户没有绑定关系，无法查看聊天记录"
}
```

---

### 2. 发送私聊消息

**接口地址**: `POST /api/chat/private/send/{receiverId}`

**功能说明**: 向亲友/长辈发送私聊消息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| receiverId | Long | 是 | 接收者用户ID |

**请求体**:
```json
{
  "messageType": 1,
  "content": "你好，今天天气不错！"
}
```

**请求参数说明**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| messageType | Integer | 是 | 消息类型：1文字 2语音 3图片 |
| content | String | 是 | 消息内容（文字内容或语音/图片URL） |

**响应示例**:
```json
{
  "code": 200,
  "message": "发送成功",
  "data": null
}
```

**错误响应**:
```json
{
  "code": 403,
  "message": "您与该用户没有绑定关系，无法发送消息"
}
```

```json
{
  "code": 400,
  "message": "长辈模式仅支持语音和快捷短语"
}
```

---

### 3. 标记消息为已读

**接口地址**: `POST /api/chat/private/read/{senderId}`

**功能说明**: 将来自指定用户的未读消息标记为已读

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| senderId | Long | 是 | 发送者用户ID |

**请求示例**:
```
POST /api/chat/private/read/2
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": 5
}
```

**返回数据说明**: 
- `data`: 标记为已读的消息数量

---

### 4. 查询未读消息数量

**接口地址**: `GET /api/chat/private/unread`

**功能说明**: 查询当前用户所有未读消息总数

**请求示例**:
```
GET /api/chat/private/unread
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "unreadCount": 12
  }
}
```

---

## 🔄 WebSocket 实时推送

### 连接地址
```
ws://localhost:8080/ws/agent?token={your_jwt_token}
```

### 接收消息格式

当收到新的私聊消息时，WebSocket 会推送以下格式的数据：

```json
{
  "type": "PRIVATE_MESSAGE",
  "senderId": 2,
  "messageType": 1,
  "content": "我很好，谢谢关心！",
  "createdAt": "2026-04-09T10:35:00"
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| type | String | 固定值 "PRIVATE_MESSAGE" |
| senderId | Long | 发送者用户ID |
| messageType | Integer | 消息类型：1文字 2语音 3图片 |
| content | String | 消息内容 |
| createdAt | String | 发送时间 |

---

## ⚠️ 重要注意事项

### 1. **坐标系要求**
- ❌ 私聊功能不涉及地理位置，无需关注坐标系

### 2. **权限校验**
- ✅ 只有建立了亲情守护绑定关系的用户才能互相聊天
- ✅ 系统会自动校验双方是否为亲友/长辈关系
- ❌ 未绑定的用户无法发送或查看聊天记录

### 3. **长辈模式限制**
- ⚠️ 如果发送者处于长辈模式（`guardMode=1`）：
  - ❌ **不允许发送文字消息**（messageType=1）
  - ✅ **允许发送语音消息**（messageType=2）
  - ✅ **允许发送快捷短语**（messageType=3）
- 💡 前端应在长辈模式下隐藏文字输入框，仅显示语音和快捷短语按钮

### 4. **消息类型说明**
| messageType | 类型 | content 内容 |
|-------------|------|--------------|
| 1 | 文字 | 纯文本内容 |
| 2 | 语音 | 语音文件URL（需先上传到服务器） |
| 3 | 图片 | 图片文件URL（需先上传到服务器） |

### 5. **实时性保证**
- ✅ 消息发送后会立即通过 WebSocket 推送给接收者
- ✅ 如果接收者在线，会即时收到消息
- ⚠️ 如果接收者离线，消息会保存到数据库，下次上线后拉取历史消息时可看到

### 6. **已读状态管理**
- 📌 消息默认状态为"未读"（`isRead=0`）
- 📌 前端应在用户查看聊天界面时调用"标记已读"接口
- 📌 建议在进入聊天页面时自动调用 `POST /api/chat/private/read/{senderId}`

### 7. **错误处理**
| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 403 | 无绑定关系 | 提示用户"您与该用户没有绑定关系" |
| 400 | 长辈模式限制 | 提示用户"长辈模式仅支持语音和快捷短语" |
| 500 | 服务器错误 | 提示用户"发送失败，请重试" |

### 8. **性能优化建议**
- 💡 首次进入聊天页面时，建议只加载最近 50 条消息
- 💡 使用分页加载更早的历史消息（后续版本支持）
- 💡 定期清理本地缓存的旧消息，避免内存占用过大

---

## 📱 前端集成示例（Kotlin）

### 1. 定义数据模型

```kotlin
data class PrivateChatMessage(
    val id: Long,
    val senderId: Long,
    val receiverId: Long,
    val messageType: Int,  // 1文字 2语音 3图片
    val content: String,
    val isRead: Int,       // 0未读 1已读
    val createdAt: String
)

data class ChatMessageRequest(
    val messageType: Int,
    val content: String
)
```

### 2. API 服务接口

```kotlin
interface ChatApiService {
    
    @GET("/api/chat/private/history/{targetUserId}")
    suspend fun getPrivateChatHistory(
        @Path("targetUserId") targetUserId: Long
    ): Result<List<PrivateChatMessage>>
    
    @POST("/api/chat/private/send/{receiverId}")
    suspend fun sendPrivateMessage(
        @Path("receiverId") receiverId: Long,
        @Body request: ChatMessageRequest
    ): Result<Unit>
    
    @POST("/api/chat/private/read/{senderId}")
    suspend fun markAsRead(
        @Path("senderId") senderId: Long
    ): Result<Int>
    
    @GET("/api/chat/private/unread")
    suspend fun getUnreadCount(): Result<Map<String, Int>>
}
```

### 3. ViewModel 使用示例

```kotlin
class PrivateChatViewModel(
    private val apiService: ChatApiService
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<PrivateChatMessage>>(emptyList())
    val messages: StateFlow<List<PrivateChatMessage>> = _messages.asStateFlow()
    
    // 加载聊天历史
    fun loadChatHistory(targetUserId: Long) {
        viewModelScope.launch {
            val result = apiService.getPrivateChatHistory(targetUserId)
            if (result.isSuccess) {
                _messages.value = result.getOrNull() ?: emptyList()
            }
        }
    }
    
    // 发送消息
    fun sendMessage(receiverId: Long, messageType: Int, content: String) {
        viewModelScope.launch {
            val request = ChatMessageRequest(messageType, content)
            val result = apiService.sendPrivateMessage(receiverId, request)
            if (result.isFailure) {
                // 显示错误提示
                showError(result.exceptionOrNull()?.message)
            }
        }
    }
    
    // 标记已读
    fun markMessagesAsRead(senderId: Long) {
        viewModelScope.launch {
            apiService.markAsRead(senderId)
        }
    }
}
```

### 4. WebSocket 监听

```kotlin
class ChatWebSocketManager {
    
    fun connect(token: String) {
        val wsUrl = "ws://localhost:8080/ws/agent?token=$token"
        webSocket = WebSocket(wsUrl)
        
        webSocket.onMessage { message ->
            val json = JSONObject(message)
            if (json.getString("type") == "PRIVATE_MESSAGE") {
                // 收到新消息
                val newMessage = PrivateChatMessage(
                    id = 0,  // WebSocket 消息没有 ID
                    senderId = json.getLong("senderId"),
                    receiverId = currentUserId,
                    messageType = json.getInt("messageType"),
                    content = json.getString("content"),
                    isRead = 0,
                    createdAt = json.getString("createdAt")
                )
                
                // 添加到消息列表
                _messages.value = _messages.value + newMessage
                
                // 播放提示音
                playNotificationSound()
            }
        }
    }
}
```

---

## 🧪 测试建议

### 测试场景 1: 正常发送文字消息
```kotlin
// 前提：用户1和用户2已建立亲情绑定关系
apiService.sendPrivateMessage(
    receiverId = 2,
    request = ChatMessageRequest(messageType = 1, content = "你好")
)
// 预期：返回成功，用户2通过 WebSocket 收到消息
```

### 测试场景 2: 长辈模式发送文字（应失败）
```kotlin
// 前提：用户1是长辈模式（guardMode=1）
apiService.sendPrivateMessage(
    receiverId = 2,
    request = ChatMessageRequest(messageType = 1, content = "你好")
)
// 预期：返回 400 错误 "长辈模式仅支持语音和快捷短语"
```

### 测试场景 3: 长辈模式发送语音（应成功）
```kotlin
// 前提：用户1是长辈模式
apiService.sendPrivateMessage(
    receiverId = 2,
    request = ChatMessageRequest(messageType = 2, content = "https://example.com/audio.mp3")
)
// 预期：返回成功
```

### 测试场景 4: 未绑定用户聊天（应失败）
```kotlin
// 前提：用户1和用户3没有绑定关系
apiService.sendPrivateMessage(
    receiverId = 3,
    request = ChatMessageRequest(messageType = 1, content = "你好")
)
// 预期：返回 403 错误 "您与该用户没有绑定关系，无法发送消息"
```

---

## 📞 技术支持

如有问题，请联系后端开发团队。

**最后更新**: 2026-04-09
