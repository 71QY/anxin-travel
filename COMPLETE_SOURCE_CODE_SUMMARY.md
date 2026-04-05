# 智能出行助手 - 完整项目源码文档

## 📋 项目概览

**项目名称**: 智能出行助手 (Anxin Travel)  
**技术栈**: Spring Boot 3.1.5 + JDK 17 + MySQL 8.0 + Redis 7.x  
**架构模式**: 分层架构 (Controller → Service → Mapper)  
**核心功能**: AI智能对话、POI搜索、订单管理、图片识别、语音交互  

---

## 🏗️ 项目结构

```
src/main/java/com/anxin/travel/
├── TravelApplication.java                    # 启动类
├── agent/                                     # AI智能体模块
│   ├── ai/
│   │   └── TongyiQianwenClient.java          # 通义千问API客户端
│   ├── config/
│   │   ├── AuthHandshakeInterceptor.java     # WebSocket认证拦截器
│   │   ├── TLSSocketFactory.java             # TLS Socket工厂
│   │   └── WebSocketConfig.java              # WebSocket配置
│   ├── controller/
│   │   ├── AgentController.java              # REST API控制器
│   │   ├── AgentWebSocketController.java     # WebSocket控制器
│   │   └── NativeWebSocket.java              # 原生WebSocket端点
│   ├── dto/
│   │   ├── AgentResponse.java                # AI响应DTO
│   │   └── ImageRecognitionRequest.java      # 图片识别请求DTO
│   ├── model/
│   │   ├── AgentIntent.java                  # 意图识别模型
│   │   ├── AgentMessage.java                 # AI消息模型
│   │   ├── AgentState.java                   # 会话状态枚举
│   │   ├── CandidateDestination.java         # 候选目的地模型
│   │   ├── ChatMessage.java                  # 聊天消息模型
│   │   └── UserMessage.java                  # 用户消息模型
│   ├── service/
│   │   ├── AgentService.java                 # 核心业务逻辑（⭐重点）
│   │   ├── DialectTranslationService.java    # 方言翻译服务
│   │   ├── ImageRecognitionService.java      # 图片识别服务
│   │   ├── MemoryService.java                # 会话记忆服务
│   │   ├── QueryClassifier.java              # 查询分类器
│   │   ├── QueryRewriter.java                # 查询重写器
│   │   ├── QueryType.java                    # 查询类型枚举
│   │   ├── SearchStrategyEngine.java         # 搜索策略引擎（⭐新增）
│   │   └── ToolExecutorService.java          # 工具执行服务
│   └── tool/
│       ├── MapTool.java                      # 地图工具
│       ├── RouteTool.java                    # 路线工具
│       ├── Tool.java                         # 工具接口
│       └── WeatherTool.java                  # 天气工具
├── common/                                    # 通用模块
│   ├── result/
│   │   ├── PageResult.java                   # 分页结果封装
│   │   └── Result.java                       # 统一响应封装
│   └── util/
│       ├── JwtUtil.java                      # JWT工具类
│       ├── RedisUtil.java                    # Redis工具类
│       ├── SpringContextUtil.java            # Spring上下文工具
│       └── UserContext.java                  # 用户上下文工具
├── config/                                    # 配置模块
│   ├── AuthInterceptor.java                  # 认证拦截器
│   ├── GlobalExceptionHandler.java           # 全局异常处理
│   ├── MybatisPlusConfig.java                # MyBatis-Plus配置
│   ├── PerformanceMonitorAspect.java         # 性能监控切面
│   ├── ThreadPoolConfig.java                 # 线程池配置
│   ├── WebConfig.java                        # Web配置
│   └── WebSocketNativeConfig.java            # 原生WebSocket配置
└── module/                                    # 业务模块
    ├── auth/                                  # 认证模块
    │   ├── controller/
    │   │   └── AuthController.java           # 认证控制器
    │   ├── dto/
    │   │   └── LoginRequest.java             # 登录请求DTO
    │   └── service/
    │       ├── AuthService.java              # 认证服务接口
    │       ├── SmsService.java               # 短信服务接口
    │       └── impl/
    │           ├── AuthServiceImpl.java      # 认证服务实现
    │           └── SmsServiceImpl.java       # 短信服务实现
    ├── map/                                   # 地图模块
    │   ├── client/
    │   │   ├── AmapClient.java               # 高德地图客户端（⭐重点）
    │   │   └── TencentMapClient.java         # 腾讯地图客户端
    │   ├── controller/
    │   │   └── MapController.java            # 地图控制器
    │   └── dto/
    │       ├── PoiDTO.java                   # POI数据传输对象
    │       ├── PoiResult.java                # POI搜索结果
    │       ├── ReverseGeocodeResponse.java   # 逆地理编码响应
    │       └── RouteResult.java              # 路线规划结果
    ├── order/                                 # 订单模块
    │   ├── controller/
    │   │   └── OrderController.java          # 订单控制器
    │   ├── dto/
    │   │   ├── CreateOrderRequest.java       # 创建订单请求
    │   │   └── OrderVO.java                  # 订单视图对象
    │   ├── entity/
    │   │   └── OrderInfo.java                # 订单实体
    │   ├── mapper/
    │   │   └── OrderMapper.java              # 订单Mapper
    │   └── service/
    │       ├── OrderService.java             # 订单服务接口
    │       └── impl/
    │           └── OrderServiceImpl.java     # 订单服务实现（⭐重点）
    └── user/                                  # 用户模块
        ├── controller/
        │   └── UserController.java           # 用户控制器
        ├── dto/
        │   ├── ChangePasswordRequest.java    # 修改密码请求
        │   ├── EmergencyContactRequest.java  # 紧急联系人请求
        │   ├── RealnameRequest.java          # 实名认证请求
        │   ├── UserVO.java                   # 用户视图对象
        │   └── LoginRequest.java             # 登录请求
        ├── entity/
        │   ├── User.java                     # 用户实体
        │   └── EmergencyContact.java         # 紧急联系人实体
        ├── mapper/
        │   ├── UserMapper.java               # 用户Mapper
        │   └── EmergencyContactMapper.java   # 紧急联系人Mapper
        └── service/
            ├── UserService.java              # 用户服务接口
            └── impl/
                └── UserServiceImpl.java      # 用户服务实现
```

