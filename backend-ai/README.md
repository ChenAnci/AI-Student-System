# AI 智能分析服务（backend-ai）

独立于主系统的 AI 分析服务，为学生提供**学习情况查询、选课建议、课程分析、自由学业问答**。

- 技术栈：Python（本机环境 3.12）+ FastAPI + LangChain + LangGraph + ChromaDB
- 模型：硅基流动 SiliconFlow（DeepSeek-V3 对话 / BGE-M3 向量 / BGE-Reranker-v2-M3 重排）
- 数据：直连主系统 MySQL（`student_management`，只读），身份由 Spring 后端透传 Bearer JWT，本服务独立验签

## 目录结构

```
backend-ai/
├── main.py            # FastAPI 入口：GET /health、POST /api/chat
├── config.py          # 配置读取（.env）
├── db.py              # MySQL 只读查询（按学号绑定参数）
├── llm.py             # SiliconFlow LLM / Embedding / Rerank 封装
├── vectorstore.py     # ChromaDB 课程向量库
├── models/            # LangGraph State + 请求响应模型
├── agents/            # 意图识别 / 工具 / 检索重排 / 生成
├── workflow.py        # LangGraph 工作流组装
├── prompts/           # 提示词
└── requirements.txt
```

## 快速开始

```bash
# 1. 安装依赖（使用本机 Python）
pip install -r requirements.txt

# 2. 配置密钥
copy .env.example .env
# 编辑 .env，填入 SILICONFLOW_API_KEY（https://console.siliconflow.cn/ 创建）
# 并将 JWT_SECRET 设置为与 Spring 后端一致的密钥（后端通过环境变量 JWT_SECRET 注入）

# 3. 启动服务（仅监听本机回环地址；端口由 .env 的 AI_PORT 控制，默认 8000）
python main.py
# 或指定端口：python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/api/chat` | 学生问答。请求头须带 `Authorization: Bearer <JWT>`（由 Spring 登录签发），body：`{"message": "...", "history": [{"role": "user"\|"assistant", "content": "..."}]}` |

## 安全

- `.env` 不入库，API Key 仅存本机
- 仅监听 `127.0.0.1`，不对外暴露端口
- 每个请求必须携带 Spring 签发的有效 JWT（HS256 验签 + 过期校验），角色从令牌 claims 中读取，**不再信任任何自声明请求头（X-User-No / X-Role 等）**
- 所有 SQL 强制绑定令牌中的学号，学生只能查自己的数据
- 缺失或无效令牌返回 401
