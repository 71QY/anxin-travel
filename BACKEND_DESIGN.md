# 智能出行助手 - 后端设计与开发文档

## 📋 项目概述

### 项目名称
智能出行助手（Intelligent Travel Assistant）

### 项目描述
基于 AI 智能体的智能出行服务平台，提供语音/文字交互、图片识别、POI 搜索、路线规划、订单管理等一体化服务。

### 技术栈
- **JDK**: 17 (LTS)
- **框架**: Spring Boot 3.1.5
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **ORM**: MyBatis-Plus 3.5.4
- **AI 服务**: 通义千问 Qwen-VL-Plus
- **地图服务**: 高德地图 API
- **WebSocket**: Jakarta WebSocket API
- **构建工具**: Maven 3.9+

---

## 🏗️ 系统架构

### 整体架构图
```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Frontend   │────▶│  Backend API  │────▶│   Database   │
│ (Android/Web)│     │ (Spring Boot) │     │  (MySQL)     │
└─────────────┘     └──────────────┘     └─────────────┘
                           │
                    ┌──────┴──────┐
                    │             │
              ┌─────▼────┐ ┌────▼─────┐
              │  Redis   │ │External  │
              │ (Cache)  │ │ APIs     │
              └──────────┘ └──────────┘
                             ├─ 高德地图
                             ├─ 通义千问
                             └─ 其他第三方
```

### 模块划分
```
com.anxin.travel
├── agent/                    # 智能体核心模块
│   ├── controller/          # REST API 控制器
│   ├── service/             # 业务逻辑层
│   ├── model/               # 领域模型
│   ├── dto/                 # 数据传输对象
│   ├── tool/                # 工具类（地图、天气等）
│   ├── ai/                  # AI 客户端
│   └── config/              # WebSocket 配置
├── module/                   # 业务模块
│   ├── auth/                # 认证授权
│   ├── user/                # 用户管理
│   ├── order/               # 订单管理
│   └── map/                 # 地图服务
├── common/                   # 公共组件
│   ├── result/              # 统一响应封装
│   └── util/                # 工具类
└── config/                   # 全局配置
```

---

## 🔧 环境配置

### JDK 17 要求
```bash
# 验证 JDK 版本
java -version
# 输出：openjdk version "17.0.x"

# 设置 JAVA_HOME
export JAVA_HOME=/path/to/jdk-17
export PATH=$JAVA_HOME/bin:$PATH
```

### Maven 配置 (pom.xml)
```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.1.5</spring-boot.version>
    <mybatis-plus.version>3.5.4</mybatis-plus.version>
</properties>

<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- WebSocket -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    
    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>${mybatis-plus.version}</version>
    </dependency>
    
    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 应用配置 (application.yml)
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/anxin_travel?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  redis:
    host: localhost
    port: 6379
    database: 0

# 高德地图配置
amap:
  api-key: YOUR_AMAP_KEY

# 通义千问配置
tongyi:
  api:
    key: YOUR_TONGYI_KEY
```

---

## 📊 数据库设计

### 核心表结构

#### 1. 用户表 (sys_user)
```sql
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    avatar VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 2. 订单表 (order_info)
```sql
CREATE TABLE order_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    dest_address VARCHAR(255),
    dest_lat DOUBLE,
    dest_lng DOUBLE,
    status TINYINT DEFAULT 0 COMMENT '0:待接单 1:已接单 2:已完成 3:已取消',
    estimate_price DECIMAL(10,2),
    actual_price DECIMAL(10,2),
    platform_used VARCHAR(20) COMMENT 'gaode/baidu/tencent',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_order_no (order_no),
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 🎯 核心功能模块

### 1. 智能体模块 (Agent Module)

#### 功能说明
- 意图识别：通过 AI 解析用户输入
- POI 搜索：基于位置搜索兴趣点
- 路线规划：计算最优路径和价格
- 订单创建：自动生成打车订单

#### 关键类
- `AgentService`: 智能体核心服务
- `AgentController`: REST API 接口
- `NativeWebSocket`: WebSocket 实时通信
- `SearchStrategyEngine`: 搜索策略引擎

