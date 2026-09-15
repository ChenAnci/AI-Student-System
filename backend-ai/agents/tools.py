"""数据查询节点：按角色与目标学号拉取学生数据（强制绑定学号）。

- 学生：只能查自己（user_no）
- 管理员：查 target_no（页面选择），未传时从提问中提取学号（S 开头数字）
"""
import re

import db
from models.state import AIState


def _resolve_target_no(state: AIState) -> str | None:
    if state.get("role") != "ADMIN":
        return state.get("user_no")
    target = (state.get("target_no") or "").strip()
    if target:
        return target
    m = re.search(r"S\d{6,}", state["query"])
    return m.group(0) if m else None


def query_node(state: AIState) -> AIState:
    if state.get("error"):
        return state

    target = _resolve_target_no(state)
    if not target:
        state["error"] = "no_target"
        state["answer"] = "请先选择要查询的学生（或在提问中包含学号），例如：查一下 S20230002 的成绩。"
        return state

    # 学生显式查询他人（页面选择了他人，或提问中携带他人学号）：明确拒绝，而非静默返回本人数据
    role = state.get("role")
    user_no = state.get("user_no") or ""
    if role != "ADMIN":
        explicit = (state.get("target_no") or "").strip()
        mentioned = re.search(r"S\d{6,}", state.get("query") or "")
        mentioned_no = mentioned.group(0) if mentioned else ""
        if (explicit and explicit != user_no) or (mentioned_no and mentioned_no != user_no):
            state["error"] = "permission_denied"
            state["answer"] = "根据权限限制，您只能查询本人的学习情况，无法查看其他同学的信息。"
            return state

    student = db.get_student(target)
    if student is None:
        state["error"] = "student_not_found"
        state["answer"] = f"未找到学号为 {target} 的学生，请确认学号是否正确。"
        return state

    state["student_profile"] = student
    intent = state["intent"]
    results: dict = {"student": student}

    if intent in ("QUERY_STUDY", "FREE_QA"):
        results["grades"] = db.get_grades(target)
        results["schedule"] = db.get_schedule(target)
    elif intent == "COURSE_RECOMMEND":
        results["grades"] = db.get_grades(target)
        results["available"] = db.get_available_courses(target)
    elif intent == "COURSE_ANALYSIS":
        keyword = state.get("target_course") or state["query"]
        results["course_detail"] = db.get_course_detail(keyword)

    state["tool_results"] = results
    return state
