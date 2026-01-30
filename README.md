# Chill Chat 🧊

Chill Chat 是一个基于 Spring Boot 和 Vue 3 构建的实时在线社交应用，旨在提供轻松高效的沟通体验。

## ✨ 主要功能

- **🤖 AI 智能助手**: 内置 ChillBot (基于通义千问模型)，支持上下文对话，具备知心好友人设。
- **🌑 全局深色模式**: 支持浅色/深色主题一键切换，跨设备/窗口自动同步。
- **🟢 在线状态感知**: 实时显示好友在线/离线状态，支持消息未读红点计数。
- **🔍 实时通讯**: 
  - 基于 Netty WebSocket 实现私聊/群聊毫秒级推送。
  - 支持联系人列表实时搜索。
- **休闲广场**: 发布动态、评论互动，完全适配深色模式。
- **好友群组**: 完整的社交关系链管理，支持创建群聊。

## 🛠 技术栈

### 后端 (Backend)
- **核心框架**: Java 21, Spring Boot 3.2.1
- **AI 集成**: Aliyun DashScope SDK (Qwen-flash)
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