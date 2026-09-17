# AI 学生信息管理系统（AI-Student-System）

一个面向高校的全栈学生信息管理系统，覆盖学生、教师、教学秘书（管理员）三角色，集成 AI 学业智能助手。

## 功能特性

### 学生端
- **学业仪表盘**：已修学分 / 毕业要求 / GPA 概览，成绩分数段与课程成绩对比图表
- **选课中心**：课程搜索（关键词/教师/课程号）与学分筛选、选课概览统计（已选门数/已获学分/可选余量）、时间冲突自动标记、一键选课退课
- **我的课表**：查看本学期课程与周课表
- **成绩查询**：按课程查看成绩（分数段、通过标记、**单科绩点**），顶部汇总已修总学分与**平均学分绩（GPA）**
- **学分与绩点**：成绩**发布后**且 **≥60 分**（正常标记）才计入已修学分；GPA 按学分加权计算（Σ(单科绩点×学分) ÷ 总学分），绩点换算 ≥90→4.0 / ≥80→3.0 / ≥70→2.0 / ≥60→1.0
- **站内通知**：顶栏铃铛 + 未读角标，选课成功/成绩发布/调课等自动通知 + 老师/管理员手动通知实时推送，通知中心（已读管理）
- **AI 智能助手**：基于 LangGraph 智能体，查询学业分析、课程推荐、成绩解读
- **自助修改密码**：顶栏"修改密码"入口，需校验旧密码 + 新密码强度（8-20 位且含字母与数字），改密后旧令牌立即失效（强制重新登录）

### 教师端
- **我的课程**：创建/编辑/发布课程（周课表选择器排课）、指定授课
- **成绩管理**：Excel 导入 / 在线录入成绩、提交审核、查看审核状态（可页内直接选择课程进入）
- **成绩统计**：所授课程平均分、通过率、分数段分布图表（实时反映录入成绩，可手动刷新）
- **站内通知**：向自己授课课程的学生发送通知；课程时间/地点变更自动发送调课通知

### 教秘端（管理员）
- **数据统计**：全校学生/教师/课程/选课总览，专业分布、院系课程、选课排行、分数段图表（仅统计已发布成绩）
- **账号管理**：添加/编辑教职工与学生信息、批量 Excel 导入导出、**重置密码**（保留管理员重置权限）、冻结/启用
- **课程管理**：课程增删改查、发布控制、指派授课教师（周课表 7×12 矩阵排课）；已发布课程支持调课（仅时间/地点）
- **成绩审核**：待审核/已审核/已发布成绩流审核
- **选课监控**：课程选课人数、余量监控、代学生选课
- **站内通知**：按学生/班级/专业/课程/全部学生发送通知

### 站内通知系统（WebSocket）
- **实时推送**：学生在线时通过 WebSocket 即时收到通知；离线消息落库、上线自动补齐
- **发送方式**：管理员/教师手动发送（精确选人 / 按班级 / 按专业 / 按课程 / 全部学生）+ 系统自动触发（选课成功、成绩发布、调课变更）
- **通知中心**：收件箱分页、未读角标、标记已读 / 全部已读；教师与管理员可查看发送记录
- **安全认证**：WebSocket 连接后通过首条 `AUTH` 消息携带 JWT 认证（不放入 URL，避免日志暴露）；未认证连接超时关闭（4401）
- **权限边界**：教师仅能向自己授课课程的学生发送（服务端强制校验）；已读操作校验属主

### 登录方式
- **账号密码登录**：工号/学号 + 密码（bcrypt 校验、失败锁定防爆破）
- **GitHub OAuth 登录**：使用 GitHub 账号授权登录；首次授权后绑定现有工号/学号账号，之后一键登录（绑定关系存 `oauth_binding` 表，含 state 防 CSRF、`provider_uid` 唯一约束防重复绑定竞态）
- **密码管理**：用户自助修改密码（旧密码校验 + 强度校验）；管理员可对任意账号**重置密码**

