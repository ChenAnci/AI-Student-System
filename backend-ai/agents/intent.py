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


# 关键词兜底规则表：LLM 分类失败时按"命中关键词"粗判意图。
# 规则顺序即优先级（先选课、再成绩、后分析），命中任意关键词即返回，全部未命中归为自由问答。
def _fallback(query: str) -> str:
    for kws, intent in _KEYWORD_RULES:
        if any(k in query for k in kws):
            return intent
    return "FREE_QA"


def intent_node(state: AIState) -> AIState:
    # 任一前置节点已置 error（如 fetch 阶段权限拒绝），直接短路返回，不再消耗 LLM。
    if state.get("error"):
        return state
    query = state["query"]
    # 默认值先置为 FREE_QA：即使分类失败，也保证后续路由总有一个合法意图可用。
    state["intent"] = "FREE_QA"
    state["target_course"] = ""
    try:
        # 用 LLM 做意图识别：比纯关键词更鲁棒，能处理口语化表达。
        # 提示词在 prompts/intent.md，要求 LLM 输出 JSON（含 intent 与 target_course）。
        prompt = INTENT_FILE.read_text(encoding="utf-8")
        resp = get_llm().invoke(
            [SystemMessage(content=prompt), HumanMessage(content=query)]
        )
        # 用正则从回复里抠出第一段 {...} JSON：LLM 输出可能夹带解释文字，直接 json.loads 会失败。
        m = re.search(r"\{.*\}", str(resp.content), re.S)
        data = json.loads(m.group(0)) if m else {}
        intent = str(data.get("intent", "")).upper()
        # 白名单校验：LLM 乱输出的意图值一律不采信（防注入恶意意图字符串影响路由）。
        if intent in VALID_INTENTS:
            state["intent"] = intent
            state["target_course"] = str(data.get("target_course", "")).strip()
            return state
    except Exception:
        # LLM 调用失败 / 返回非 JSON：静默降级，不中断整个工作流。
        pass
    # 降级路径：LLM 不可用或输出不合规时，用关键词规则兜底分类。
    state["intent"] = _fallback(query)
    return state
