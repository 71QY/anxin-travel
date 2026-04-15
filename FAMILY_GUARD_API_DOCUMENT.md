# 亲情守护功能 - 前端接口文档

## 📋 目录
- [一、认证相关](#一认证相关)
- [二、亲情绑定相关](#二亲情绑定相关)
- [三、代叫车相关](#三代叫车相关)
- [四、订单群聊相关](#四订单群聊相关)
- [五、注意事项](#五注意事项)

---

## 一、认证相关

### 1.1 登录接口(已增强)
**接口**: `POST /api/auth/codeLogin` 或 `POST /api/auth/passwordLogin`

**返回数据增强**:
```json
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 123,
    "isGuarded": 1,      // 【新增】是否被守护 0否 1是
    "guardMode": 1        // 【新增】0普通模式 1长辈精简模式
  }
}
```

**前端处理逻辑**:
```javascript
// 登录成功后
if (response.data.guardMode === 1) {
  // 进入长辈精简模式UI
  switchToElderMode();
} else {
  // 普通模式UI
  switchToNormalMode();
}
```

---

## 二、亲情绑定相关

### 2.1 添加长辈绑定(亲友端)
**接口**: `POST /api/guard/add`

**请求体**:
```json
{
  "elderPhone": "13812345678",    // 长辈手机号(必填)
  "elderName": "张三",             // 长辈姓名(必填)
  "guardianName": "李四",          // 亲友姓名(必填)
  "guardianIdCard": "110101199001011234"  // 亲友身份证号(必填,18位)
}
```

**返回**:
```json
{
  "code": 200,
  "message": "绑定成功,请告知长辈使用该手机号登录"
}
```

**校验规则**:
- 手机号: 11位,正则 `^1[3-9]\d{9}$`
- 身份证: 18位,正则 `^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$`
- 一个亲友最多绑定4个长辈
- 同一手机号不能被多个亲友绑定

---

### 2.2 查询我的长辈列表(亲友端)
**接口**: `GET /api/guard/myElders`

**返回**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "elderPhone": "13812345678",
      "elderName": "张三",
      "status": 1,  // 0待激活 1已绑定
      "bindTime": "2026-04-08T10:00:00"
    }
  ]
}
```

---

### 2.3 查询我的亲友列表(长辈端)
**接口**: `GET /api/guard/myGuardians`

**返回**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "guardianName": "李*",           // 脱敏处理
      "guardianPhone": "138****5678",  // 脱敏处理
      "status": 1
    }
  ]
}
```

---

### 2.4 一键解绑所有亲友(长辈端)
**接口**: `POST /api/guard/unbindAll`

**返回**:
```json
{
  "code": 200,
  "message": "已解绑所有亲友,恢复普通模式"
}
```

**副作用**: 
- 用户`is_guarded`和`guard_mode`重置为0
- 下次登录时恢复普通模式UI

---

### 2.5 单条解绑(亲友端)
**接口**: `POST /api/guard/unbindOne/{guardId}`

**参数**: guardId - 绑定记录ID(路径参数)

**返回**:
```json
{
  "code": 200,
  "message": "解绑成功"
}
```

---

## 三、代叫车相关

### 3.1 代叫车下单(亲友端)
**接口**: `POST /api/guard/proxyOrder`

**请求体**:
```json
{
  "elderId": 456,              // 长辈用户ID(必填)
  "startLat": 39.9042,         // 起点纬度
  "startLng": 116.4074,        // 起点经度
  "destLat": 31.2304,          // 终点纬度
  "destLng": 121.4737,         // 终点经度
  "destAddress": "上海市浦东新区xxx"  // 终点地址
}
```

**返回**:
```json
{
  "code": 200,
  "message": "代叫车成功,已通知长辈"
}
```

**后端自动处理**:
1. 创建订单(order_info),设置`proxy_user_id`和`elder_user_id`
2. 创建订单群聊系统消息
3. WebSocket推送给长辈:`{"type":"NEW_ORDER","orderId":123,...}`

---

### 3.2 查询代叫订单列表(亲友端)
**接口**: `GET /api/guard/proxyOrders`

**返回**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 789,
      "orderNo": "AX1234567890",
      "userId": 456,           // 实际乘车人(长辈)
      "proxyUserId": 123,      // 代叫车人(亲友)
      "destAddress": "上海市xxx",
      "status": 1,             // 0待接单 1进行中 2已完成
      "createTime": "2026-04-08T10:00:00"
    }
  ]
}
```

---

### 3.3 呼叫司机
**接口**: `GET /api/guard/callDriver/{orderId}`

**参数**: orderId - 订单ID(路径参数)

**返回**:
```json
{
  "code": 200,
  "data": {
    "driverPhone": "138****5678",  // 模拟数据,待司机模块完成后替换
    "driverName": "张*傅"
  }
}
```

---

### 3.4 呼叫亲友(长辈端)
**接口**: `GET /api/guard/callGuardian`

**返回**:
```json
{
  "code": 200,
  "data": {
    "guardianPhone": "13812345678",
    "guardianName": "李*"
  }
}
```

**说明**: 返回第一个绑定的亲友信息

---

## 四、订单群聊相关

### 4.1 获取订单群聊历史
**接口**: `GET /api/chat/order/{orderId}`

**参数**: orderId - 订单ID(路径参数)

**返回**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "orderId": 789,
      "senderId": 123,
      "senderType": 2,         // 1长辈 2亲友 3司机
      "messageType": 1,        // 1文字 2语音 3快捷短语
      "content": "亲友李四为您代叫车辆,目的地:上海市xxx",
      "createdAt": "2026-04-08T10:00:00"
    },
    {
      "id": 2,
      "orderId": 789,
      "senderId": 456,
      "senderType": 1,
      "messageType": 2,        // 语音消息
      "content": "base64音频数据或URL",
      "createdAt": "2026-04-08T10:05:00"
    }
  ]
}
```