---

## 🔑 核心源码详解

### 1. 启动类

#### **TravelApplication.java**
```java
@SpringBootApplication
@MapperScan("com.anxin.travel.module.*.mapper")
public class TravelApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelApplication.class, args);
    }
}
```
**功能**: Spring Boot应用入口，扫描所有Mapper接口

---

### 2. AI智能体模块（⭐核心）

#### **2.1 AgentService.java** - 核心业务逻辑（1796行）

**职责**: 
- 意图识别与分发
- POI搜索与排序
- 订单创建
- 图片识别处理

**关键方法**:

```java
// 1. 处理用户请求（主入口）
public AgentResponse processUserRequest(String sessionId, Long userId, String message, Double lat, Double lng)

// 2. AI意图识别
private AgentIntent parseIntentWithAI(String sessionId, String message)

// 3. 执行意图
private ExecutionResult executeIntentWithFinalType(String sessionId, Long userId, 
                                                   String type, String keyword, 
                                                   Double lat, Double lng)

// 4. 搜索POI（含评分算法）
private List<PoiDTO> searchDestinations(String sessionId, Long userId, String keyword, 
                                        Double lat, Double lng)

// 5. 关键词清洗（新增口语前缀去除）
private String cleanKeyword(String keyword)

// 6. POI评分算法（优化版：匹配等级+分层加权）
private double computeRelevanceScore(PoiDTO poi, String keyword, Double userLat, Double userLng)

// 7. POI过滤（增强黑名单）
private List<PoiDTO> filterPoiList(List<PoiDTO> poiList, String keyword)

// 8. 图片识别处理
public AgentResponse processImageRecognition(String sessionId, Long userId, 
                                             String imageBase64, Double lat, Double lng)
```