#### API 接口

**智能搜索**
```http
POST /api/agent/search
Content-Type: application/json

{
  "sessionId": "uuid",
  "keyword": "医院",
  "lat": 23.6533,
  "lng": 116.6772
}
```

**确认选择**
```http
POST /api/agent/confirm
Content-Type: application/json

{
  "sessionId": "uuid",
  "selectedPoiName": "潮州市中心医院",
  "lat": 23.6533,
  "lng": 116.6772
}
```

**图片识别**
```http
POST /api/agent/image
Content-Type: application/json

{
  "sessionId": "uuid",
  "imageBase64": "data:image/jpeg;base64,...",
  "lat": 23.6533,
  "lng": 116.6772
}
```

### 2. 订单模块 (Order Module)

#### 功能说明
- 订单创建：生成打车订单
- 订单查询：查看订单详情和列表
- 订单状态管理：取消、确认等操作

#### 关键类
- `OrderService`: 订单业务逻辑
- `OrderController`: 订单 API
- `OrderServiceImpl`: 订单实现类

#### API 接口

**创建订单**
```http
POST /api/order/create
Content-Type: application/json

{
  "destName": "潮州市中心医院",  // 或 poiName
  "destLat": 23.666123,          // 或 poiLat
  "destLng": 116.676392          // 或 poiLng
}
```

**查询订单**
```http
GET /api/order/{id}
```

**订单列表**
```http
GET /api/order/list?page=1&size=10&status=0
```

### 3. 地图模块 (Map Module)

#### 功能说明
- POI 搜索：调用高德地图 API
- 路线规划：计算驾车/步行路线
- 逆地理编码：坐标转地址

#### 关键类
- `AmapClient`: 高德地图客户端
- `MapController`: 地图 API

---

## 🔐 安全与认证

### JWT Token 认证
```java
// 生成 Token
String token = jwtUtil.generateToken(userId);

// 验证 Token
boolean valid = jwtUtil.validateToken(token);

// 提取用户 ID
Long userId = jwtUtil.getUserIdFromToken(token);
```

### WebSocket 认证
```java
// 连接时携带 Token
ws://host:8080/ws/agent?token=YOUR_TOKEN

// 服务端验证
JwtUtil jwtUtil = SpringContextUtil.getBean(JwtUtil.class);
boolean valid = jwtUtil.validateToken(token);
```

---

## 🚀 部署指南

### 本地开发
```bash
# 1. 克隆项目
git clone https://gitee.com/kndmfy/travel.git
cd travel

# 2. 安装依赖
mvn clean install

# 3. 配置数据库和 Redis
# 修改 application.yml 中的配置

# 4. 启动应用
mvn spring-boot:run
```

### 生产部署
```bash
# 1. 打包
mvn clean package -DskipTests

# 2. 运行
java -jar target/travel-1.0.0.jar

# 3. 后台运行
nohup java -jar travel-1.0.0.jar > app.log 2>&1 &
```

### Docker 部署
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/travel-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 📝 开发规范

### 代码风格
- 使用 Lombok 简化代码
- 遵循阿里巴巴 Java 开发手册
- 统一异常处理
- 统一响应格式

### 日志规范
```java
@Slf4j
public class ExampleService {
    public void example() {
        log.info("业务开始");
        log.error("异常信息", exception);
    }
}
```

### 响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

---

## 🐛 常见问题

### 1. 订单创建失败：目的地名称为空
**原因**: 前端传递的字段名与后端不一致  
**解决**: 已在 `CreateOrderRequest` 中添加 `@JsonProperty` 注解兼容多种字段名

### 2. WebSocket 心跳处理
**原因**: ping 消息未单独处理  
**解决**: 在 `NativeWebSocket` 中添加了 ping/pong 机制

### 3. 图片识别超时
**原因**: 图片过大  
**解决**: 前端压缩到 500KB 以内

---

## 📞 技术支持

- **项目地址**: https://gitee.com/kndmfy/travel
- **分支**: main (最新稳定版)
- **最后更新**: 2026-04-04

---

## 📄 许可证

本项目仅供学习和内部使用。
