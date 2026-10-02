"""模型封装：LLM 走 DeepSeek 官方，Embedding / Rerank 走硅基流动 SiliconFlow。"""
# 导入 typing.Any：用于标注 rerank 返回值的通用元素类型（dict 内部值类型不定）
from typing import Any

# httpx：通用 HTTP 客户端（这里用于调用 SiliconFlow 的 /rerank 私有端点）
import httpx
# langchain-openai：提供 OpenAI 兼容接口的对话模型（ChatOpenAI）与向量模型（OpenAIEmbeddings）封装
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

# 导入全局配置（API Key、模型名、基地址等）
from config import settings


# 对话模型工厂函数：返回 DeepSeek 官方对话模型实例，供各业务节点复用
def get_llm() -> ChatOpenAI:
    """对话模型（DeepSeek 官方 deepseek-v4-flash，OpenAI 兼容接口）。"""
    # 统一封装对话模型：后续所有节点（意图识别/改写/生成）都通过本函数取实例，
    # 换模型只改 config.py 一处。base_url 指向 DeepSeek 官方，走 OpenAI 兼容协议。
    # 构造 ChatOpenAI 实例：模型名/Key/基地址/温度/最大 token 均取自配置，网络超时 60 秒
    return ChatOpenAI(
        model=settings.llm_model,  # 对话模型名
        api_key=settings.deepseek_api_key,  # DeepSeek 官方 API Key
        base_url=settings.deepseek_base_url,  # DeepSeek 官方基地址（OpenAI 兼容）
        temperature=settings.llm_temperature,  # 采样温度（控制回答随机性）
        max_tokens=settings.llm_max_tokens,  # 单次生成最大 token 数
        timeout=60,  # 请求超时：60 秒（LLM 生成可能较慢）
    )


# 向量模型工厂函数：返回 BGE-M3 向量模型实例（用于文本 embedding）
def get_embeddings() -> OpenAIEmbeddings:
    """向量模型（BGE-M3，SiliconFlow；DeepSeek 官方不提供 embedding）。"""
    # DeepSeek 官方 API 只提供对话模型，不提供 embedding/rerank，
    # 因此向量检索链路改用硅基流动 SiliconFlow（BGE-M3），走 OpenAI 兼容的 embeddings 接口。
    # 构造 OpenAIEmbeddings 实例：基地址指向 SiliconFlow，走其 OpenAI 兼容的 /embeddings 接口
    return OpenAIEmbeddings(
        model=settings.embedding_model,  # 向量模型名（BGE-M3）
        api_key=settings.siliconflow_api_key,  # SiliconFlow API Key
        base_url=settings.siliconflow_base_url,  # SiliconFlow 基地址
    )


# 重排序函数：对检索候选按与 query 的相关性做精排
def rerank(query: str, documents: list[str], top_n: int = 5) -> list[dict[str, Any]]:
    """重排序（BGE-Reranker-v2-M3，SiliconFlow /v1/rerank 端点，非标准 OpenAI 接口，用 httpx 调用）。

    返回按相关性降序的 [{"index": int, "relevance_score": float}]。
    """
    # rerank 端点是 SiliconFlow 私有协议（/rerank），OpenAI SDK 无法覆盖，故直接用 httpx 调用。
    # 待重排文档为空：直接返回空列表，避免发起无意义的 HTTP 请求
    if not documents:
        return []
    # 调用 SiliconFlow 的 /rerank 端点：携带 Bearer 认证头，请求体含模型/query/文档/top_n
    resp = httpx.post(
        f"{settings.siliconflow_base_url}/rerank",  # 拼接出 rerank 端点完整 URL
        headers={"Authorization": f"Bearer {settings.siliconflow_api_key}"},  # Bearer Token 认证
        json={
            "model": settings.rerank_model,  # 重排模型名
            "query": query,  # 查询文本
            "documents": documents,  # 待重排的文档列表
            "top_n": top_n,  # 需要返回相关性最高的条数
        },
        timeout=30,  # 超时 30 秒（重排耗时通常较短）
    )
    # 响应状态码非 2xx 时抛出异常，由调用方（vectorstore.search_courses）捕获并降级处理
    resp.raise_for_status()
    # 解析响应体为 JSON
    data = resp.json()
    # 按 relevance_score 降序排列：调用方据此取前几条作为最终答案依据。
    # 失败降级策略在调用方（vectorstore.search_courses）处理：rerank 异常时退回融合结果。
    # 用生成器提取每条结果的 index 与分数，再按分数降序排序，返回精排后的结果列表
    return sorted(
        ({"index": r["index"], "relevance_score": r["relevance_score"]} for r in data.get("results", [])),
        key=lambda x: x["relevance_score"],  # 按相关性分数排序
        reverse=True,  # 降序：最相关的排最前
    )