**核心优化点**:

1. **关键词清洗增强**（第1165-1214行）
   - 去除口语前缀："我想去"、"带我去"等
   - 去除括号内容："北京大学(东门)" → "北京大学"
   - 提取核心词："韩山师范学院东区" → "韩山师范学院"

2. **POI评分算法优化**（第984-1063行）
   ```java
   // 匹配等级机制
   等级5: 完全匹配 (100分) - "故宫" → "故宫"
   等级4: 开头短后缀 (90分) - "故宫" → "故宫博物院"
   等级3: 开头长后缀 (70-80分)
   等级2: 中间包含 (50-60分)
   等级1: 地址包含 (20-40分)
   
   // 分层加权
   高等级: 名称95% + 距离5%
   中等级: 名称70% + 距离20% + 类型10%
   低等级: 名称50% + 距离30% + 类型20%
   ```

3. **POI过滤器增强**（第1676-1723行）
   - 黑名单扩展：女装、男装、奶茶等
   - 针对"韩师"的严格过滤：只保留学校类POI

4. **自动下单防护**（第148-150行）
   ```java
   intent.setAutoOrder(false); // 强制禁用
   ```

---

#### **2.2 TongyiQianwenClient.java** - 通义千问API客户端（184行）

**职责**: 调用阿里云通义千问API进行意图识别

**关键方法**:
```java
// 1. 解析意图（带上下文）
public AgentIntent parseIntentWithContext(List<ChatMessage> history, String currentMessage)

// 2. 解析意图（无上下文）
public AgentIntent parseIntent(String message)

// 3. 构建系统Prompt（强化autoOrder规则）
private String buildSystemPrompt()
```

**Prompt核心规则**:
```
【重要规则】autoOrder 必须始终为 false！绝对不允许自动下单！
【禁止】SEARCH 类型绝对不能设置 autoOrder=true
【前提】必须先有 CONFIRM 步骤，不能直接从 SEARCH 跳到 ORDER
```

---

#### **2.3 SearchStrategyEngine.java** - 搜索策略引擎（新增）

**职责**: 多地图源优先级搜索（腾讯地图优先 → 高德地图降级）

**关键方法**:
```java
public List<PoiDTO> search(String keyword, Double lat, Double lng)
```

---

#### **2.4 MemoryService.java** - 会话记忆服务

**职责**: 管理Redis中的会话数据

**关键方法**:
```java
// 保存/获取历史消息
public void saveMessage(String sessionId, String role, String content)
public List<ChatMessage> getHistory(String sessionId)

// 保存/获取候选POI
public void saveCandidates(String sessionId, List<PoiDTO> candidates)
public List<PoiDTO> getCandidates(String sessionId)

// 保存/获取用户位置
public void saveLocation(String sessionId, Double lat, Double lng)
```

---

#### **2.5 ImageRecognitionService.java** - 图片识别服务

**职责**: OCR文字提取 + AI语义理解

**关键方法**:
```java
public String extractTextFromImage(String imageBase64)
```

**流程**:
1. Base64图片 → 通义千问VL模型
2. 提取文字（OCR）
3. 返回结构化文本

---

### 3. 地图模块

#### **3.1 AmapClient.java** - 高德地图客户端（⭐重点，1500+行）

**职责**: 调用高德地图API

**关键方法**:
```java
// 1. POI搜索（支持城市精确模式）
public List<PoiDTO> searchPoi(String keyword, Double lat, Double lng)

// 2. 路线规划（驾车）
public RouteResult getRoute(String origin, String destination, String mode)

// 3. 逆地理编码
public ReverseGeocodeResponse reverseGeocode(Double lat, Double lng)

// 4. IP定位
public double[] getLocationByIp()
```

