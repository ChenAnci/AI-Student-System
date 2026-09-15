"""硅基流动 SiliconFlow 模型封装：LLM / Embedding / Rerank。"""
from typing import Any

import httpx
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from config import settings


def get_llm() -> ChatOpenAI:
    """对话模型（DeepSeek-V3）。"""
    return ChatOpenAI(
        model=settings.llm_model,
        api_key=settings.siliconflow_api_key,
        base_url=settings.siliconflow_base_url,
        temperature=settings.llm_temperature,
        max_tokens=settings.llm_max_tokens,
        timeout=60,
    )


def get_embeddings() -> OpenAIEmbeddings:
    """向量模型（BGE-M3）。"""
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.siliconflow_api_key,
        base_url=settings.siliconflow_base_url,
    )


def rerank(query: str, documents: list[str], top_n: int = 5) -> list[dict[str, Any]]:
    """重排序（BGE-Reranker-v2-M3，SiliconFlow /v1/rerank 端点，非标准 OpenAI 接口，用 httpx 调用）。

    返回按相关性降序的 [{"index": int, "relevance_score": float}]。
    """
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
    return sorted(
        ({"index": r["index"], "relevance_score": r["relevance_score"]} for r in data.get("results", [])),
        key=lambda x: x["relevance_score"],
        reverse=True,
    )
