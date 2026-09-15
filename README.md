# AI 学生信息管理系统（AI-Student-System）

一个面向高校的全栈学生信息管理系统，覆盖学生、教师、教学秘书（管理员）三角色，集成 AI 学业智能助手。

## 功能特性

### 学生端
- **学业仪表盘**：已修学分 / 毕业要求 / GPA 概览，成绩分数段与课程成绩对比图表
- **选课中心**：课程搜索（关键词/教师/课程号）与学分筛选、选课概览统计（已选门数/已获学分/可选余量）、时间冲突自动标记、一键选课退课
- **我的课表**：查看本学期课程与周课表
- **成绩查询**：按课程查看成绩（含分数段、通过标记）
- **AI 智能助手**：基于 LangGraph 智能体，查询学业分析、课程推荐、成绩解读

### 教师端
- **我的课程**：创建/编辑/发布课程（周课表选择器排课）、指定授课
- **成绩管理**：Excel 导入 / 在线录入成绩、提交审核、查看审核状态
- **成绩统计**：所授课程平均分、通过率、分数段分布图表

### 教秘端（管理员）
- **数据统计**：全校学生/教师/课程/选课总览，专业分布、院系课程、选课排行、分数段图表
- **账号管理**：添加/编辑教职工与学生信息、批量 Excel 导入导出、重置密码、冻结/启用
- **课程管理**：课程增删改查、发布控制、指派授课教师（周课表 7×12 矩阵排课）
- **成绩审核**：待审核/已审核/已发布成绩流审核
- **选课监控**：课程选课人数、余量监控、代学生选课

### 登录方式
- **账号密码登录**：工号/学号 + 密码（bcrypt 校验、失败锁定防爆破）
- **GitHub OAuth 登录**：使用 GitHub 账号授权登录；首次授权后绑定现有工号/学号账号，之后一键登录（绑定关系存 `oauth_binding` 表，含 state 防 CSRF）

### 安全设计
- JWT 登录鉴权 + 接口级角色权限（fail-closed，未命中规则默认 403）
- 登录失败锁定防爆破、bcrypt 密码哈希、账号状态（休学/冻结）联动拦截
- 选课容量行级锁（SELECT … FOR UPDATE）防并发超选、成绩 0-100 校验
- 数据库/AI 服务密码与密钥全部环境变量注入，不入库；AI 服务独立 JWT 验签 + 限流 + 仅本机监听
- 跨域白名单、CORS 配置外置、Swagger 文档鉴权
- GitHub OAuth：回调 state 一次性校验（10 分钟过期）防 CSRF；`providerUid` 绑定防一码多用；Client Secret 仅环境变量注入不入库

### 演示数据
- 内置约 100+ 条真实数据：学生 55 人、教师 12 人、课程 25 门、选课成绩 368 条（含学分/GPA 汇总）
- 新增账号初始密码统一 `123456`（首次部署后请尽快修改）

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts + Pinia |
| 后端 | Spring Boot 2.7 + Java 17 + MyBatis-Plus + MySQL 8.0 |
| AI 服务 | Python + FastAPI + LangChain / LangGraph + ChromaDB + BM25（jieba） |
| AI 模型 | LLM：DeepSeek 官方 `deepseek-v4-flash`；向量/重排：SiliconFlow BGE-M3 / BGE-Reranker-v2-M3 |
| 安全 | JWT（HS256）+ BCrypt + 角色权限拦截器 |

## 系统架构

```
┌─────────────────────────────────────────────┐
│              前端 Vue 3 (5173)               │
└──────────────┬──────────────────────────────┘
               │ REST (HTTP 200 + body.code)
┌──────────────▼──────────────────────────────┐
│      Spring Boot 后端 (8080)                │
│   JWT 鉴权 / 角色权限 / 业务逻辑            │
└──────┬───────────────────────────┬──────────┘
       │                           │ 内部 HTTP（透传登录态）
┌──────▼───────────┐     ┌────────▼──────────────────────┐
│   MySQL 8.0      │     │  FastAPI AI 服务              │
│ 学生/课程/成绩   │◄────│  (8000, 仅本机 127.0.0.1)     │
└──────┬───────────┘     │  LangGraph：提问重写→混合检索 │
       │ 只读             │  ChromaDB 向量 + BM25 关键词  │
       └─────────────────►└────────┬──────────────────────┘
                                   │ OpenAI 兼容 API
                ┌──────────────────┴──────────────┐
                ▼                                  ▼
     DeepSeek 官方 API                   SiliconFlow API
     deepseek-v4-flash                   BGE-M3 / BGE-Reranker
     （对话生成）                          （混合检索向量化 / 重排）
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
│       ├── config/       # JWT/CORS/权限拦截/初始化
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
└── sql/init.sql      # 数据库初始化脚本
    sql/oauth_binding.sql  # GitHub OAuth 绑定关系表（可选，启用 GitHub 登录时执行）
```

## 快速开始

### 1. 初始化数据库

```sql
mysql -uroot -p < sql/init.sql
mysql -uroot -p student_management < sql/oauth_binding.sql   # 启用 GitHub 登录时执行
```

### 2. 启动后端（8080）

```bash
cd backend
# 注入密钥与数据库凭据（环境变量）
export JWT_SECRET='<64字节随机密钥>'
export DB_USERNAME=root
export DB_PASSWORD='<数据库密码>'
export GITHUB_CLIENT_ID='<GitHub OAuth App Client ID>'         # 可选：启用 GitHub 登录
export GITHUB_CLIENT_SECRET='<GitHub OAuth App Client Secret>' # 可选：启用 GitHub 登录
# export GITHUB_REDIRECT_URI='http://localhost:8080/api/oauth/github/callback'  # 可选：自定义回调地址
mvn spring-boot:run
```

### 3. 启动 AI 服务（8000，仅本机监听）

```bash
cd backend-ai
cp .env.example .env   # 必填 DEEPSEEK_API_KEY（对话模型）；选课建议需另填 SILICONFLOW_API_KEY
python main.py
```

### 4. 启动前端（5173）

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

## 默认账号（初始密码均为 `123456`，首次部署后请修改）

| 角色 | 账号 | 说明 |
|---|---|---|
| 教学秘书 | `admin` | 全功能管理 |
| 教师 | `T1001` | 成绩录入 / 课程管理 |
| 学生 | `S20230001` | 选课 / 查成绩 / AI 助手 |

## 环境变量

| 变量 | 用途 | 位置 |
|---|---|---|
| `JWT_SECRET` | JWT 签名密钥（≥32字符） | 后端启动时注入 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库凭据 | 后端启动时注入 |
| `CORS_ALLOWED_ORIGINS` | 前端域名白名单 | 后端启动时注入 |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth App 凭据（可选，启用 GitHub 登录） | 后端启动时注入 |
| `GITHUB_REDIRECT_URI` | GitHub 授权回调地址（默认 `http://localhost:8080/api/oauth/github/callback`） | 后端启动时注入 |
| `MYSQL_PASSWORD` | AI 服务数据库密码 | backend-ai/.env |
| `DEEPSEEK_API_KEY` | AI 对话模型密钥（`deepseek-v4-flash`，必填） | backend-ai/.env |
| `SILICONFLOW_API_KEY` | AI 向量检索密钥（选课建议使用） | backend-ai/.env |

> **安全提示**：`.env`、真实密钥与安全审查报告均已被 `.gitignore` 排除，请勿将任何真实密钥提交到仓库。

## License

MIT
