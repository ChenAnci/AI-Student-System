"""意图识别节点：LLM 分类，失败时关键词兜底。"""
import json
import re
from pathlib import Path

from langchain_core.messages import HumanMessage, SystemMessage

from llm import get_llm
from models.state import AIState

INTENT_FILE = Path(__file__).resolve().parent.parent / "prompts" / "intent.md"

VALID_INTENTS = ("QUERY_STUDY", "COURSE_RECOMMEND", "COURSE_ANALYSIS", "FREE_QA")

_KEYWORD_RULES = [
    (["选课", "推荐", "建议选", "下学期选", "帮我选", "有什么课", "上什么课"], "COURSE_RECOMMEND"),
    (["成绩", "学分", "GPA", "绩点", "课表", "及格", "挂了", "过没过", "修了", "学习情况", "学业"], "QUERY_STUDY"),
    (["分析", "怎么样", "适合我吗", "值不值得", "难不难", "这门课"], "COURSE_ANALYSIS"),
]


def _fallback(query: str) -> str:
    for kws, intent in _KEYWORD_RULES:
        if any(k in query for k in kws):
            return intent
    return "FREE_QA"


def intent_node(state: AIState) -> AIState:
    if state.get("error"):
        return state
    query = state["query"]
    state["intent"] = "FREE_QA"
    state["target_course"] = ""
    try:
        prompt = INTENT_FILE.read_text(encoding="utf-8")
        resp = get_llm().invoke(
            [SystemMessage(content=prompt), HumanMessage(content=query)]
        )
        m = re.search(r"\{.*\}", str(resp.content), re.S)
        data = json.loads(m.group(0)) if m else {}
        intent = str(data.get("intent", "")).upper()
        if intent in VALID_INTENTS:
            state["intent"] = intent
            state["target_course"] = str(data.get("target_course", "")).strip()
            return state
    except Exception:
        pass
    state["intent"] = _fallback(query)
    return state
