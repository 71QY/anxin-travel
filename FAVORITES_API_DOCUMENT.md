# 收藏常用地点功能 - 前端对接文档

## 1. 数据库准备
请在数据库中执行以下 SQL 脚本以创建收藏表：
`database/alter_user_favorites.sql`

## 2. API 接口定义

**基础路径**: `/api/favorites`
**鉴权方式**: Header 中携带 `Authorization: Bearer <token>`

### 2.1 获取收藏列表
- **接口**: `GET /api/favorites`
- **说明**: 获取当前登录用户的所有收藏地点，按更新时间倒序排列。
- **响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "家",
      "address": "广东省潮州市湘桥区...",
      "latitude": 23.656,
      "longitude": 116.622,
      "type": "HOME",
      "updatedAt": "2026-04-17T10:00:00"
    }
  ]
}
```

### 2.2 添加收藏
- **接口**: `POST /api/favorites`
- **请求体**:
```json
{
  "name": "市人民医院",
  "address": "广东省潮州市...",
  "latitude": 23.660,
  "longitude": 116.630,
  "type": "HOSPITAL" 
}
```
- **注意**: `userId` 由后端从 Token 中自动获取，前端无需传递。`type` 可选值为：`HOME`, `COMPANY`, `HOSPITAL`, `CUSTOM`。

### 2.3 更新收藏
- **接口**: `PUT /api/favorites`
- **请求体**: 需包含 `id` 字段，其他字段同添加接口。

### 2.4 删除收藏
- **接口**: `DELETE /api/favorites/{id}`
- **说明**: 路径参数传入收藏记录的 ID。

## 3. 实现逻辑与注意事项

1.  **一键叫车联动**: 
    - 点击收藏列表项时，应跳转至首页或订单页，并将该地点的 `name`, `latitude`, `longitude` 自动填充为目的地。
2.  **地图标记联动**:
    - 在收藏页面点击“地图标记”时，建议调用高德地图 SDK 的选点组件，获取坐标和地址后调用 `POST` 接口保存。
3.  **适老化设计**:
    - 列表项按钮高度建议不低于 `72dp`，字体大小不低于 `17sp`。
    - 删除操作建议增加二次确认弹窗，防止长辈误触。
4.  **数据同步**:
    - 建议在进入“收藏”Tab 时自动刷新列表，确保看到最新数据。
