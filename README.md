# AI 学生信息管理系统（AI-Student-System）

一个面向高校的全栈学生信息管理系统，覆盖学生、教师、教学秘书（管理员）三角色，集成 AI 学业智能助手。

## 功能特性

### 学生端
- **学业仪表盘**：已修学分 / 毕业要求 / GPA 概览，成绩分数段与课程成绩对比图表
- **选课中心**：浏览课程、容量/冲突校验、一键选课退课
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
- **课程管理**：课程增删改查、发布控制、指派授课教师
- **成绩审核**：待审核/已审核/已发布成绩流审核
- **选课监控**：课程选课人数、余量监控、代学生选课

### 安全设计
- JWT 登录鉴权 + 接口级角色权限（fail-closed）
- 登录失败锁定防爆破、bcrypt 密码哈希
- 选课容量行级锁防并发超选、成绩 0-100 校验
- 数据库/AI 服务密码与密钥全部环境变量注入，不入库

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts + Pinia |
| 后端 | Spring Boot 2.7 + Java 17 + MyBatis-Plus + MySQL 8.0 |
| AI 服务 | Python + FastAPI + LangChain / LangGraph + ChromaDB（向量检索） |
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
┌──────▼───────────┐     ┌────────▼──────────┐
│   MySQL 8.0      │     │  FastAPI AI 服务   │
│  学生/课程/成绩   │     │  (8000, 仅本机)    │
└──────────────────┘     │  LangGraph + LLM   │
                         │  ChromaDB 向量库   │
                         └───────────────────┘
```

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
│   ├── prompts/      # 智能体提示词
│   └── db.py         # 参数化数据库访问
├── frontend/         # Vue 3 前端
│   └── src/views/    # student/ teacher/ admin 三角色页面
└── sql/init.sql      # 数据库初始化脚本
```

## 快速开始

### 1. 初始化数据库

```sql
mysql -uroot -p < sql/init.sql
```

### 2. 启动后端（8080）

```bash
cd backend
# 注入密钥与数据库凭据（环境变量）
export JWT_SECRET='<64字节随机密钥>'
export DB_USERNAME=root
export DB_PASSWORD='<数据库密码>'
mvn spring-boot:run
```

### 3. 启动 AI 服务（8000，仅本机监听）

```bash
cd backend-ai
cp .env.example .env   # 填写 SILICONFLOW_API_KEY 等
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
| `MYSQL_PASSWORD` | AI 服务数据库密码 | backend-ai/.env |
| `SILICONFLOW_API_KEY` | LLM 服务密钥 | backend-ai/.env |

> **安全提示**：`.env`、真实密钥与安全审查报告均已被 `.gitignore` 排除，请勿将任何真实密钥提交到仓库。

## License

MIT
