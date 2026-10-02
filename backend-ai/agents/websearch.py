"""联网搜索节点：Tavily 搜索（仅 FREE_QA 意图触发）。

自由问答（FREE_QA）时调 Tavily 联网搜索，把 title/url/snippet 存入 state["web_results"]。
TAVILY_API_KEY 未配置或调用失败时降级为空列表（不阻断工作流，与 SiliconFlow 可选依赖一致）。
"""
# 模块 docstring：本文件实现"联网搜索节点"，仅自由问答意图触发 Tavily 搜索。

# import 区域：日志、HTTP 客户端、应用配置与工作流状态类型。
import logging

import httpx

from config import settings
from models.state import AIState

# 获取业务日志器：统一以 "sms-ai" 为 logger 名，便于日志归类与过滤。
logger = logging.getLogger("sms-ai")

# Tavily 搜索接口地址。
TAVILY_URL = "https://api.tavily.com/search"
# 最多返回 3 条搜索结果（控制上下文体积）。
MAX_RESULTS = 3
# HTTP 请求超时 8 秒：搜索只是补充信息，超时就放弃，不拖慢主流程。
TIMEOUT_SECONDS = 8


def tavily_search(query: str, api_key: str, client: httpx.Client | None = None) -> list[dict]:
    """调用 Tavily /search，返回 [{"title","url","snippet"}]（最多 MAX_RESULTS 条）。

    client 可注入 mock（测试用）；默认用 httpx 模块级函数直连。
    """
    # 支持注入 mock 客户端（测试用）；未注入时用 httpx 模块级函数直连真实接口。
    http = client if client is not None else httpx
    resp = http.post(
        TAVILY_URL,
        json={
            "api_key": api_key,  # 请求体：API 密钥
            "query": query,  # 请求体：搜索关键词（用户提问）
            "max_results": MAX_RESULTS,  # 请求体：返回条数上限
            "search_depth": "basic",  # 请求体：basic 深度（更快、省额度，够用）
            "include_answer": False,  # 请求体：不需要 Tavily 生成摘要，只要原始搜索结果
        },
        timeout=TIMEOUT_SECONDS,
    )
    # HTTP 状态码非 2xx 时抛异常（由上层捕获后降级）。
    resp.raise_for_status()
    # 解析响应 JSON。
    data = resp.json()
    # 规范化后的结果列表。
    out = []
    for r in data.get("results", [])[:MAX_RESULTS]:
        # 遍历 Tavily 返回的结果（再截断一次，双保险保证不超过 MAX_RESULTS）。
        out.append({
            "title": str(r.get("title", "")),  # 标题
            "url": str(r.get("url", "")),  # 原文链接
            "snippet": str(r.get("content", "")),  # 摘要片段（字段名由 content 映射为 snippet）
        })
    # 返回规范化后的搜索结果。
    return out


def web_search_node(state: AIState, client: httpx.Client | None = None) -> AIState:
    """联网搜索节点：仅 FREE_QA 触发；失败/未配置一律降级为空列表，不阻断工作流。"""
    # 前置节点已置 error 或非 FREE_QA：直接短路返回（不影响其它意图）。
    if state.get("error") or state.get("intent") != "FREE_QA":
        state["web_results"] = []
        return state
    # 读取配置中的 Tavily API 密钥。
    api_key = settings.tavily_api_key
    if not api_key:
        # 密钥未配置：记录警告并跳过联网搜索（自由问答仍可用内部数据回答）。
        logger.warning("TAVILY_API_KEY 未配置，联网搜索跳过（仅影响自由问答的联网补充）")
        state["web_results"] = []
        return state
    # 取用户提问作为搜索关键词。
    query = state.get("query") or ""
    try:
        # 执行搜索并把结果写入状态。
        state["web_results"] = tavily_search(query, api_key, client=client)
    except Exception:
        # 联网失败不阻断回答：内库数据仍可用，仅丢失联网补充。
        logger.warning("Tavily 联网搜索失败，本次跳过", exc_info=True)
        state["web_results"] = []  # 降级为空列表
    return state
