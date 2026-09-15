"""提问重写：将学生画像 + 用户提问改写为多个检索查询，提升召回。

仅用于「选课建议」：检索信号是学生画像而非问题措辞，多角度改写可覆盖
专业方向 / 兴趣技能 / 学分与上课时间等不同维度。LLM 调用失败时降级为单一画像查询。
"""
from __future__ import annotations

from llm import get_llm

REWRITE_PROMPT = (
    "你是课程检索查询改写助手。根据学生的画像信息和原始提问，生成 2-3 个用于检索课程目录的查询。\n"
    "要求：\n"
    "1. 每个查询覆盖不同角度（专业方向、兴趣/技能关键词、学分或上课时间偏好等）\n"
    "2. 每个查询是一句自然语言，独立成行，不要编号、不要解释\n"
    "学生画像：{profile}\n"
    "原始提问：{query}"
)


def rewrite_queries(profile: str, query: str) -> list[str]:
    """返回改写后的检索查询列表；LLM 异常时降级为 [profile]。"""
    try:
        resp = get_llm().invoke(REWRITE_PROMPT.format(profile=profile, query=query))
        lines = [ln.strip() for ln in str(resp.content).splitlines() if ln.strip()]
        if lines:
            return lines[:3]
    except Exception:
        pass
    return [profile]
