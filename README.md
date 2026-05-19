Anxin Travel - AI智能出行助手

基于 Java 17 + Spring Boot 3.1.5 构建的AI驱动智能出行平台，通过自然语言交互和图像识别技术实现便捷叫车服务。项目集成阿里云通义千问大模型、双地图数据源（腾讯+高德）、Redis缓存及WebSocket实时通信，功能完整、架构规范

技术栈

核心框架：Java 17、Spring Boot 3.1.5
持久层：MyBatis-Plus 3.5.3
数据库：MySQL 8.0、Redis 7.x
AI服务：阿里云通义千问API（LLM意图识别 + OCR图像识别）
地图服务：腾讯地图API（主）、高德地图API（备）
网络通信：OkHttp 4.12、Retrofit 2.9、WebSocket
安全认证：JJWT 0.9.1、Spring Security Crypto（BCrypt）
JSON处理：Jackson 2.13、Fastjson 2.0
其他：Lombok、Apache HttpClient5、Tyrus WebSocket

核心功能

AI智能体对话
 意图识别：基于通义千问大模型精准区分搜索/下单/聊天意图
 多轮对话：MemoryService维护会话上下文，支持连续交互
 方言支持：8种方言语音转文字识别与翻译
 工具调用：动态调度地图搜索、路线规划、天气查询等工具

图像识别叫车
 OCR地址提取：自动识别照片中的目的地地址信息
 Base64图片上传：支持JPEG/PNG格式图片传输
 批量识别：支持多张图片连续识别与会话关联
 智能纠错：地址模糊时提供"您是否要找"推荐

多源地图搜索
 双数据源融合：腾讯地图（主）+ 高德地图（备）智能降级
 POI智能排序：多维度评分算法（名称匹配50% + 距离30% + 地址相关性20%）
 零结果反馈：无匹配结果时提供纠偏建议
 Redis缓存：POI搜索结果缓存10分钟，减少API调用60%

智能路径规划
 并行计算：多线程同时计算多个候选目的地路线
 实时预估：返回距离、时间、费用等多维度信息
 最优推荐：自动筛选最佳路线并排序

亲情守护模式
 长辈UI切换：适老化界面设计，大字体、大按钮
 代叫车功能：监护人可代替长辈创建订单并支付
 位置共享：实时追踪长辈行程，三方群聊（用户-司机-监护人）
 紧急联系人：一键呼叫预设紧急联系人

订单管理
 实时状态推送：WebSocket推送订单状态变更（待接单/已接单/行程中/已完成）
 司机信息展示：显示司机姓名、车牌、评分、联系电话
 历史订单查询：支持分页查询与条件筛选
 订单取消：支持用户主动取消待接单订单

用户系统
 JWT认证：无状态会话管理，支持HTTP和WebSocket鉴权
 密码加密：BCrypt哈希加密，防止明文存储
 头像上传：支持用户自定义头像
 收藏地点：常用地址收藏与管理

项目结构

```
src/main/java/com/anxin/travel/
├── agent/                    # AI智能体模块（核心业务）
│   ├── ai/                   # AI客户端层
│   │   └── TongyiQianwenClient.java
│   ├── controller/           # 控制层
│   │   ├── AgentController.java
│   │   └── AgentWebSocketController.java
│   ├── service/              # 服务层（业务逻辑）
│   │   ├── AgentService.java
│   │   ├── SearchStrategyEngine.java
│   │   ├── ImageRecognitionService.java
│   │   ├── MemoryService.java
│   │   ├── QueryClassifier.java
│   │   └── ToolExecutorService.java
│   ├── tool/                 # 工具层
│   │   ├── MapTool.java
│   │   ├── RouteTool.java
│   │   └── WeatherTool.java
│   └── model/                # 数据模型
│       ├── AgentMessage.java
│       ├── AgentIntent.java
│       └── CandidateDestination.java
│
├── module/                   # 业务功能模块
│   ├── auth/                 # 认证授权
│   ├── user/                 # 用户管理
│   ├── order/                # 订单管理
│   ├── guard/                # 亲情守护
│   ├── chat/                 # 私聊功能
│   └── map/                  # 地图服务
│       ├── client/           # 地图API客户端
│       │   ├── TencentMapClient.java
│       │   └── AmapClient.java
│       └── controller/
│
├── config/                   # 全局配置
│   ├── WebConfig.java
│   ├── AuthInterceptor.java
│   ├── WebSocketConfig.java
│   ├── ThreadPoolConfig.java
│   └── GlobalExceptionHandler.java
│
└── common/                   # 通用组件
    ├── result/               # 统一响应封装
    │   ├── Result.java
    │   └── PageResult.java
    └── util/                 # 工具类
        ├── JwtUtil.java
        ├── RedisUtil.java
        └── UserContext.java

快速启动

环境要求
- JDK 17+
- Maven 3.9+
- MySQL 8.0
- Redis 7.x

安装步骤

1. 数据库初始化
bash
mysql -u root -p
CREATE DATABASE anxin_travel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE anxin_travel;
SOURCE database/anxin_travel.sql;

2. 配置环境变量
   复制 `.env.example` 为 `.env` 或 `application-local.yml`，填写以下配置：
yaml
spring:
  datasource:
    username: your_db_username
    password: your_db_password
    
anxin:
  tencent:
    map:
      key: your_tencent_map_key
  amap:
    web-api-key: your_amap_key

tongyi:
  api:
    key: your_tongyi_qianwen_key

jwt:
  secret: your_jwt_secret_key

注意：敏感配置请使用 `application-local.yml` 并添加到 `.gitignore`，不要提交到版本控制系统。

3. 编译运行
bash
mvn clean package -DskipTests
java -jar target/travel-1.0-SNAPSHOT.jar


或使用Maven直接运行：
bash
mvn spring-boot:run


4. 访问服务
HTTP API：http://localhost:8080
WebSocket：ws://localhost:8080/ws/agent?token={jwt_token}

API接口文档

认证接口：
POST /api/auth/register` - 用户注册
POST /api/auth/login` - 用户登录
GET /api/auth/info` - 获取用户信息