### 安全设计
- JWT 登录鉴权 + 接口级角色权限（fail-closed，未命中规则默认 403）
- **JWT 令牌吊销**：用户表维护 `token_version`，改密/重置密码/禁用/改角色时 +1，验签时比对数据库版本号——**旧令牌在过期前也立即失效**（防已窃取令牌复用）
- **自助修改密码**：需校验旧密码 + 强度（8-20 位含字母数字）；管理员可重置任意账号密码（保留重置权限）
- 登录失败锁定防爆破、bcrypt 密码哈希、账号状态（休学/冻结）联动拦截
- **未发布成绩遮蔽**：学生成绩单仅返回"已发布"成绩的分数与绩点，未发布课程分数返回空（防提前泄露）
- 选课容量行级锁（SELECT … FOR UPDATE）防并发超选、成绩 0-100 校验
- Redis 限流（登录/授权码）与登录失败计数用原子 Lua 脚本（INCR+EXPIRE），Redis 故障降级放行
- 数据库/AI 服务密码与密钥全部环境变量注入，不入库；**数据库使用专用低权账号 `sms_app`（仅授本库 CRUD，禁止 root 直连）**
- **AI 服务请求体大小限制**（64KB，Content-Length 预检 + 流式截断，chunked 编码同样拦截）；Spring 侧 AiBodySizeFilter 亦按实际读取字节数计数
- AI 服务独立 JWT 验签 + 限流 + 仅本机监听；**启动时校验 JWT_SECRET / MYSQL_PASSWORD / DEEPSEEK_API_KEY 缺失即拒绝启动**
- 跨域白名单、CORS 配置外置、Swagger 文档鉴权
- GitHub OAuth：回调 state 一次性校验（10 分钟过期）防 CSRF；`providerUid` 绑定防一码多用；Client Secret 仅环境变量注入不入库
- 缓存序列化多态反序列化白名单（仅允许项目包与 JDK 值类型），防 Redis 投毒触发 gadget
- WebSocket 通知认证：JWT 经连接后首条 `AUTH` 消息传递（不出现于 URL），未认证连接超时关闭；会话空闲超时显式配置（60s）
- 通知发送权限：教师仅可发给自己授课课程的学生（服务端强校验）；已读操作校验属主（防越权）
- **ECharts tooltip 渲染转义**：课程名等动态数据经 HTML 转义后插入 tooltip，防存储型 XSS
- **500 异常统一包装**：对外返回通用消息 + `traceId`，服务端日志同 ID 记录完整堆栈（不泄露内部细节、可定位）
- 登录失败提示统一为"账号或密码错误"（不区分账号类型，防枚举）

### 演示数据
- 内置 100+ 条真实数据：学生 59 人、教师 18 人、课程 33 门、选课成绩 373 条（含学分/GPA 汇总、审核流程数据，成绩覆盖录入/待审核/待发布/已发布各阶段）
- 新增账号初始密码统一 `123456`；**登录后请通过"修改密码"自助改密，管理员亦可重置任意账号密码**

### 文档
- 项目解析与答辩指南：[docs/项目解析-答辩指南.md](docs/项目解析-答辩指南.md)（架构分析 / 目录与文件作用 / 业务流程 / 亮点 / 答辩 Q&A）
- 站内通知设计文档：`docs/superpowers/specs/2026-09-16-websocket-notifications-design.md`
- 站内通知实施计划：`docs/superpowers/plans/2026-09-16-websocket-notifications.md`

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts + Pinia + WebSocket |
| 后端 | Spring Boot 2.7 + Java 17 + MyBatis-Plus + MySQL 8.0 + Redis + WebSocket |
| AI 服务 | Python + FastAPI + LangChain / LangGraph + ChromaDB + BM25（jieba） |
| AI 模型 | LLM：DeepSeek 官方 `deepseek-v4-flash`；向量/重排：SiliconFlow BGE-M3 / BGE-Reranker-v2-M3 |
| 安全 | JWT（HS256）+ 令牌版本吊销 + BCrypt + 角色权限拦截器 + 多态反序列化白名单 + 低权数据库账号 |

## 系统架构

```
┌─────────────────────────────────────────────┐
│              前端 Vue 3 (5173)               │
│  WebSocket(通知实时推送) ◄──── 学生在线会话   │
└──────────────┬──────────────────────────────┘
               │ REST (HTTP 200 + body.code)
┌──────────────▼──────────────────────────────┐
│      Spring Boot 后端 (8080)                │
│   JWT 鉴权 / 角色权限 / 业务逻辑 / 通知推送   │
└──────┬───────────────────────────┬──────────┘
       │                           │ 内部 HTTP（透传登录态）
┌──────▼───────────┐     ┌────────▼──────────────────────┐
│   MySQL 8.0      │     │  FastAPI AI 服务              │
│ 学生/课程/成绩   │◄────│  (8000, 仅本机 127.0.0.1)     │
│ 通知/审核/选课   │     │  LangGraph：提问重写→混合检索 │
└──────┬───────────┘     │  ChromaDB 向量 + BM25 关键词  │
       │ 只读             └────────┬──────────────────────┘
       └─────────────────►         │ OpenAI 兼容 API
┌───────────────┐      ┌───────────┴───────────────┐
│  Redis (6379) │      ▼                           ▼
│ 限流/锁定/缓存 │  DeepSeek 官方 API      SiliconFlow API
│ 通知会话注册表 │  deepseek-v4-flash     BGE-M3 / BGE-Reranker
└───────────────┘  （对话生成）          （混合检索向量化 / 重排）
```

