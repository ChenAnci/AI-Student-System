"""回答生成节点：组 prompt（系统提示 + 历史 + 数据）→ DeepSeek 官方模型生成回答。"""
import json
from pathlib import Path

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from llm import get_llm
from models.state import AIState

PROMPTS = Path(__file__).resolve().parent.parent / "prompts"


def _dump(obj) -> str:
    return json.dumps(obj, ensure_ascii=False)


def _build_body(state: AIState) -> str:
    intent = state["intent"]
    results = state.get("tool_results") or {}
    profile = _dump(state.get("student_profile"))

    if intent == "COURSE_RECOMMEND":
        extra = PROMPTS.joinpath("recommend.md").read_text(encoding="utf-8")
        return (
            f"{extra}\n\n## 学生画像\n{profile}\n"
            f"## 已修课程成绩\n{_dump(results.get('grades'))}\n"
            f"## 可选课程\n{_dump(results.get('available'))}\n"
            f"## 向量检索优先推荐的课程\n{_dump([r['course_name'] for r in state.get('retrieved', [])])}"
        )
    if intent == "COURSE_ANALYSIS":
        return (
            f"\n## 学生画像\n{profile}\n"
            f"## 目标课程信息\n{_dump(results.get('course_detail'))}\n"
            "\n请结合学生已修情况分析该课程是否适合，并给出选课注意事项。"
        )
    return (
        f"\n## 学生画像\n{profile}\n"
        f"## 成绩单\n{_dump(results.get('grades'))}\n"
        f"## 课表\n{_dump(results.get('schedule'))}\n"
        f"## 学生问题\n{state['query']}"
    )


def generate_node(state: AIState) -> AIState:
    if state.get("error"):
        state["answer"] = state.get("answer") or "抱歉，暂时无法回答这个问题，请稍后重试。"
        return state

    system = PROMPTS.joinpath("system.md").read_text(encoding="utf-8")
    messages = [SystemMessage(content=system)]
    for h in state.get("history") or []:
        messages.append(
            HumanMessage(content=h["content"]) if h.get("role") == "user"
            else AIMessage(content=h["content"])
        )
    messages.append(HumanMessage(content=_build_body(state)))

    resp = get_llm().invoke(messages)
    state["answer"] = str(resp.content).strip()
    return state
