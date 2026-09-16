"""模型封装：LLM 走 DeepSeek 官方，Embedding / Rerank 走硅基流动 SiliconFlow。"""
from typing import Any

import httpx
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from config import settings


def get_llm() -> ChatOpenAI:
    """对话模型（DeepSeek 官方 deepseek-v4-flash，OpenAI 兼容接口）。"""
    # 统一封装对话模型：后续所有节点（意图识别/改写/生成）都通过本函数取实例，
    # 换模型只改 config.py 一处。base_url 指向 DeepSeek 官方，走 OpenAI 兼容协议。
    return ChatOpenAI(
        model=settings.llm_model,
        api_key=settings.deepseek_api_key,
        base_url=settings.deepseek_base_url,
        temperature=settings.llm_temperature,
        max_tokens=settings.llm_max_tokens,
        timeout=60,
    )


def get_embeddings() -> OpenAIEmbeddings:
    """向量模型（BGE-M3，SiliconFlow；DeepSeek 官方不提供 embedding）。"""
    # DeepSeek 官方 API 只提供对话模型，不提供 embedding/rerank，
    # 因此向量检索链路改用硅基流动 SiliconFlow（BGE-M3），走 OpenAI 兼容的 embeddings 接口。
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.siliconflow_api_key,
        base_url=settings.siliconflow_base_url,
    )


def rerank(query: str, documents: list[str], top_n: int = 5) -> list[dict[str, Any]]:
    """重排序（BGE-Reranker-v2-M3，SiliconFlow /v1/rerank 端点，非标准 OpenAI 接口，用 httpx 调用）。

    返回按相关性降序的 [{"index": int, "relevance_score": float}]。
    """
    # rerank 端点是 SiliconFlow 私有协议（/rerank），OpenAI SDK 无法覆盖，故直接用 httpx 调用。
    if not documents:
        return []
    resp = httpx.post(
        f"{settings.siliconflow_base_url}/rerank",
        headers={"Authorization": f"Bearer {settings.siliconflow_api_key}"},
        json={
            "model": settings.rerank_model,
            "query": query,
            "documents": documents,
            "top_n": top_n,
        },
        timeout=30,
    )
    resp.raise_for_status()
    data = resp.json()
    # 按 relevance_score 降序排列：调用方据此取前几条作为最终答案依据。
    # 失败降级策略在调用方（vectorstore.search_courses）处理：rerank 异常时退回融合结果。
    return sorted(
        ({"index": r["index"], "relevance_score": r["relevance_score"]} for r in data.get("results", [])),
        key=lambda x: x["relevance_score"],
        reverse=True,
    )