**权限校验**:
- 只有订单参与者(长辈/亲友/司机)才能查看
- 长辈模式下,聊天记录正常展示

---

### 4.2 发送聊天消息
**接口**: `POST /api/chat/order/{orderId}`

**参数**: orderId - 订单ID(路径参数)

**请求体**:
```json
{
  "messageType": 2,            // 1文字 2语音 3快捷短语
  "content": "我到了"           // 文字内容或语音base64
}
```

**返回**:
```json
{
  "code": 200,
  "message": "发送成功"
}
```

**权限限制**:
- **长辈模式用户**: 只能发送`messageType=2`(语音)或`3`(快捷短语),禁止`messageType=1`(文字)
- **订单状态校验**: 只有订单状态为0/1/2(待接单/进行中/已接单)才能发消息
- 订单结束后(status=3/4),禁止发送消息

**WebSocket推送**:
消息发送后,自动推送给其他参与者:
```json
{
  "type": "CHAT_MESSAGE",
  "orderId": 789,
  "senderId": 456,
  "senderType": 1,
  "messageType": 2,
  "content": "我到了",
  "createdAt": "2026-04-08T10:05:00"
}
```

---

### 4.3 语音转文字
**接口**: `POST /api/chat/voiceToText`

**请求体**: 
```
"base64音频数据"
```

**返回**:
```json
{
  "code": 200,
  "data": {
    "text": "我到了"  // 当前为模拟数据,后续接入通义千问API
  }
}
```

---

### 4.4 文字转语音
**接口**: `GET /api/chat/textToSpeech?text=我到了`

**返回**:
```json
{
  "code": 200,
  "data": {
    "audioUrl": "https://example.com/audio.mp3"  // 当前为模拟数据,后续接入阿里云TTS
  }
}
```

---

## 五、注意事项

### 5.1 长辈模式UI切换
```javascript
// 登录成功后判断
const { isGuarded, guardMode } = response.data;

if (guardMode === 1) {
  // 长辈精简模式
  - 隐藏打车入口
  - 只显示:当前订单、亲友列表、一键呼叫
  - 聊天界面:隐藏输入框,只显示语音按钮+快捷短语
} else {
  // 普通模式
  - 完整打车功能
}
```

### 5.2 WebSocket消息类型
前端需监听以下消息类型:
```javascript
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  switch(data.type) {
    case 'NEW_ORDER':
      // 亲友代叫车,推送给长辈
      showNewOrderNotification(data);
      break;
      
    case 'CHAT_MESSAGE':
      // 订单群聊消息
      addChatMessage(data);
      // 根据senderType显示不同标识
      if (data.senderType === 1) {
        showLabel('长辈');
      } else if (data.senderType === 2) {
        showLabel('亲友');
      } else if (data.senderType === 3) {
        showLabel('司机');
      }
      break;
  }
};
```