> **说明**：
> - AI 服务独立部署、仅监听本机回环地址；Spring 通过内部 HTTP 透传登录态（Bearer JWT），AI 服务独立验签 + 限流。
> - AI 服务**直连 MySQL（只读）**：启动时同步课程目录构建 ChromaDB 向量库与 BM25 关键词索引，运行时查询学生数据；与 Spring 共用同一数据库。
> - 「选课建议 / 自由问答」走 **提问重写 → 混合检索（向量 + BM25 → RRF 融合 → BGE-Reranker 重排）→ LLM 生成**；DeepSeek 负责对话生成，SiliconFlow 负责向量化与重排。

## AI 智能助手架构

AI 服务（`backend-ai/`）独立部署，基于 **FastAPI + LangGraph 智能体编排 + 混合检索（向量 + BM25）**，为三类角色提供学业问答与选课建议。

### 模型供应（双供应商）

| 用途 | 供应商 | 模型 / 接口 |
|---|---|---|
| 对话生成（LLM） | DeepSeek 官方 | `deepseek-v4-flash`，OpenAI ChatCompletions 兼容接口 |
| 向量化（Embedding） | SiliconFlow | `BAAI/bge-m3` |
| 重排序（Rerank） | SiliconFlow | `BAAI/bge-reranker-v2-m3`（`/rerank` 端点） |

> DeepSeek 官方不提供 embedding/rerank，故「选课建议」的向量检索继续走 SiliconFlow；未配置 `SILICONFLOW_API_KEY` 时仅选课建议不可用，主问答不受影响。

### LangGraph 工作流

```
classify（意图识别）→ fetch（查库）
   │
   ├─【选课建议 / 自由问答】→ retrieve（提问重写 + 混合检索 + rerank）→ generate（LLM 生成）
   └─【成绩查询 / 课程分析】──────────────────────→ generate（LLM 生成）
```

| 节点 | 职责 |
|---|---|
| `classify` | 意图识别：学业查询 / 选课建议 / 课程分析 / 自由问答 |
| `fetch` | 按角色 + 学号拉取学生数据（学生只能查自己，管理员按目标学号） |
| `retrieve` | 选课建议：LLM 提问重写 → 混合检索（向量 + BM25 → RRF 融合）→ BGE-Reranker 重排；自由问答：以提问检索课程目录 |
| `generate` | 组装 prompt（系统提示 + 历史 + 检索数据）→ DeepSeek 生成回答 |

「选课建议」的课程召回采用**提问重写 + 混合检索**：先用 LLM 将学生画像与原始提问改写为多角度查询（专业方向 / 兴趣技能 / 学分与时间偏好），再分别做 BGE-M3 向量召回 + jieba 分词 BM25 关键词召回，经 Reciprocal Rank Fusion（RRF）融合后由 BGE-Reranker 重排取 top5；「自由问答」也接入课程目录检索以扩大 RAG 覆盖面。BM25 语料直接来自 MySQL，embedding 不可用时关键词检索仍可用。可用 `backend-ai/test_retrieval.py` 评估各策略的 Recall@K / Precision@K / MRR。

### 安全与限流

- Spring 签发 JWT，AI 服务**独立验签**（HS256 + 过期校验），不信任任何自声明请求头
- 按用户 60 秒滑动窗口限流（默认 10 次/分钟），防刷接口消耗模型额度
- 仅监听 `127.0.0.1`，端口由 `AI_PORT` 控制（默认 8000）
- 全部 SQL 参数化并按令牌学号强制绑定，学生无法查询他人数据

## 项目结构

```
├── backend/          # Spring Boot 后端
│   └── src/main/java/com/example/sms/
│       ├── controller/   # REST 接口
│       ├── service/      # 业务逻辑
│       ├── mapper/       # MyBatis-Plus 数据访问
│       ├── config/       # JWT/CORS/权限拦截/Redis/WebSocket 配置
│       ├── websocket/    # WebSocket 会话注册表 + 通知处理器（AUTH 认证/心跳）
│       └── vo/ dto/ entity/ util/
├── backend-ai/       # FastAPI AI 服务（LangGraph 智能体）
│   ├── agents/       # 意图识别 / 检索 / 回答生成 / 工具
│   ├── models/       # LangGraph State + 请求响应模型
│   ├── prompts/      # 智能体提示词
│   ├── workflow.py   # LangGraph 工作流组装
│   ├── llm.py        # LLM（DeepSeek）/ Embedding·Rerank（SiliconFlow）封装
│   ├── vectorstore.py# 混合检索（向量 + BM25 → RRF 融合 → rerank）
│   ├── bm25.py       # jieba 分词 + BM25 关键词索引
│   ├── test_retrieval.py  # 检索质量评估（Recall@K / Precision@K / MRR）
│   └── db.py         # 参数化数据库访问
├── frontend/         # Vue 3 前端
│   └── src/views/    # student/ teacher/ admin 三角色页面
└── sql/
    ├── init.sql              # 数据库初始化脚本（建库建表 + 演示数据 + 外键约束 + 令牌版本列 + 唯一索引）
    ├── create_app_user.sql   # 创建专用低权账号 sms_app（仅授本库 CRUD，替代 root 直连，安全加固）
    ├── oauth_binding.sql     # GitHub OAuth 绑定表（可选，历史库升级用；init.sql 已含）
    └── notifications.sql     # 站内通知表（可选，历史库升级用；init.sql 已含）
```

