# Chill Chat 🧊

> 一个基于 Spring Boot + Vue 3 构建的全栈实时社交聊天平台，集成 AI 知心好友、语义记忆、广场动态、好友群组等功能。

## 📸 功能预览

| 私聊 & 实时通讯 | AI 知心好友 ChillBot | 休闲广场 |
|:-:|:-:|:-:|
| Netty WebSocket 毫秒级推送 | 工具调用 + 语义记忆 | 发帖、点赞、评论 |

---

## ✨ 主要功能

### 💬 实时通讯
- 基于 **Netty WebSocket**（端口 9090）实现私聊与群聊，消息毫秒级推送
- 好友在线/离线状态实时感知，消息未读红点计数
- 支持联系人列表实时搜索过滤

### 🤖 AI 知心好友 — ChillBot
- 基于 **通义千问 qwen-turbo**，具备温暖贴心的聊天人设
- **Function Calling Agent**：可代用户完成以下操作（执行前需确认）：
  - `get_friend_list` — 查询好友列表
  - `send_message` — 以用户身份给好友发私信
  - `create_post` — 以用户身份在广场发布帖子
- **混合语义记忆**：最近 10 条消息 + 基于 `text-embedding-v3` 向量检索的 8 条语义相关历史，长对话也能记住重要内容
- 内置幻觉执行检测，防止模型跳过 Function Call 直接伪造结果

### 🏖 休闲广场
- 发布图文动态、点赞、评论互动
- 点赞数/评论数由 **Redis** 缓存，高并发下性能稳定

### 👥 好友 & 群组
- 完整社交关系链：发送/接受好友申请
- 创建群聊，群消息实时广播

### 🌙 深色模式
- 全局浅色/深色主题一键切换，状态持久化，跨标签页自动同步

### 🔐 认证
- 基于 **JWT** 的无状态登录认证，Token 由拦截器自动校验

---

## 🛠 技术栈

### 后端
| 技术 | 说明 |
|---|---|
| Java 17 + Spring Boot 3.2.1 | 核心框架 |
| Netty 4.1 | WebSocket 服务器（端口 9090） |
| MyBatis-Plus 3.5.5 | ORM 框架 |
| MySQL 8.x | 主数据库 |
| Redis | 会话缓存、点赞计数、AI 待确认操作暂存 |
| Aliyun DashScope | LLM（qwen-turbo）+ Embedding（text-embedding-v3） |
| Aliyun OSS | 图片/文件存储 |
| FastJSON2 | JSON 序列化 |

### 前端
| 技术 | 说明 |
|---|---|
| Vue 3 + TypeScript | 框架与语言 |
| Vite | 构建工具 |
| Vue Router | 路由管理 |
| Tailwind CSS | 样式框架 |
| Element Plus | UI 组件库 |

---

## 📁 项目结构

```
chill-chat/
├── backend/                        # Spring Boot 后端
│   └── src/main/java/com/chillchat/
│       ├── config/                 # CORS、JWT 拦截器、WebMVC 配置
│       ├── controller/             # REST API（认证、用户、好友、群组、消息、帖子、文件）
│       ├── entity/                 # JPA 实体类
│       ├── mapper/                 # MyBatis-Plus Mapper
│       ├── model/                  # DTO（ChatMessage、MessageType）
│       ├── netty/                  # Netty 服务器 + WebSocket 帧处理器
│       ├── service/                # 业务逻辑（含 BotService、EmbeddingService）
│       └── util/                   # JWT 工具
└── frontend/                       # Vue 3 前端
    └── src/
        ├── views/                  # 页面（Landing、Login、AppLayout、ChatLayout、Square）
        ├── components/             # MessageBubble 等通用组件
        ├── services/               # WebSocket 客户端封装
        └── router/                 # 路由配置
```

---

## 🚀 快速开始

### 环境要求
- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 6+

### 1. 数据库准备

创建数据库：
```sql
CREATE DATABASE chill_chat CHARACTER SET utf8mb4;
```

后端启动时会自动执行以下 SQL 初始化表结构：
`schema.sql` / `schema_posts.sql` / `schema_friends.sql` / `schema_friend_requests.sql` / `schema_user_signature.sql` / `schema_groups.sql` / `schema_embeddings.sql`

### 2. 后端配置

修改 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://<host>:3306/chill_chat?...
    username: <用户名>
    password: <密码>
  data:
    redis:
      host: <host>
      password: <密码>

aliyun:
  oss:
    access-key-id: <你的 AccessKeyId>
    access-key-secret: <你的 AccessKeySecret>
    bucket-name: <Bucket 名>
```

如需使用 AI 功能，在 `BotService.java` 中替换 DashScope API Key：
```java
private static final String API_KEY = "sk-xxxxxxxxxxxxxxxx";
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
# 或直接运行 ChillChatApplication.java
```

后端监听：
- REST API → `http://localhost:8080`
- WebSocket → `ws://localhost:9090/ws`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

---

## 🧠 ChillBot 使用示例

| 用户说 | ChillBot 行为 |
|---|---|
| 帮我发个帖子说"今天天气真好" | 发出 `create_post` 工具调用，向用户确认后执行 |
| 帮我给 Alice 发消息说我晚点到 | 发出 `send_message` 工具调用，确认后代发私信 |
| 我的好友有哪些？ | 调用 `get_friend_list` 并返回好友列表 |
| 我最近有点焦虑 | 正常情感陪伴回复，不触发工具 |

---

## 📄 License

MIT