**核心优化**:
- 坐标格式修复：`lng,lat`（经度在前）
- 城市判断逻辑：地标/机构判断
- 超时控制：HttpClient5配置

---

#### **3.2 MapController.java** - 地图控制器

**关键接口**:
```java
// 1. 获取POI详情和路线
@GetMapping("/poi/detail")
public Result<Map<String, Object>> getPoiDetailAndRoute(...)

// 2. 地图点击下单
@PostMapping("/order/create-from-map")
public Result<OrderVO> createOrderFromMapClick(...)
```

---

### 4. 订单模块

#### **4.1 OrderServiceImpl.java** - 订单服务实现（⭐重点）

**职责**: 订单创建、计价、状态管理

**关键方法**:
```java
public OrderVO createOrder(Long userId, CreateOrderRequest request)
```

**计价逻辑**（仿滴滴阶梯计价）:
```java
// 1. 调用高德API获取真实距离
RouteResult route = amapClient.getRoute(origin, destination, "driving");

// 2. 阶梯计价
if (distance <= 3000) {
    price = 10.0; // 起步价
} else if (distance <= 10000) {
    price = 10.0 + (distance - 3000) / 1000.0 * 2.5;
} else {
    price = 10.0 + 7000 / 1000.0 * 2.5 + (distance - 10000) / 1000.0 * 3.5;
}
```

---

### 5. 用户模块

#### **5.1 UserServiceImpl.java** - 用户服务实现

**关键功能**:
```java
// 1. 头像上传
public String uploadAvatar(Long userId, MultipartFile avatarFile)

// 2. 紧急联系人管理
public void addEmergencyContact(Long userId, EmergencyContactRequest request)
public List<EmergencyContact> getEmergencyContacts(Long userId)

// 3. 实名认证
public void realname(Long userId, String realName, String idCard)
```

**头像上传字段**: `avatar`（不是avatarFile）

---

#### **5.2 UserController.java** - 用户控制器

**关键接口**:
```java
// 1. 获取用户信息
@GetMapping("/profile")
public Result<UserVO> getProfile()

// 2. 上传头像
@PostMapping("/avatar")
public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile avatarFile)

// 3. 获取头像
@GetMapping("/avatar/{filename}")
public void getAvatar(@PathVariable String filename, HttpServletResponse response)
```

---

### 6. 认证模块

#### **6.1 AuthServiceImpl.java** - 认证服务实现

**关键功能**:
```java
// 1. 手机号登录（短信验证码）
public String loginByPhone(String phone, String code)

// 2. 发送短信验证码
public void sendSmsCode(String phone)
```

---

### 7. 配置模块

#### **7.1 ThreadPoolConfig.java** - 线程池配置

**线程池定义**:
```java
@Bean("businessExecutor")
public Executor businessExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("business-");
    return executor;
}
```

**用途**: 并行计算POI路线（提升性能）

---

#### **7.2 GlobalExceptionHandler.java** - 全局异常处理

**统一错误响应**:
```java
@ExceptionHandler(Exception.class)
public Result<Void> handleException(Exception e) {
    log.error("系统异常", e);
    return Result.error("系统繁忙，请稍后重试");
}
```

---

#### **7.3 AuthInterceptor.java** - 认证拦截器

**JWT验证**:
```java
public boolean preHandle(HttpServletRequest request, ...) {
    String token = request.getHeader("Authorization");
    if (token != null && token.startsWith("Bearer ")) {
        token = token.substring(7);
        if (jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.getUserIdFromToken(token);
            UserContext.setUserId(userId);
            return true;
        }
    }
    return false;
}
```

---

### 8. 通用模块

#### **8.1 Result.java** - 统一响应封装

```java
@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    
    public static <T> Result<T> success(T data) { ... }
    public static <T> Result<T> error(String message) { ... }
}
```

---

#### **8.2 JwtUtil.java** - JWT工具类