### 5.3 聊天界面角色标识
```javascript
// 根据senderType显示不同颜色/标签
function renderMessage(msg) {
  let label = '';
  let color = '';
  
  if (msg.senderType === 1) {
    label = '长辈';
    color = '#FF6B6B';  // 红色
  } else if (msg.senderType === 2) {
    label = '亲友';
    color = '#4ECDC4';  // 青色
  } else if (msg.senderType === 3) {
    label = '司机';
    color = '#45B7D1';  // 蓝色
  }
  
  return `<div style="color:${color}">[${label}] ${msg.content}</div>`;
}
```

### 5.4 长辈聊天权限收紧
```javascript
// 长辈模式下,隐藏文字输入框
if (user.guardMode === 1) {
  hideTextInput();
  showVoiceButton();
  showQuickPhrases(['我到了', '我在原地等你', '谢谢']);
}
```

### 5.5 订单结束后隐藏聊天入口
```javascript
// 查询订单详情时判断
if (order.status >= 3) {
  hideChatEntry();  // 隐藏聊天按钮
} else {
  showChatEntry();  // 显示聊天按钮
}
```

### 5.6 错误码说明
| 错误码 | 说明 |
|--------|------|
| 403 | 长辈模式无法访问该功能 |
| 400 | 绑定人数超过上限4人 |
| 400 | 该手机号已被其他亲友绑定 |
| 400 | 您未绑定该长辈,无法代叫车 |
| 400 | 订单已结束,无法发送消息 |
| 400 | 长辈模式仅支持语音和快捷短语 |

---

## 六、测试流程建议

### 场景1: 亲友绑定长辈
1. 亲友登录 → 我的 → 亲情守护 → 添加长辈
2. 填写表单(姓名+身份证+长辈手机号)
3. 提示"绑定成功,请告知长辈使用该手机号登录"

### 场景2: 长辈登录激活
1. 长辈使用被绑定的手机号登录
2. 登录返回`isGuarded=1, guardMode=1`
3. 前端切换到长辈精简模式UI

### 场景3: 亲友代叫车
1. 亲友选择长辈 → 输入起点终点 → 提交代叫
2. 长辈收到WebSocket推送:`{"type":"NEW_ORDER",...}`
3. 长辈查看当前订单,看到亲友代叫的订单

### 场景4: 订单群聊
1. 订单进行中,长辈点击"联系"
2. 进入群聊界面,显示角色标识
3. 长辈只能发语音/快捷短语,不能打字
4. 亲友/司机可以正常发消息
5. 消息实时推送到三方

### 场景5: 长辈解绑
1. 长辈 → 我的 → 亲情守护 → 一键解绑
2. 提示"已解绑所有亲友,恢复普通模式"
3. 退出重新登录,返回普通模式UI

---

## 七、数据库执行脚本

**文件位置**: `database/alter_family_guard.sql`

**执行命令**:
```bash
mysql -u root -p < database/alter_family_guard.sql
```

**验证执行结果**:
```sql
-- 检查user表字段
DESC user;

-- 检查order_info表字段
DESC order_info;

-- 检查新表
SHOW TABLES LIKE 'family_guard';
SHOW TABLES LIKE 'order_chat';
```

---

## 八、后端已完成清单

✅ 数据库改造(4张表)  
✅ 实体类(User/FamilyGuard/OrderInfo/OrderChat)  
✅ Mapper层(FamilyGuardMapper/OrderChatMapper/OrderMapper)  
✅ Service层(FamilyGuardServiceImpl/OrderChatServiceImpl)  
✅ Controller层(FamilyGuardController/OrderChatController)  
✅ 拦截器(GuardModeInterceptor白名单机制)  
✅ 登录增强(批量激活逻辑)  
✅ WebSocket推送(sendMessageToUser方法)  
✅ 编译通过(BUILD SUCCESS)  

---

**文档版本**: v1.0  
**更新时间**: 2026-04-08  
**联系人**: 后端开发团队
