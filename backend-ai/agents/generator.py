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
        # 选课建议：加载专用提示词（recommend.md），喂画像 + 已修成绩 + 可选课程 + 检索推荐课程，
        # 让 LLM 依据"学生已修情况 + 数据库可选池 + 向量检索偏好"综合给建议。
        extra = PROMPTS.joinpath("recommend.md").read_text(encoding="utf-8")
        return (
            f"{extra}\n\n## 学生画像\n{profile}\n"
            f"## 已修课程成绩\n{_dump(results.get('grades'))}\n"
            f"## 可选课程\n{_dump(results.get('available'))}\n"
            f"## 向量检索优先推荐的课程\n{_dump([r['course_name'] for r in state.get('retrieved', [])])}"
        )
    if intent == "COURSE_ANALYSIS":
        # 课程分析：只需画像 + 目标课程详情，让 LLM 判断"这门课适不适合该学生"。
        return (
            f"\n## 学生画像\n{profile}\n"
            f"## 目标课程信息\n{_dump(results.get('course_detail'))}\n"
            "\n请结合学生已修情况分析该课程是否适合，并给出选课注意事项。"
        )
    # 默认分支（QUERY_STUDY / FREE_QA）：画像 + 成绩单 + 课表 + 检索结果 + 原问题，
    # 覆盖"查成绩/课表/学习情况"和自由问答两类请求。
    return (
        f"\n## 学生画像\n{profile}\n"
        f"## 成绩单\n{_dump(results.get('grades'))}\n"
        f"## 课表\n{_dump(results.get('schedule'))}\n"
        f"## 检索到的相关课程\n{_dump([r['course_name'] for r in state.get('retrieved', [])])}\n"
        f"## 学生问题\n{state['query']}"
    )


def generate_node(state: AIState) -> AIState:
    # 前置节点已置 error（权限拒绝/学生不存在/未指定目标）：不再调 LLM，
    # 直接回显前置节点写好的错误提示（answer），避免把错误状态当正常问题去生成回答。
    if state.get("error"):
        state["answer"] = state.get("answer") or "抱歉，暂时无法回答这个问题，请稍后重试。"
        return state

    # 组消息序列：系统提示（人设/边界）→ 历史对话（user/assistant 交替）→ 本次完整数据体。
    # 历史作为多轮上下文，让回答能引用之前聊过的内容；本轮数据体即 _build_body 组装的上下文。
    system = PROMPTS.joinpath("system.md").read_text(encoding="utf-8")
    messages = [SystemMessage(content=system)]
    for h in state.get("history") or []:
        messages.append(
            HumanMessage(content=h["content"]) if h.get("role") == "user"
            else AIMessage(content=h["content"])
        )
    messages.append(HumanMessage(content=_build_body(state)))

    # 调用 DeepSeek 生成最终回答；LLM 异常会向上抛，由 FastAPI 层统一捕获返回 503。
    resp = get_llm().invoke(messages)
    state["answer"] = str(resp.content).strip()
    return state
