<div align="center">

  <img src="docs/assets/logo.svg" alt="MindVideo Logo" width="88"/>
  <br/><br/>

  <h1 align="center">MindVideo-AISum</h1>
  <p align="center"><strong>智能视频内容理解平台</strong></p>

  [![Typing SVG](https://readme-typing-svg.demolab.com?font=Fira+Code&weight=500&size=24&duration=3800&pause=1200&color=818CF8&center=true&vCenter=true&width=640&lines=智能视频内容理解平台;读懂每一支视频 · 可搜索 · 可复用;全链路异步 · 分片续传 · AI 智能问答)](https://git.io/typing-svg)

  <br/><br/>

  <!-- 快捷入口 -->
  <a href="https://github.com/Yongxincc/MindVideo-AISum">
    <img src="https://img.shields.io/github/stars/Yongxincc/MindVideo-AISum?style=for-the-badge&logo=github&logoColor=white&labelColor=18181b&color=6366f1" alt="GitHub Stars"/>
  </a>
  <a href="#quick-start">
    <img src="https://img.shields.io/badge/快速启动-本地运行-34d399?style=for-the-badge&logo=rocket&logoColor=white&labelColor=18181b" alt="快速启动"/>
  </a>
  <a href="#demo">
    <img src="https://img.shields.io/badge/功能演示-查看视频-fbbf24?style=for-the-badge&logo=airplayvideo&logoColor=white&labelColor=18181b" alt="功能演示"/>
  </a>
  <a href="https://github.com/Yongxincc/MindVideo-AISum/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-818cf8?style=for-the-badge&labelColor=18181b" alt="MIT License"/>
  </a>

  <br/><br/>

  <!-- 核心亮点 -->
  <img src="https://img.shields.io/badge/分片断点续传-弱网可用-6366f1?style=flat-square&labelColor=27272a"/>
  <img src="https://img.shields.io/badge/RocketMQ-异步削峰-FF6A00?style=flat-square&labelColor=27272a&logo=apacherocketmq&logoColor=white"/>
  <img src="https://img.shields.io/badge/Redisson-分布式锁-DC382D?style=flat-square&labelColor=27272a&logo=redis&logoColor=white"/>
  <img src="https://img.shields.io/badge/LangChain4j-Function_Calling-8B5CF6?style=flat-square&labelColor=27272a"/>
  <img src="https://img.shields.io/badge/MinIO-对象存储-FFC107?style=flat-square&labelColor=27272a&logo=minio&logoColor=white"/>

  <br/><br/>

  ### 🛠 技术栈

  <table align="center" cellpadding="12">
    <tr>
      <td align="center" width="220"><strong>🖥 前端</strong></td>
      <td align="center" width="220"><strong>⚙️ 后端</strong></td>
      <td align="center" width="220"><strong>📦 存储 & 消息</strong></td>
      <td align="center" width="220"><strong>🤖 AI & 媒体</strong></td>
    </tr>
    <tr>
      <td align="center">
        <img src="https://img.shields.io/badge/Vue-3-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/Vite-7-646CFF?style=for-the-badge&logo=vite&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/JavaScript-ES6+-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black"/>
      </td>
      <td align="center">
        <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/MyBatis_Plus-3.5-2496ED?style=for-the-badge&logo=mybatis&logoColor=white"/>
      </td>
      <td align="center">
        <img src="https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/RocketMQ-4.9-FF6A00?style=for-the-badge&logo=apacherocketmq&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/MinIO-对象存储-FFC107?style=for-the-badge&logo=minio&logoColor=white"/>
      </td>
      <td align="center">
        <img src="https://img.shields.io/badge/LangChain4j-AI-8B5CF6?style=for-the-badge"/><br/>
        <img src="https://img.shields.io/badge/DeepSeek-大模型-1C64F2?style=for-the-badge"/><br/>
        <img src="https://img.shields.io/badge/FFmpeg-音视频-007808?style=for-the-badge&logo=ffmpeg&logoColor=white"/><br/>
        <img src="https://img.shields.io/badge/yt--dlp-链接解析-FF0000?style=for-the-badge&logo=youtube&logoColor=white"/>
      </td>
    </tr>
    <tr>
      <td colspan="4" align="center">
        <strong>🐳 部署与工具</strong><br/><br/>
        <img src="https://go.skillicons.dev/icons?i=docker,maven,nodejs,git&theme=dark"/>
        &nbsp;&nbsp;
        <img src="https://img.shields.io/badge/Docker_Compose-一键中间件-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
        <img src="https://img.shields.io/badge/Redisson-分布式锁-DC382D?style=for-the-badge&logo=redis&logoColor=white"/>
      </td>
    </tr>
  </table>

</div>

<br/>
## ✨ 项目预览

<p align="center">
  <img src="docs/assets/preview.png" alt="MindVideo 工作台首页" width="920" />
</p>

<p align="center">
  <em>上传区支持「本地文件」与「链接导入」，任务卡片可转写、AI 分析、下载音频与自定义命名。</em>
</p>

<br/>

**MindVideo-AISum** 是一个集成用户鉴权、视频上传、音频提取及 AI 自动总结的全链路视频内容理解平台。

针对视频处理场景中常见的 **长耗时阻塞**、**高并发资源冲突** 以及 **大文件传输不稳定** 等痛点，本项目基于 **RocketMQ + Redisson + 分片续传** 重构了系统架构，将「存储与播放」延伸为「理解与复用」。

系统可接入大模型 API、自定义提示词；基于 Function Calling 支持查询信息与精准总结。

<br/>

<a id="demo"></a>

## 🎬 功能演示

### 本地上传（拖拽 / 选择文件）

<p align="center">
  <img src="docs/assets/demo-local-cover.png" alt="本地上传界面示意" width="720" />
</p>

<p align="center">
  <video src="docs/assets/demo-local-upload.mp4" controls width="860">
    您的浏览器不支持 HTML5 视频，请<a href="docs/assets/demo-local-upload.mp4">下载演示</a>观看。
  </video>
</p>

支持拖拽或点击选择视频文件，大文件走分片断点续传；上传完成后接口快速返回，转写与 AI 分析在后台异步执行。

---

### 链接导入（粘贴网页 URL）

<p align="center">
  <img src="docs/assets/demo-url-cover.png" alt="链接导入界面示意" width="720" />
</p>

<p align="center">
  <video src="docs/assets/demo-url-upload.mp4" controls width="860">
    您的浏览器不支持 HTML5 视频，请<a href="docs/assets/demo-url-upload.mp4">下载演示</a>观看。
  </video>
</p>

粘贴 B 站等平台分享链接，由 **yt-dlp** 拉取视频并写入 MinIO，后续流程与本地上传一致。


## 🏗 系统架构

```mermaid
flowchart TB
  subgraph Client["浏览器 (Vue 3)"]
    U[上传区：本地 / 链接]
    G[任务网格 + 侧栏]
  end

  subgraph API["Spring Boot :9090"]
    C[MediaController]
    Q[RocketMQ Producer]
    L[Redisson 分布式锁]
  end

  subgraph Async["异步消费"]
    M[VideoAnalysisConsumer]
    ASR[ASR 转写]
    AI[LangChain4j / DeepSeek]
  end

  subgraph Storage["基础设施"]
    MINIO[(MinIO 视频)]
    MYSQL[(MySQL 元数据)]
    REDIS[(Redis 缓存 / 锁 / 分片)]
    RMQ[RocketMQ]
  end

  U --> C
  G --> C
  C --> MINIO
  C --> Q
  Q --> RMQ
  RMQ --> M
  M --> L
  M --> ASR
  M --> AI
  C --> REDIS
  C --> MYSQL
  M --> MYSQL
  M --> MINIO
```

<br/>

## 💡 核心能力

### 1. 稳定上传体验

- **分片断点续传**：Redis 维护分片状态，弱网与大文件场景更稳。
- **秒级响应**：上传完成后由 RocketMQ 异步处理分析，主线程快速返回。

### 2. 高并发防护

- **分布式锁**：Redisson + MD5 去重，同一热门视频避免重复转码与 AI 调用。
- **令牌桶限流**：保护后端，抑制突发流量。

### 3. 任务处理链路

| 阶段 | 说明 |
|------|------|
| 入口 | 文件直传 MinIO，减轻应用服务器带宽压力 |
| 解耦 | Controller 发 MQ 消息后立即返回 |
| 消费 | 消费者按视频 MD5 加锁，保证同一时刻单线程处理 |
| 重试 | 对第三方 AI API 抖动做指数退避，保证最终一致 |

<br/>

<a id="quick-start"></a>

## 🚀 快速启动

**环境：** Docker、Java 21、Maven、Node.js 18+；视频处理需 FFmpeg，在线链接解析需 yt-dlp。

### 一键启动（推荐）

| 方式 | 说明 |
|------|------|
| 双击 `start-dev.bat` | 启动 Docker 中间件 + 新开两个窗口跑后端 / 前端 |
| 双击 `start-apps.bat` | 仅启动后端 + 前端（中间件已在跑时用） |
| `.\scripts\start-all.ps1` | 同上，PowerShell 里执行 |
| `.\scripts\start-all.ps1 -SkipDocker` | 仅应用，不拉 Docker |

首次使用前请复制并填写 `server/src/main/resources/application.properties`（见下方手动步骤）。

### 只启动中间件

```powershell
# 启动 MySQL / Redis / MinIO / RocketMQ 并自动建表（需先打开 Docker Desktop）
.\scripts\start-dev.ps1
```

### 手动分步

```bash
# 1. 启动中间件（MySQL / Redis / MinIO / RocketMQ）
docker-compose up -d
.\scripts\init-db.ps1

# 2. 配置后端：复制示例文件并填入本地密钥与工具路径
cp server/src/main/resources/application.properties.example server/src/main/resources/application.properties

# 3. 启动后端（端口 9090）
cd server && mvn spring-boot:run

# 4. 启动前端（端口 5173）
cd client && npm install && npm run dev
```

浏览器访问 http://localhost:5173 。`start-dev.ps1` / `init-db.ps1` 会自动创建 `users`、`media_files` 表。

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 | http://localhost:9090 |
| MinIO 控制台 | http://localhost:9001 |
| RocketMQ Dashboard | http://localhost:8180 |


## 👥 贡献者

- [Yongxincc](https://github.com/Yongxincc)
