# 智能出行助手 - 外部依赖库清单

## 📦 Maven 依赖总览

**项目基本信息**
- **GroupId**: com.anxin
- **ArtifactId**: travel
- **Version**: 1.0-SNAPSHOT
- **JDK 版本**: 17
- **Spring Boot 版本**: 3.1.5
- **打包方式**: jar

---

## 🔧 核心框架依赖

### 1. Spring Boot 生态系统

| 依赖名称 | GroupId | ArtifactId | Version | 用途说明 |
|---------|---------|-----------|---------|---------|
| **Spring Boot Parent** | org.springframework.boot | spring-boot-starter-parent | 3.1.5 | 父 POM，管理依赖版本 |
| **Spring Web** | org.springframework.boot | spring-boot-starter-web | 3.1.5 (继承) | RESTful API、Tomcat 容器 |
| **Spring AOP** | org.springframework.boot | spring-boot-starter-aop | 3.1.5 (继承) | 面向切面编程、事务管理 |
| **Spring WebSocket** | org.springframework.boot | spring-boot-starter-websocket | 3.1.5 (继承) | WebSocket 实时通信 |
| **Spring Data Redis** | org.springframework.boot | spring-boot-starter-data-redis | 3.1.5 (继承) | Redis 缓存操作 |

**传递依赖（自动引入）**：
- Spring Core 6.0.13
- Spring Context 6.0.13
- Spring MVC 6.0.13
- Tomcat Embed 10.1.15
- Jackson Databind 2.15.3
- Spring Security Crypto（部分功能）

---

### 2. 数据持久化

#### 2.1 MyBatis-Plus
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.3</version>
</dependency>
```
**功能**：
- ORM 框架，简化 CRUD 操作
- 分页插件
- 代码生成器支持
- 乐观锁、逻辑删除

**传递依赖**：
- MyBatis 3.5.13
- MyBatis-Spring 3.0.2
- jsqlparser 4.6

#### 2.2 MySQL 驱动
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.0.33</version>
    <scope>runtime</scope>
</dependency>
```
**功能**：
- JDBC 驱动程序
- 连接池支持（HikariCP）
- SSL/TLS 加密连接

---

### 3. JSON 处理

#### 3.1 FastJSON
```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>2.0.40</version>
</dependency>
```
**功能**：
- 高性能 JSON 序列化/反序列化
- 高德地图 API 响应解析
- WebSocket 消息处理

**特点**：
- 比 Jackson 快 2-3 倍
- 支持复杂对象转换
- 中文友好

#### 3.2 Jackson（兼容层）
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.13.5</version>
</dependency>
```
**功能**：
- JWT Token 序列化兼容
- Spring MVC 默认 JSON 处理器
- Retrofit 转换器

**包含模块**：
- jackson-core 2.13.5
- jackson-annotations 2.13.5
- jackson-databind 2.13.5

---

### 4. HTTP 客户端

#### 4.1 OkHttp
```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>

<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>logging-interceptor</artifactId>
    <version>4.12.0</version>
</dependency>
```
**功能**：
- 高性能 HTTP 客户端
- 连接池管理
- 请求/响应拦截器
- 日志记录

**应用场景**：
- 通义千问 API 调用
- 高德地图 API 调用
- 第三方服务集成

#### 4.2 Apache HttpClient5
```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.2.1</version>
</dependency>
```
**功能**：
- RestTemplate 超时控制
- 连接池管理
- 重试机制

---

### 5. Retrofit（REST 客户端）

```xml
<dependency>
    <groupId>com.squareup.retrofit2</groupId>
    <artifactId>adapter-rxjava2</artifactId>
    <version>2.9.0</version>
</dependency>

<dependency>
    <groupId>com.squareup.retrofit2</groupId>
    <artifactId>converter-jackson</artifactId>
    <version>2.9.0</version>
</dependency>
```
**功能**：
- 类型安全的 HTTP 客户端
- RxJava2 适配器（异步调用）
- Jackson 转换器

**传递依赖**：
- Retrofit 2.9.0
- RxJava2 2.2.21
- Reactive Streams 1.0.3

---

### 6. 安全认证

#### 6.1 JWT (jjwt)
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.1</version>
</dependency>
```
**功能**：
- Token 生成与验证
- HS256 签名算法
- 用户身份认证

**注意**：
- 版本较旧（0.9.1），建议升级到 0.11.x
- 需要 javax.xml.bind 支持

#### 6.2 Spring Security Crypto
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
    <version>5.8.6</version>
</dependency>
```
**功能**：
- BCrypt 密码加密
- 单向哈希算法
- 盐值自动生成

---

### 7. WebSocket 实现

#### 7.1 Jakarta WebSocket API
```xml
<dependency>
    <groupId>jakarta.websocket</groupId>
    <artifactId>jakarta.websocket-api</artifactId>
    <version>2.1.0</version>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>jakarta.websocket</groupId>
    <artifactId>jakarta.websocket-client-api</artifactId>
    <version>2.1.0</version>
    <scope>provided</scope>