AI智能体接口
POST /api/agent/image` - 图片识别叫车
   Headers: `X-User-Id: {user_id}`
   Body: `{ sessionId, imageBase64, lat, lng }`
   
 POST /api/agent/confirm` - 确认目的地
   Body: `{ sessionId, selectedPoiName, lat, lng }`

订单接口
 POST /api/order/create` - 创建订单
 GET /api/order/list` - 查询订单列表
 GET /api/order/{id}` - 查询订单详情
 POST /api/order/cancel` - 取消订单

亲情守护接口
 POST /api/guard/add` - 添加监护人
 GET /api/guard/list` - 查询监护人列表
 POST /api/guard/share-location` - 分享位置给长辈

WebSocket消息类型
 text` - 文本消息（自然语言对话）
 image` - 图片消息（Base64编码）
 confirm` - 确认消息（选择目的地）
 NEW_ORDER` - 新订单通知
 ORDER_STATUS_UPDATE` - 订单状态更新

技术亮点

1. 纯后端智能体架构
 基于通义千问大模型的意图理解引擎
 多轮对话上下文管理（MemoryService）
 工具调用框架（Tool Executor Pattern）
 查询重写与优化（Query Rewriter）

2. 多源地图融合策略
 双数据源智能降级（腾讯→高德）
 POI结果去重与融合
 多维度评分排序算法
 Redis缓存优化（10分钟TTL）

3. 性能优化
 多线程并行路线计算（CompletableFuture）
 早期终止策略（找到足够结果立即返回）
 预计算核心关键词避免重复正则匹配
 JVM G1GC垃圾回收器调优

4. 高可用设计
 多级Fallback容错机制
 WebSocket断线重连支持
 全局异常处理与日志记录
 输入验证防SQL注入/XSS攻击

5. 安全机制
 JWT无状态认证（HTTP + WebSocket）
 BCrypt密码哈希加密
 敏感配置环境变量管理
 CORS跨域资源共享配置

测试账号：
普通用户：
 手机号：13800138000
 密码：123456

管理员：
 手机号：13900139000
 密码：123456

项目预览

核心功能演示：
 AI对话叫车：自然语言输入"我要去火车站" → 自动搜索并推荐站点
 图片识别：上传车站照片 → OCR提取地址 → 自动填充目的地
 亲情守护：监护人代叫车 → 实时位置共享 → 三方群聊
 地图搜索：双数据源融合 → 智能排序 → Top 5推荐

贡献指南

欢迎提交 Issue 与 PR！

开发流程：
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

代码规范：
 遵循阿里巴巴Java开发手册
 使用Lombok简化代码
 添加必要的中文注释
 保持方法单一职责

License:
本项目仅用于比赛和学习目的，禁止商业用途

联系方式:
如有问题或建议，欢迎提交 Issue 或通过以下方式联系：
 GitHub Issues:https://github.com/71QY/anxin-travel/issues
 Email: 1396587508@qq.com

Star this repo if you find it helpful! ⭐