```java
public String generateToken(Long userId) { ... }
public Long getUserIdFromToken(String token) { ... }
public boolean validateToken(String token) { ... }
```

---

#### **8.3 RedisUtil.java** - Redis工具类

```java
public void set(String key, Object value, long expire) { ... }
public Object get(String key) { ... }
public void delete(String key) { ... }
```

---

## 🗄️ 数据库设计

### 核心表结构

#### **1. user（用户表）**
```sql
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `phone` VARCHAR(20) UNIQUE NOT NULL COMMENT '手机号',
  `password` VARCHAR(255) COMMENT '密码（BCrypt加密）',
  `nickname` VARCHAR(50) COMMENT '昵称',
  `avatar` VARCHAR(255) COMMENT '头像URL',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `id_card` VARCHAR(18) COMMENT '身份证号',
  `verified` TINYINT DEFAULT 0 COMMENT '是否实名认证',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### **2. order_info（订单表）**
```sql
CREATE TABLE `order_info` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_no` VARCHAR(64) UNIQUE NOT NULL COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `driver_id` BIGINT COMMENT '司机ID',
  `dest_lat` DOUBLE COMMENT '目的地纬度',
  `dest_lng` DOUBLE COMMENT '目的地经度',
  `dest_address` VARCHAR(255) COMMENT '目的地地址',
  `status` INT DEFAULT 0 COMMENT '订单状态',
  `estimate_price` DECIMAL(10,2) COMMENT '预估价格',
  `actual_price` DECIMAL(10,2) COMMENT '实际价格',
  `platform_used` VARCHAR(50) COMMENT '使用的平台',
  `platform_order_id` VARCHAR(100) COMMENT '第三方订单ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### **3. emergency_contact（紧急联系人表）**
```sql
CREATE TABLE `emergency_contact` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `name` VARCHAR(50) NOT NULL COMMENT '联系人姓名',
  `phone` VARCHAR(20) NOT NULL COMMENT '联系人电话',
  `relationship` VARCHAR(50) COMMENT '关系',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🔌 API接口清单

### 1. 认证接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 发送验证码 | POST | `/api/auth/sms/send` | 发送短信验证码 |
| 手机号登录 | POST | `/api/auth/login/phone` | 手机号+验证码登录 |

---

### 2. 用户接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取用户信息 | GET | `/api/user/profile` | 获取当前用户信息 |
| 更新用户信息 | PUT | `/api/user/profile` | 更新昵称等 |
| 上传头像 | POST | `/api/user/avatar` | 参数名：`avatar` |
| 获取头像 | GET | `/api/user/avatar/{filename}` | 返回图片流 |
| 添加紧急联系人 | POST | `/api/user/emergency-contact` | 添加联系人 |
| 获取紧急联系人 | GET | `/api/user/emergency-contact` | 获取列表 |
| 删除紧急联系人 | DELETE | `/api/user/emergency-contact/{id}` | 删除联系人 |
| 实名认证 | POST | `/api/user/realname` | 提交实名信息 |
| 修改密码 | PUT | `/api/user/password` | 手机号+验证码改密 |

---

### 3. AI智能体接口（REST）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 发送消息 | POST | `/api/agent/chat` | 文本对话 |
| 图片识别 | POST | `/api/agent/image` | 上传图片识别 |
| 确认选择 | POST | `/api/agent/confirm` | 确认POI并下单 |

**请求示例**（发送消息）:
```json
POST /api/agent/chat
{
  "sessionId": "uuid-xxx",
  "userId": 7,
  "message": "我想去故宫",
  "lat": 23.653491,
  "lng": 116.676126
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "type": "search",
    "message": "为你找到以下地点",
    "places": [
      {
        "id": "uuid-1",
        "name": "故宫博物院",
        "address": "北京市东城区景山前街4号",
        "lat": 39.916345,
        "lng": 116.397155,
        "distance": 1200.5,
        "duration": 300,
        "price": 15.5,
        "score": 95.0
      }
    ]
  }
}
```

