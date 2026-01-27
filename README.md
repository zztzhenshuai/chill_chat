# Chill Chat 🧊

Chill Chat 是一个基于 Spring Boot 和 Vue 3 构建的实时在线社交应用，旨在提供轻松高效的沟通体验。

## ✨ 主要功能

- **实时消息**: 基于 Netty WebSocket 实现私聊和群聊的毫秒级推送。
- **休闲广场**: 发布动态、分享生活，支持图片上传和点赞互动。
- **好友系统**: 添加好友、处理好友请求、管理好友列表。
- **群组聊天**: 创建群组、邀请好友，支持多人实时互动。
- **用户中心**: 个性化头像上传（支持阿里云 OSS/本地存储）和个性签名。

## 🛠 技术栈

### 后端 (Backend)
- **核心框架**: Java 21, Spring Boot 3.2.1
- **数据库**: MySQL 8.x, MyBatis Plus
- **缓存**: Redis
- **实时通信**: Netty (WebSocket)
- **文件存储**: Aliyun OSS / Local Storage

### 前端 (Frontend)
- **框架**: Vue 3 (Composition API)
- **语言**: TypeScript
- **构建工具**: Vite
- **UI 组件**: Element Plus, Tailwind CSS

## 🚀 快速开始

### 环境要求
- JDK 21+
- Node.js 18+
- MySQL 8.0+
- Redis

### 启动步骤

1. **数据库准备**:
   创建数据库 `chill_chat`，后端启动时会自动执行 `src/main/resources/schema*.sql` 初始化表结构。

2. **后端启动**:
   - 修改 `backend/src/main/resources/application.yml` 中的数据库和 Redis 配置。
   - 运行 `ChillChatApplication` 主类。

3. **前端启动**:
   ```bash
   cd frontend
   npm install
   npm run dev