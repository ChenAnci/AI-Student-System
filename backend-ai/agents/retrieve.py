"""向量检索节点（仅选课建议）：学生画像向量化 → Chroma 召回 → bge-reranker 重排。"""
import vectorstore
from models.state import AIState


def _profile_text(state: AIState) -> str:
    s = state.get("student_profile") or {}
    grades = (state.get("tool_results") or {}).get("grades") or []
    taken = [g["course_name"] for g in grades if g.get("course_name")]
    failed = [g["course_name"] for g in grades if g.get("score") is not None and g["score"] < 60]
    return (
        f"学号{s.get('student_no','')}，专业{s.get('major','')}，院系{s.get('department','')}，"
        f"GPA{s.get('gpa','')}，已修学分{s.get('total_earned_credits','')}，毕业要求{s.get('required_credits','')}。"
        f"已修课程：{'、'.join(taken) or '无'}。未通过课程：{'、'.join(failed) or '无'}"
    )


def retrieve_node(state: AIState) -> AIState:
    if state.get("error"):
        return state
    state["retrieved"] = vectorstore.search_courses(_profile_text(state), top_k=20)
    return state