---

### 4. AI智能体接口（WebSocket）

**连接地址**: `ws://localhost:8080/ws/agent?sessionId={sessionId}&token={token}`

**消息格式**:
```json
// 客户端发送
{
  "type": "text",
  "content": "我想去故宫",
  "lat": 23.653491,
  "lng": 116.676126
}

// 服务端推送
{
  "type": "search_result",
  "message": "为你找到以下地点",
  "places": [...]
}
```

---

### 5. 地图接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取POI详情 | GET | `/api/map/poi/detail` | 获取POI+路线 |
| 地图点击下单 | POST | `/api/map/order/create-from-map` | 从地图创建订单 |

---

### 6. 订单接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建订单 | POST | `/api/order/create` | 创建新订单 |
| 获取订单详情 | GET | `/api/order/{orderNo}` | 查询订单 |
| 取消订单 | PUT | `/api/order/{orderNo}/cancel` | 取消订单 |
| 获取用户订单列表 | GET | `/api/order/list` | 分页查询 |

---

## 🎯 核心业务流程

### 1. 用户对话流程

```
用户输入 → AI意图识别 → 意图分发
    ↓
[SEARCH] → 关键词清洗 → POI搜索 → 评分排序 → 返回候选列表 → WAIT_CONFIRM
    ↓
[CONFIRM] → 用户选择 → 创建订单 → ORDER_CREATED
    ↓
[ORDER] → 直接创建订单（需明确指令）
    ↓
[CHAT] → AI对话回复
```

---

### 2. 图片识别流程

```
用户上传Base64图片 → OCR文字提取 → AI语义理解
    ↓
提取地名 → POI搜索 → 评分排序 → 返回候选列表
```

---

### 3. 订单创建流程

```
用户确认POI → 获取起点坐标 → 调用高德API规划路线
    ↓
获取真实距离 → 阶梯计价 → 创建订单记录 → 返回订单信息
```

**计价公式**:
- 0-3km: 10元（起步价）
- 3-10km: 10 + (距离-3) × 2.5元/km
- >10km: 10 + 7×2.5 + (距离-10) × 3.5元/km

---

## 🔧 关键技术点

### 1. 关键词清洗（5步流程）

```java
// 第1步：去除口语前缀
"我想去故宫" → "故宫"

// 第2步：去除括号内容
"北京大学(东门)" → "北京大学"

// 第3步：去除冗余后缀
"故宫的位置在哪" → "故宫"

// 第4步：提取核心词
"韩山师范学院东区" → "韩山师范学院"

// 第5步：最终清理
去除首尾空格
```

---

### 2. POI评分算法（匹配等级+分层加权）

**匹配等级**:
- 等级5: 完全匹配 (100分)
- 等级4: 开头短后缀 (90分)
- 等级3: 开头长后缀 (70-80分)
- 等级2: 中间包含 (50-60分)
- 等级1: 地址包含 (20-40分)

**分层加权**:
```java
if (matchLevel >= 4) {
    finalScore = 0.95 * nameMatchScore + 0.05 * distanceScore;
} else if (matchLevel >= 2) {
    finalScore = 0.70 * nameMatchScore + 0.20 * distanceScore + 0.10 * typeMatchScore;
} else {
    finalScore = 0.50 * nameMatchScore + 0.30 * distanceScore + 0.20 * typeMatchScore;
}
```

---

### 3. 自动下单防护（三层机制）

1. **Prompt层**: AI系统提示词明确禁止autoOrder=true
2. **代码层**: `intent.setAutoOrder(false)` 强制禁用
3. **状态层**: 搜索后强制进入WAIT_CONFIRM状态

---

### 4. 并行路线计算（性能优化）