## 快速开始

### 1. 初始化数据库

```sql
mysql -uroot -p < sql/init.sql              # 建库建表 + 演示数据（已含 OAuth 绑定表与通知表）
mysql -uroot -p < sql/create_app_user.sql   # 创建专用低权账号 sms_app（安全加固，必执行）
```

> 历史库升级（已存在数据时）：仅执行缺失的 `sql/oauth_binding.sql` / `sql/notifications.sql`（幂等 IF NOT EXISTS），并按需为 staff/student 补 `token_version` 列。

### 2. 启动 Redis（6379）

```bash
redis-server                                    # 已安装 / 已在 PATH 时
# 便携版（本机无安装时）：直接运行解压目录中的 redis-server.exe，例如
# C:\Users\ASUS\AppData\Local\Temp\redis-x64\redis-server.exe
redis-cli ping                                  # 返回 PONG 即就绪
```

> Redis 用于登录失败锁定/限流、选课缓存、OAuth state 等。未启动时系统会**降级放行**（日志打 WARN，功能基本可用），但 **GitHub OAuth 依赖 Redis 存储 state**——启用 GitHub 登录前务必先启动 Redis。

### 3. 启动后端（8080）

```bash
cd backend
# 注入密钥与数据库凭据（环境变量）
export JWT_SECRET='<64字节随机密钥>'
export DB_USERNAME=sms_app
export DB_PASSWORD='<sms_app 密码>'
export GITHUB_CLIENT_ID='<GitHub OAuth App Client ID>'         # 可选：启用 GitHub 登录
export GITHUB_CLIENT_SECRET='<GitHub OAuth App Client Secret>' # 可选：启用 GitHub 登录
# export GITHUB_REDIRECT_URI='http://localhost:8080/api/oauth/github/callback'  # 可选：自定义回调地址
mvn spring-boot:run
```

### 4. 启动 AI 服务（8000，仅本机监听）

```bash
cd backend-ai
cp .env.example .env   # 必填 DEEPSEEK_API_KEY（对话模型）；选课建议需另填 SILICONFLOW_API_KEY
python main.py
```

### 5. 启动前端（5173）

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

## 默认账号（初始密码均为 `123456`，登录后请自助改密）

| 角色 | 账号 | 说明 |
|---|---|---|
| 教学秘书 | `admin` | 全功能管理 |
| 教师 | `T1001` | 成绩录入 / 课程管理 |
| 学生 | `S20230001` | 选课 / 查成绩 / AI 助手 |

## 环境变量

| 变量 | 用途 | 位置 |
|---|---|---|
| `JWT_SECRET` | JWT 签名密钥（≥32字符），**与 AI 服务共享** | 后端 / backend-ai/.env |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库专用低权账号（`sms_app`）凭据 | 后端启动时注入 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接（默认 `localhost:6379` 无密码） | 后端启动时注入 |
| `CORS_ALLOWED_ORIGINS` | 前端域名白名单 | 后端启动时注入 |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth App 凭据（可选，启用 GitHub 登录） | 后端启动时注入 |
| `GITHUB_REDIRECT_URI` | GitHub 授权回调地址（默认 `http://localhost:8080/api/oauth/github/callback`） | 后端启动时注入 |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` | AI 服务数据库连接（只读，`sms_app`） | backend-ai/.env |
| `DEEPSEEK_API_KEY` | AI 对话模型密钥（`deepseek-v4-flash`，必填） | backend-ai/.env |
| `SILICONFLOW_API_KEY` | AI 向量检索密钥（选课建议使用） | backend-ai/.env |
| `AI_PORT` | AI 服务监听端口（默认 8000，仅本机） | backend-ai/.env |

> **安全提示**：`.env`、真实密钥与安全审查报告均已被 `.gitignore` 排除，请勿将任何真实密钥提交到仓库。

## License

MIT
