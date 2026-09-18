"""联网搜索节点：Tavily 搜索（仅 FREE_QA 意图触发）。

自由问答（FREE_QA）时调 Tavily 联网搜索，把 title/url/snippet 存入 state["web_results"]。
TAVILY_API_KEY 未配置或调用失败时降级为空列表（不阻断工作流，与 SiliconFlow 可选依赖一致）。
"""
import logging

import httpx

from config import settings
from models.state import AIState

logger = logging.getLogger("sms-ai")

TAVILY_URL = "https://api.tavily.com/search"
MAX_RESULTS = 3
TIMEOUT_SECONDS = 8


def tavily_search(query: str, api_key: str, client: httpx.Client | None = None) -> list[dict]:
    """调用 Tavily /search，返回 [{"title","url","snippet"}]（最多 MAX_RESULTS 条）。

    client 可注入 mock（测试用）；默认用 httpx 模块级函数直连。
    """
    http = client if client is not None else httpx
    resp = http.post(
        TAVILY_URL,
        json={
            "api_key": api_key,
            "query": query,
            "max_results": MAX_RESULTS,
            "search_depth": "basic",
            "include_answer": False,
        },
        timeout=TIMEOUT_SECONDS,
    )
    resp.raise_for_status()
    data = resp.json()
    out = []
    for r in data.get("results", [])[:MAX_RESULTS]:
        out.append({
            "title": str(r.get("title", "")),
            "url": str(r.get("url", "")),
            "snippet": str(r.get("content", "")),
        })
    return out


def web_search_node(state: AIState, client: httpx.Client | None = None) -> AIState:
    """联网搜索节点：仅 FREE_QA 触发；失败/未配置一律降级为空列表，不阻断工作流。"""
    # 前置节点已置 error 或非 FREE_QA：直接短路返回（不影响其它意图）。
    if state.get("error") or state.get("intent") != "FREE_QA":
        state["web_results"] = []
        return state
    api_key = settings.tavily_api_key
    if not api_key:
        logger.warning("TAVILY_API_KEY 未配置，联网搜索跳过（仅影响自由问答的联网补充）")
        state["web_results"] = []
        return state
    query = state.get("query") or ""
    try:
        state["web_results"] = tavily_search(query, api_key, client=client)
    except Exception:
        # 联网失败不阻断回答：内库数据仍可用，仅丢失联网补充。
        logger.warning("Tavily 联网搜索失败，本次跳过", exc_info=True)
        state["web_results"] = []
    return state
