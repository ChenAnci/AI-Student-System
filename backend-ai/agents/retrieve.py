"""检索节点（选课建议 / 自由问答）：改写查询 → 混合检索（向量 + BM25 + rerank）→ 去重合并。

- 选课建议（COURSE_RECOMMEND）：以学生画像为基线，经 LLM 提问重写为多角度查询
- 自由问答（FREE_QA）：直接以用户提问检索课程目录（扩大 RAG 覆盖面）
"""
import vectorstore
from agents.rewrite import rewrite_queries
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
    intent = state.get("intent")
    if intent == "COURSE_RECOMMEND":
        # 提问重写：画像 + 原始提问 → 多角度查询（LLM 失败时降级为单一画像查询）
        queries = rewrite_queries(_profile_text(state), state.get("query") or "")
    else:
        # 自由问答：以用户提问直接检索课程目录
        queries = [state.get("query") or ""]

    merged: list[dict] = []
    seen: set[int] = set()
    for q in queries:
        if not q.strip():
            continue
        for r in vectorstore.search_courses(q, top_k=10):
            if r["course_id"] not in seen:
                seen.add(r["course_id"])
                merged.append(r)
    # 跨查询按 rerank 分数降序融合后再截断，避免查询1独占 top5 导致提问重写多角度收益丢失
    merged.sort(key=lambda r: r["score"], reverse=True)
    state["retrieved"] = merged[:5]
    return state