</dependency>
```
**功能**：
- WebSocket 标准 API（Jakarta EE 9+）
- 服务端和客户端支持
- Java 17 兼容

#### 7.2 Tyrus WebSocket 实现
```xml
<dependency>
    <groupId>org.glassfish.tyrus.bundles</groupId>
    <artifactId>tyrus-standalone-client-jdk</artifactId>
    <version>2.0.1</version>
</dependency>
```
**功能**：
- WebSocket 客户端实现
- 独立运行（无需应用服务器）
- JDK 原生支持

---

### 8. 工具库

#### 8.1 Lombok
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```
**功能**：
- 代码简化（@Data, @Slf4j, @RequiredArgsConstructor）
- 编译时代码生成
- 减少样板代码

**常用注解**：
- `@Data` - getter/setter/toString
- `@Slf4j` - 日志对象
- `@RequiredArgsConstructor` - 构造器注入

#### 8.2 JAXB API（兼容性）
```xml
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
    <version>2.3.1</version>
</dependency>
```
**功能**：
- XML 绑定支持
- JWT 库依赖
- Java 11+ 需手动添加

#### 8.3 Java Annotation API
```xml
<dependency>
    <groupId>javax.annotation</groupId>
    <artifactId>javax.annotation-api</artifactId>
    <version>1.3.2</version>
</dependency>
```
**功能**：
- @PostConstruct, @PreDestroy
- Java 11+ 需手动添加
- Spring Bean 生命周期

---

## 🌐 外部 API 服务

### 1. 高德地图 API
**官网**: https://lbs.amap.com/api  
**用途**:
- POI 搜索
- 路线规划
- 逆地理编码
- IP 定位

**API Key**: 配置在 `application.yml`
```yaml
amap:
  api-key: YOUR_AMAP_KEY
```

**调用的端点**:
- `/v3/place/text` - POI 搜索
- `/v3/direction/driving` - 驾车路线
- `/v3/geocode/regeo` - 逆地理编码
- `/v3/ip` - IP 定位

---

### 2. 通义千问 API（阿里云）
**官网**: https://help.aliyun.com/zh/dashscope  
**用途**:
- 意图识别
- 图片 OCR
- 自然语言理解

**API Key**: 配置在 `application.yml`
```yaml
tongyi:
  api:
    key: YOUR_TONGYI_KEY
```

**使用的模型**:
- `qwen-turbo` - 文本意图识别
- `qwen-vl-plus` - 图片识别（多模态）

**API 端点**:
- `https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation`
- `https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`

---

## 🗄️ 数据存储

### 1. MySQL 8.0
**版本**: 8.0.33  
**用途**:
- 用户数据存储
- 订单数据存储
- 紧急联系人存储

**核心表**:
- `sys_user` - 用户表
- `order_info` - 订单表
- `emergency_contact` - 紧急联系人表

---

### 2. Redis 7.x
**用途**:
- 会话缓存（sessionId → 对话历史）
- POI 候选列表缓存（30 分钟过期）
- 用户位置缓存

**数据结构**:
- String - 简单键值对
- Hash - 会话数据
- List - 消息队列（预留）

---

## 📊 依赖关系图

```
travel (主项目)
├── Spring Boot 3.1.5
│   ├── Spring Web (Tomcat + MVC)
│   ├── Spring AOP (事务管理)
│   ├── Spring WebSocket
│   └── Spring Data Redis
├── MyBatis-Plus 3.5.3
│   ├── MyBatis 3.5.13
│   └── HikariCP (连接池)
├── MySQL Connector 8.0.33
├── FastJSON 2.0.40
├── OkHttp 4.12.0
│   └── Logging Interceptor
├── Retrofit 2.9.0
│   ├── RxJava2 Adapter
│   └── Jackson Converter
├── JWT (jjwt) 0.9.1
├── Spring Security Crypto 5.8.6
├── Jakarta WebSocket API 2.1.0
├── Tyrus WebSocket 2.0.1
├── Lombok (编译时)
├── Jackson Databind 2.13.5
├── Apache HttpClient5 5.2.1
├── JAXB API 2.3.1 (兼容)
└── Java Annotation API 1.3.2 (兼容)
```

---

## 🔒 安全性说明

### 已知安全问题

1. **JWT 版本过旧**
    - 当前版本：0.9.1
    - 建议升级：0.11.5+
    - 原因：0.9.x 存在安全漏洞

2. **FastJSON 版本**
    - 当前版本：2.0.40
    - 状态：✅ 相对安全
    - 建议：定期关注官方更新

3. **Jackson 版本**
    - 当前版本：2.13.5
    - 建议升级：2.15.x+
    - 原因：修复多个 CVE 漏洞

---

## 📈 性能优化建议

