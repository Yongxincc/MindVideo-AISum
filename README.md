<div align="center">

  <img src="docs/assets/logo.svg" alt="MindVideo Logo" width="88"/>
  <br/><br/>

  <h1 align="center">MindVideo-AISum</h1>
  <p align="center"><strong>智能视频内容理解平台</strong></p>

  <br/>

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

</div>

---

## 📑 目录

| | |
| :--- | :--- |
| [✨ 项目预览](#preview) | [🎬 功能演示](#demo) |
| [🏗 系统架构](#architecture) | [💡 核心能力](#features) |
| [🛠 技术栈](#tech-stack) | [🚀 快速启动](#quick-start) |
| [👥 贡献者](#contributors) | |

---

<a id="preview"></a>

## ✨ 项目预览

<p align="center">
  <img src="docs/assets/preview.png" alt="MindVideo 工作台首页" width="920" />
</p>

<p align="center">
  <em>登录后可上传本地视频或粘贴链接；任务卡片支持转写、AI 分析、下载音频、自定义命名，侧栏展示摘要与 RAG 多轮问答。</em>
</p>

**MindVideo-AISum** 是一个集成 **JWT 用户鉴权**、视频上传、音频提取及 AI 自动总结的全链路视频内容理解平台。

针对视频处理场景中常见的 **长耗时阻塞**、**高并发资源冲突** 以及 **大文件传输不稳定** 等痛点，本项目基于 **RocketMQ + Redisson + 分片续传** 重构了异步处理链路，将「存储与播放」延伸为「理解与复用」。

- **AI 总结**：转写完成后将全文一次直连大模型（DeepSeek-V3 / 硅基流动），按模型上下文上限智能截断。
- **向视频提问**：基于 **LangChain4j** 构建 RAG Agent——语义检索 + Redis 多轮记忆 + `@Tool` 按需调用，侧栏展示回答与引用片段，历史记录持久化至 MySQL。

---

<a id="demo"></a>

## 🎬 功能演示

> **说明：** GitHub 的 README 会过滤 HTML `<video>` 标签，无法在仓库首页内嵌播放 MP4。下方 **GIF 为完整画面录屏**（与 MP4 视频轨一致）；带声音的原始 MP4 请点击下方按钮在 GitHub 文件页播放。

### 本地上传（拖拽 / 选择文件）

<p align="center">
  <a href="https://github.com/Yongxincc/MindVideo-AISum/blob/main/docs/assets/demo-local-upload.mp4">
    <img src="docs/assets/demo-local-preview.gif" alt="本地上传完整演示动图" width="860" />
  </a>
</p>

<p align="center">
  <a href="https://github.com/Yongxincc/MindVideo-AISum/blob/main/docs/assets/demo-local-upload.mp4">
    <img src="https://img.shields.io/badge/▶_原始_MP4（含音频）-本地上传-6366f1?style=for-the-badge&labelColor=18181b" alt="观看本地上传原始 MP4"/>
  </a>
</p>

支持拖拽或点击选择视频文件，大文件走分片断点续传；上传完成后接口快速返回，转写与 AI 分析在后台异步执行。

---

### 链接导入（粘贴网页 URL）

<p align="center">
  <a href="https://github.com/Yongxincc/MindVideo-AISum/blob/main/docs/assets/demo-url-upload.mp4">
    <img src="docs/assets/demo-url-preview.gif" alt="链接导入完整演示动图" width="860" />
  </a>
</p>

<p align="center">
  <a href="https://github.com/Yongxincc/MindVideo-AISum/blob/main/docs/assets/demo-url-upload.mp4">
    <img src="https://img.shields.io/badge/▶_原始_MP4（含音频）-链接导入-fbbf24?style=for-the-badge&labelColor=18181b" alt="观看链接导入原始 MP4"/>
  </a>
</p>

粘贴 B 站等平台分享链接，由 **yt-dlp** 拉取视频并写入 MinIO，后续流程与本地上传一致。

---

<a id="architecture"></a>

## 🏗 系统架构

```mermaid
flowchart TB
  subgraph Client["浏览器 (Vue 3 + Vite)"]
    U[上传区：本地 / 链接]
    G[任务网格 + 侧栏问答]
    A[JWT 登录 / 注册]
  end

  subgraph API["Spring Boot :9090"]
    C[MediaController / UserController]
    F[AuthFilter + JWT]
    Q[RocketMQ Producer]
    L[Redisson 分布式锁]
  end

  subgraph Async["异步消费与 AI"]
    M[VideoAnalysisConsumer]
    ASR[分段并行 ASR]
    SUM[全文 AI 总结]
    AG[LangChain4j VideoQaAgent]
    RET[ContentRetriever 语义检索]
    MEM[Redis ChatMemory]
    TOOL["@Tool Function Calling"]
  end

  subgraph Storage["基础设施"]
    MINIO[(MinIO 视频)]
    MYSQL[(MySQL 元数据 / 向量块 / 问答历史)]
    REDIS[(Redis 缓存 / 锁 / 分片 / 记忆)]
    RMQ[RocketMQ]
  end

  A --> F
  U --> C
  G --> C
  C --> F
  C --> MINIO
  C --> Q
  Q --> RMQ
  RMQ --> M
  M --> L
  M --> ASR
  ASR --> SUM
  SUM --> RET
  G --> AG
  AG --> RET
  AG --> MEM
  AG --> TOOL
  RET --> MYSQL
  C --> REDIS
  C --> MYSQL
  M --> MYSQL
  M --> MINIO
```

---

<a id="features"></a>

## 💡 核心能力

- **上传与导入**：本地拖拽上传、链接导入（yt-dlp），大文件分片断点续传
- **异步处理**：RocketMQ 解耦，上传后快速返回，转写与分析后台执行
- **语音转写**：FFmpeg 提取音频，长视频分段并行 ASR
- **AI 总结**：转写全文直连大模型生成摘要
- **RAG 问答**：LangChain4j 语义检索 + 多轮对话，侧栏展示回答与引用
- **稳定可靠**：JWT 鉴权、MD5 去重复用、Redisson 分布式锁防并发冲突

---

<a id="tech-stack"></a>

## 🛠 技术栈

Vue 3 · Spring Boot · LangChain4j · RocketMQ · Redis · MySQL · MinIO · FFmpeg · yt-dlp

---

<a id="quick-start"></a>

## 🚀 快速启动

**环境：** Docker、Java 17+、Maven、Node.js 18+、FFmpeg、yt-dlp

```bash
# 1. 启动中间件
docker-compose up -d
.\scripts\init-db.ps1

# 2. 配置后端（复制示例文件并填入 API Key、FFmpeg / yt-dlp 路径等）
cp server/src/main/resources/application.properties.example server/src/main/resources/application.properties

# 3. 启动后端
cd server && mvn spring-boot:run

# 4. 启动前端
cd client && npm install && npm run dev
```

浏览器访问 http://localhost:5173 ，注册账号后即可使用。

---

<a id="contributors"></a>

## 👥 贡献者

- [Yongxincc](https://github.com/Yongxincc)

---

## 写在最后
如果你：

- 觉得有用，想拿来学习或二次开发
- 跑起来遇到问题，愿意提 Issue 反馈
- 或者单纯觉得「这玩意儿有点意思」

**欢迎点个 ⭐ Star**，这是对我继续维护和改进最大的鼓励。

👉 [给 MindVideo-AISum 点个 Star](https://github.com/Yongxincc/MindVideo-AISum)

谢谢你看看到这里 🙏
