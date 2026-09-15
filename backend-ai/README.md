# AI 智能分析服务（backend-ai）

独立于主系统的 AI 分析服务，为学生提供**学习情况查询、选课建议、课程分析、自由学业问答**。

- 技术栈：Python（本机环境 3.12）+ FastAPI + LangChain + LangGraph + ChromaDB
- 模型：DeepSeek 官方（`deepseek-v4-flash` 对话）+ 硅基流动 SiliconFlow（BGE-M3 向量 / BGE-Reranker-v2-M3 重排，仅选课建议使用）
- 数据：直连主系统 MySQL（`student_management`，只读），身份由 Spring 后端透传 Bearer JWT，本服务独立验签

## 模型供应（双供应商）

| 用途 | 供应商 | 模型 / 接口 | 配置项 |
|---|---|---|---|
| 对话生成（LLM） | DeepSeek 官方 | `deepseek-v4-flash`（OpenAI ChatCompletions 兼容） | `DEEPSEEK_API_KEY`（必填） |
| 向量化（Embedding） | SiliconFlow | `BAAI/bge-m3` | `SILICONFLOW_API_KEY` |
| 重排序（Rerank） | SiliconFlow | `BAAI/bge-reranker-v2-m3`（`/rerank` 端点） | 同左 |

> DeepSeek 官方不提供 embedding/rerank，因此向量检索（选课建议）继续走 SiliconFlow；`SILICONFLOW_API_KEY` 未配置时仅选课建议不可用，主问答不受影响。

## LangGraph 工作流

```
classify（意图识别）→ fetch（查库）→（选课建议 / 自由问答）retrieve → generate（LLM 生成）
```

| 节点 | 文件 | 职责 |
|---|---|---|
| `classify` | `agents/intent.py` | 意图识别：学业查询 / 选课建议 / 课程分析 / 自由问答 |
| `fetch` | `agents/tools.py` | 按角色 + 学号拉取数据（学生只能查自己，管理员按目标学号） |
| `retrieve` | `agents/retrieve.py` | 选课建议：LLM 提问重写为多角度查询 → 混合检索（向量 + BM25 → RRF 融合）→ BGE-Reranker 重排；自由问答：以提问检索课程目录 |
| `generate` | `agents/generator.py` | 组装 prompt（系统提示 + 历史 + 检索数据）→ DeepSeek 生成回答 |

课程向量库在服务启动时从 MySQL 全量同步到 ChromaDB（`vectorstore.sync_catalog`，失败仅告警不阻塞启动）。

## 混合检索

选课建议的课程召回采用**混合检索**（`vectorstore.py` / `bm25.py`）：

1. **向量召回**：学生画像文本经 BGE-M3 向量化 → ChromaDB 召回 top20（语义相近）
2. **关键词召回**：jieba 分词 + BM25 在课程语料上召回 top20（精确词项匹配，独立于向量库）
3. **RRF 融合**：两路结果按 Reciprocal Rank Fusion 融合为 top20
4. **重排序**：融合结果经 SiliconFlow BGE-Reranker 重排，取 top5

BM25 语料直接来自 MySQL（不依赖向量库），embedding 不可用时关键词检索仍可用。

## 检索质量测试

`test_retrieval.py` 基于真实课程语料自动构造查询集（课程名 / 学分 / 教师名等 ground truth），对比**纯向量 / 纯 BM25 / 混合 RRF / 混合 + rerank** 四种策略，输出 Hit@K、Recall@K、Precision@K、MRR：

```bash
python test_retrieval.py               # 默认 K=5，跳过 rerank（不消耗 API）
python test_retrieval.py --rerank      # 启用 SiliconFlow rerank
python test_retrieval.py --topk 10
```

## 目录结构

```
backend-ai/
├── main.py            # FastAPI 入口：GET /health、POST /api/chat
├── config.py          # 配置读取（.env）
├── db.py              # MySQL 只读查询（按学号绑定参数）
├── llm.py             # LLM（DeepSeek 官方）/ Embedding·Rerank（SiliconFlow）封装
├── vectorstore.py     # 混合检索（向量 + BM25 → RRF 融合 → rerank）
├── bm25.py            # jieba 分词 + BM25 关键词索引
├── test_retrieval.py  # 检索质量评估（Recall@K / Precision@K / MRR）
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
# 编辑 .env，填入 DEEPSEEK_API_KEY（https://platform.deepseek.com/ 创建，对话模型必需）
# 选课建议需额外填写 SILICONFLOW_API_KEY（https://console.siliconflow.cn/ 创建）
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
- 按用户 60 秒滑动窗口限流（默认 10 次/分钟），防刷接口消耗模型额度
- 所有 SQL 强制绑定令牌中的学号，学生只能查自己的数据
- 缺失或无效令牌返回 401