### 1. 依赖精简
**可移除的依赖**：
- `adapter-rxjava2` - 如果未使用 RxJava
- `tyrus-standalone-client-jdk` - 如果只用服务端 WebSocket

### 2. 版本统一
**冲突解决**：
- Jackson 2.13.5 vs Spring Boot 内置 2.15.3
- 建议统一使用 Spring Boot 管理的版本

### 3. 可选依赖标记
```xml
<optional>true</optional>
```
已正确标记 Lombok 为可选依赖。

---

## 🚀 构建与部署

### Maven 命令

```bash
# 清理并编译
mvn clean compile

# 打包（跳过测试）
mvn clean package -DskipTests

# 安装到本地仓库
mvn clean install

# 运行应用
mvn spring-boot:run
```

### JVM 参数（pom.xml 配置）
```xml
<jvmArguments>
    -Xms512m -Xmx1024m
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=logs/heap_dump.hprof
    -Dfile.encoding=UTF-8
    -Dsun.stdout.encoding=UTF-8
    --add-opens=java.base/java.lang=ALL-UNNAMED
    --add-opens=java.base/java.util=ALL-UNNAMED
</jvmArguments>
```

---

## 📋 依赖清单表格（快速查阅）

| # | 依赖名称 | 版本 | 用途 | 必需性 |
|---|---------|------|------|--------|
| 1 | Spring Boot Starter Web | 3.1.5 | REST API | ⭐⭐⭐⭐⭐ |
| 2 | Spring Boot Starter AOP | 3.1.5 | 事务管理 | ⭐⭐⭐⭐⭐ |
| 3 | Spring Boot Starter WebSocket | 3.1.5 | 实时通信 | ⭐⭐⭐⭐⭐ |
| 4 | Spring Boot Starter Data Redis | 3.1.5 | 缓存 | ⭐⭐⭐⭐⭐ |
| 5 | MyBatis-Plus | 3.5.3 | ORM | ⭐⭐⭐⭐⭐ |
| 6 | MySQL Connector | 8.0.33 | 数据库驱动 | ⭐⭐⭐⭐⭐ |
| 7 | FastJSON | 2.0.40 | JSON 处理 | ⭐⭐⭐⭐ |
| 8 | OkHttp | 4.12.0 | HTTP 客户端 | ⭐⭐⭐⭐ |
| 9 | Retrofit | 2.9.0 | REST 客户端 | ⭐⭐⭐ |
| 10 | JWT (jjwt) | 0.9.1 | Token 认证 | ⭐⭐⭐⭐⭐ |
| 11 | Spring Security Crypto | 5.8.6 | 密码加密 | ⭐⭐⭐⭐ |
| 12 | Jakarta WebSocket API | 2.1.0 | WebSocket 标准 | ⭐⭐⭐⭐⭐ |
| 13 | Tyrus WebSocket | 2.0.1 | WebSocket 实现 | ⭐⭐⭐ |
| 14 | Lombok | (继承) | 代码简化 | ⭐⭐⭐⭐ |
| 15 | Jackson Databind | 2.13.5 | JSON 兼容 | ⭐⭐⭐ |
| 16 | Apache HttpClient5 | 5.2.1 | HTTP 客户端 | ⭐⭐ |
| 17 | JAXB API | 2.3.1 | 兼容支持 | ⭐ |
| 18 | Java Annotation API | 1.3.2 | 兼容支持 | ⭐ |

---

## 🎓 技术选型理由

### 为什么选择这些依赖？

1. **Spring Boot 3.1.5**
    - ✅ JDK 17 最佳支持
    - ✅ 长期维护版本
    - ✅ 丰富的生态系统

2. **MyBatis-Plus**
    - ✅ 简化 CRUD 操作
    - ✅ 分页插件开箱即用
    - ✅ 国内社区活跃

3. **FastJSON**
    - ✅ 性能优于 Jackson
    - ✅ 中文友好
    - ⚠️ 需注意安全更新

4. **OkHttp + Retrofit**
    - ✅ 类型安全
    - ✅ 异步支持
    - ✅ 拦截器机制

5. **JWT (jjwt)**
    - ✅ 轻量级
    - ✅ 易于集成
    - ⚠️ 版本需升级

---

## 📞 依赖管理建议

### 短期优化（1 个月内）
1. 升级 JWT 到 0.11.5+
2. 统一 Jackson 版本
3. 移除未使用的依赖

### 中期优化（3 个月内）
1. 升级到 Spring Boot 3.2.x
2. 评估替换 FastJSON 为 Jackson
3. 引入依赖安全检查插件

### 长期规划（6 个月内）
1. 迁移到 Spring Boot 3.3+ LTS
2. 引入 GraalVM 原生镜像支持
3. 微服务拆分后的依赖隔离

---

**文档生成时间**: 2026-04-04  
**Maven 中央仓库**: https://repo.maven.apache.org/maven2  
**总依赖数**: 18 个直接依赖 + ~50 个传递依赖
