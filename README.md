# Anxin Travel - Intelligent Travel Assistant

## Project Overview
Anxin Travel is an AI-powered intelligent travel assistant system that provides convenient ride-hailing services through natural language interaction and image recognition.

## Core Features
1. **Intelligent Dialogue**: Natural language understanding for travel intentions
2. **Image Recognition**: Photo-based destination recognition and booking
3. **Multi-Map Search**: Tencent Map (primary) + Amap (fallback)
4. **Smart Route Planning**: Automatic optimal route calculation with time and cost estimation
5. **Dialect Support**: Multi-dialect voice-to-text recognition

## Tech Stack
- **Backend**: Java 17, Spring Boot 3.1.5
- **Database**: MySQL 8.0, MyBatis-Plus 3.5.3
- **Cache**: Redis 7.x
- **AI Service**: Tongyi Qianwen (Intent Recognition + OCR)
- **Map Service**: Tencent Map API (Primary) + Amap API (Fallback)
- **Communication**: WebSocket + HTTP RESTful API
- **Security**: JWT + BCrypt Password Encryption

## Quick Start

### Prerequisites
- JDK 17+
- Maven 3.9+
- MySQL 8.0
- Redis 7.x

### Installation

#### 1. Database Setup
```bash
mysql -u root -p
CREATE DATABASE anxin_travel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
use anxin_travel;
source database/anxin_travel.sql;
```

#### 2. Configuration
Edit `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    username: your_db_username
    password: your_db_password
    
anxin:
  amap:
    web-api-key: your_amap_key
  tencent:
    map:
      key: your_tencent_key

tongyi:
  api:
    key: your_tongyi_key
```

#### 3. Run Application
```bash
mvn clean package -DskipTests
java -jar target/travel-1.0-SNAPSHOT.jar
```

## API Endpoints

### Image Recognition
- **URL**: `POST /api/agent/image`
- **Headers**: `X-User-Id: {user_id}`
- **Request Body**:
```json
{
  "sessionId": "unique-session-id",
  "imageBase64": "data:image/jpeg;base64,...",
  "lat": 23.6533,
  "lng": 116.6772
}
```

### Confirm Destination
- **URL**: `POST /api/agent/confirm`
- **Request Body**:
```json
{
  "sessionId": "unique-session-id",
  "selectedPoiName": "Destination Name",
  "lat": 23.6533,
  "lng": 116.6772
}
```

### WebSocket
- **URL**: `ws://localhost:8080/ws/agent?token={jwt_token}`
- **Message Types**: text, image, confirm

## Project Structure
```
src/main/java/com/anxin/travel/
├── agent/                    # AI Agent Module
│   ├── ai/                   # AI Client (Tongyi Qianwen)
│   ├── controller/           # Agent Controllers
│   ├── service/              # Core Business Logic
│   │   ├── AgentService.java
│   │   ├── SearchStrategyEngine.java
│   │   ├── ImageRecognitionService.java
│   │   └── MemoryService.java
│   └── tool/                 # Utility Tools
├── module/                   # Business Modules
│   ├── auth/                 # Authentication
│   ├── user/                 # User Management
│   ├── order/                # Order Management
│   └── map/                  # Map Services
│       ├── client/           # Map API Clients
│       │   ├── TencentMapClient.java
│       │   └── AmapClient.java
│       └── controller/
└── common/                   # Common Components
    ├── result/               # Unified Response
    └── util/                 # Utilities (JWT, Redis, etc.)
```

## Technical Highlights

### 1. Multi-Path Search Strategy
- Tencent Map as primary search source
- Amap as fallback for better coverage
- Intelligent scoring: Name match (50%) + Distance (30%) + Address relevance (20%)

### 2. Performance Optimization
- Early termination when sufficient results found
- Pre-computed core keywords to avoid repeated regex
- Redis caching for POI results (10 minutes TTL)
- Parallel route calculation using multi-threading

### 3. Security
- JWT authentication for stateless sessions
- BCrypt password hashing
- Input validation to prevent SQL injection and XSS

### 4. Fault Tolerance
- Multi-map degradation strategy
- Zero-result feedback with "Did you mean?" suggestions
- Quality gating to filter irrelevant results

## License
This project is for competition and educational purposes only.
