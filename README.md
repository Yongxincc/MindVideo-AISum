<div align="center">
  <a href="https://github.com/Yongxincc/MindVideo-AISum">
  </a>

  <h1 align="center">MindVideo-AISum - 智能视频内容理解平台</h1>
  
  <p align="center">
    <strong>全链路异步化 / 长任务稳定性保障 / AI 智能问答 </strong>
  </p>

  <p align="center">
    <a href="https://github.com/Yongxincc/MindVideo-AISum">
      <img src="https://img.shields.io/badge/Spring%20Boot-3.0-brightgreen" alt="Spring Boot">
    </a>
    <a href="https://github.com/Yongxincc/MindVideo-AISum">
      <img src="https://img.shields.io/badge/RocketMQ-4.9-orange" alt="RocketMQ">
    </a>
    <a href="https://github.com/Yongxincc/MindVideo-AISum">
      <img src="https://img.shields.io/badge/Redisson-Lock-red" alt="Redisson">
    </a>
    <a href="https://github.com/Yongxincc/MindVideo-AISum">
      <img src="https://img.shields.io/badge/LangChain4j-AI-blueviolet" alt="LangChain4j">
    </a>
    <a href="https://github.com/Yongxincc/MindVideo-AISum">
      <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
    </a>
  </p>
</div>

<br/>

<br/>

**MindVideo-AISum** 是一个集成用户鉴权、视频上传、音频提取及 AI 自动总结的全链路视频内容理解平台。

针对视频处理场景中常见的 **“长耗时阻塞”** 、 **“高并发资源冲突”** 以及 **“大文件传输不稳定”** 等痛点，本项目抛弃了传统的同步处理模式，基于 **RocketMQ + Redisson + 分片续传** 重构了系统架构。

系统可以接入大模型api，自定义提示词，基于 Function Calling 可以实现查询信息和精准总结。

视频平台大多只解决了“存储”和“播放”的问题。MindVideo-AISum 旨在解决“理解”的问题。 它通过异步架构处理长耗时任务，利用 AI 提取核心价值，让视频不再是黑盒。

<br/>

<br/>

##  核心功能

1. 🚀 稳定上传体验

分片断点续传：针对 GB 级大文件（如 4K 课程录像），采用 Redis 维护上传分片状态。实测在 20% 丢包率弱网环境下，上传成功率从 25% 提升至 99%。

秒级响应：引入 RocketMQ 将耗时的“视频分析”动作剥离出主线程。用户上传完成后仅需 50ms 即可得到反馈，后续处理全异步化，彻底告别页面转圈卡死。

2. 🛡️ 高并发防护

分布式锁兜底：使用 Redisson + WatchDog 机制。当多个用户同时上传同一个热门公开课视频时，系统通过 MD5 内容指纹识别，利用分布式锁防止重复转码与 AI 分析，节省算力与 Token 开销。

削峰填谷：Controller 层集成 Redis 令牌桶算法，有效遏制恶意请求与突发流量，保护后端服务不被击穿。

3.  🔄 任务处理流程详解

稳健入口：文件直传 MinIO，避免应用服务器带宽瓶颈。

异步解耦：上传成功后，Controller 仅发送一条消息至 RocketMQ 即刻返回，将长耗时任务留给后台。

安全消费：消费者通过 Redisson 锁住视频 MD5，确保同一视频在同一时刻只有一个线程在处理。

智能重试：针对第三方 AI API 可能的网络抖动，设计了指数退避重试机制，确保任务最终一致性。

<br/>

## 技术栈

### 后端

SpringBoot + RocketMQ + Redis + MySQL + MyBatis Plus + MinIO + FFmpeg + LangChain4j

## 快速启动

**环境：** Docker、Java 21、Maven、Node.js 18+；视频处理需 FFmpeg，在线链接解析需 yt-dlp。

```bash
# 1. 启动中间件（MySQL / Redis / MinIO / RocketMQ）
docker-compose up -d

# 2. 配置后端：复制示例文件并填入本地密钥与工具路径
cp server/src/main/resources/application.properties.example server/src/main/resources/application.properties

# 3. 启动后端（端口 9090）
cd server && mvn spring-boot:run

# 4. 启动前端（端口 5173）
cd client && npm install && npm run dev
```

浏览器访问 http://localhost:5173 。首次使用需在 MySQL `media_db` 中创建 `users`、`media_files` 表（字段见 `server/src/main/java/com/example/server/entity/`）。

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 | http://localhost:9090 |
| MinIO 控制台 | http://localhost:9001 |
| RocketMQ Dashboard | http://localhost:8180 |

## 贡献者

- [Yongxincc](https://github.com/Yongxincc)