```java
// 使用自定义线程池并行计算前3个POI的路线
CompletableFuture.runAsync(() -> {
    poiList.stream()
        .limit(3)
        .forEach(poi -> {
            RouteResult route = amapClient.getRoute(origin, destination, "driving");
            poi.setDuration(route.getDuration());
            poi.setPrice(route.getPrice());
        });
}, businessExecutor);

// 最多等待8秒
future.get(8, TimeUnit.SECONDS);
```

---

### 5. 会话记忆管理（Redis）

```java
// 保存历史消息（最多保留20条）
memoryService.saveMessage(sessionId, "user", "我想去故宫");
memoryService.saveMessage(sessionId, "assistant", "为你找到以下地点...");

// 获取历史构建上下文
List<ChatMessage> history = memoryService.getHistory(sessionId);
AgentIntent intent = tongyiClient.parseIntentWithContext(history, currentMessage);
```

---

## 📦 依赖清单

详见原 `COMPLETE_SOURCE_CODE_SUMMARY.md` 文档，此处省略。

---

## 🚀 部署说明

### 1. 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 7.x+
- Maven 3.6+

### 2. 配置文件（application.yml）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/anxin_travel?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
  redis:
    host: localhost
    port: 6379

anxin:
  amap:
    web-api-key: YOUR_AMAP_KEY

tongyi:
  api:
    key: YOUR_TONGYI_KEY

jwt:
  secret: your_jwt_secret_key
  expiration: 86400000  # 24小时
```

### 3. 启动命令

```bash
# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/travel-1.0-SNAPSHOT.jar

# 或使用Maven插件
mvn spring-boot:run
```

---

## 📊 性能指标

### 响应时间
- AI意图识别: 200-500ms
- POI搜索: 100-300ms
- 路线规划: 200-500ms
- 订单创建: 500-1000ms（含路线计算）

### 并发能力
- 线程池核心线程数: 10
- 最大线程数: 20
- 队列容量: 200
- 预计QPS: 50-100

---

## 🐛 常见问题

### 1. 坐标格式问题
**问题**: 高德API返回空结果  
**原因**: 坐标格式应为 `lng,lat`（经度在前）  
**解决**: 检查 `AmapClient.getRoute()` 中的坐标拼接

### 2. 头像上传字段名
**问题**: 前端上传失败  
**原因**: 字段名应为 `avatar`，不是 `avatarFile`  
**解决**: 修改前端FormData字段名

### 3. AI返回关键词过长
**问题**: AI返回整句话作为keyword  
**解决**: `cleanKeyword()` 会自动清洗，或触发fallback逻辑

### 4. POI排序不正确
**问题**: 完全匹配的POI不在第一位  
**解决**: 已优化评分算法，确保等级5（完全匹配）得分最高

---

## 📝 开发规范

### 1. 日志规范
```java
log.info("✅ 操作成功：{}", detail);
log.warn("⚠️ 警告信息：{}", detail);
log.error("❌ 错误信息：{}", detail, exception);
log.debug("🔍 调试信息：{}", detail);
```

### 2. 异常处理
- Controller层：抛出业务异常
- Service层：捕获并转换异常
- GlobalExceptionHandler：统一响应格式

### 3. 事务管理
```java
@Transactional(rollbackFor = Exception.class)
public void createOrder(...) { ... }
```

---

## 🔄 版本历史

### v1.0（当前版本）
- ✅ AI意图识别（通义千问）
- ✅ POI搜索与评分排序
- ✅ 订单管理（仿滴滴计价）
- ✅ 图片识别（OCR）
- ✅ WebSocket实时通信
- ✅ 会话记忆管理
- ✅ 用户认证与授权
- ✅ 紧急联系人管理

### 待优化项
- ⏳ JWT版本升级（0.9.1 → 0.11.5+）
- ⏳ Jackson版本统一
- ⏳ 引入依赖安全检查
- ⏳ 微服务拆分

---

**文档生成时间**: 2026-04-05  
**总代码行数**: ~8000行  
**核心文件数**: 50+  
**API接口数**: 20+
