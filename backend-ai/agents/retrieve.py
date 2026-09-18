"""检索节点（选课建议 / 自由问答）：改写查询 → 混合检索（向量 + BM25 + rerank）→ 去重合并。

- 选课建议（COURSE_RECOMMEND）：以学生画像为基线，经 LLM 提问重写为多角度查询
- 自由问答（FREE_QA）：直接以用户提问检索课程目录（扩大 RAG 覆盖面）
"""
import vectorstore
from agents.rewrite import rewrite_queries
from models.state import AIState


def _profile_text(state: AIState) -> str:
    """把学生画像与成绩信息压缩成一段自然语言文本（供提问重写作输入信号）。

    入参 state：工作流状态（含 student_profile、tool_results.grades）。
    返回：拼接好的画像文本（含已修课程、未通过课程）。
    """
    # 把结构化画像/成绩压缩成一段自然语言文本，作为提问重写的输入信号。
    # 从成绩单里拆出"已修课程"与"未通过课程"：这俩是选课建议最关键的约束条件
    # （避免推荐已修/挂科相关的课），单独拼进画像让改写模型看得到。
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
    """检索节点：改写/构造查询 → 混合检索 → 跨查询去重合并，结果写回 state["retrieved"]。

    入参 state：工作流状态（含 intent、query、student_profile、tool_results）。
    返回：写入 retrieved（最多 5 条）后的同一 state。
    """
    # 任一前置节点已置 error，直接短路返回。
    if state.get("error"):
        return state
    intent = state.get("intent")
    if intent == "COURSE_RECOMMEND":
        # 提问重写：画像 + 原始提问 → 多角度查询（LLM 失败时降级为单一画像查询）
        # 选课建议的核心信号是"学生是谁"（画像），而不是"问题怎么问"，所以以画像为检索基线。
        queries = rewrite_queries(_profile_text(state), state.get("query") or "")
    else:
        # 自由问答：以用户提问直接检索课程目录
        # FREE_QA 没有画像约束，直接用原提问检索课程目录，扩大 RAG 覆盖面兜底回答。
        queries = [state.get("query") or ""]

    # 对多路改写查询逐条做混合检索，再跨查询去重合并：
    # 同一门课可能被多个角度查询都召回，seen 集合保证只保留一次。
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
    # 若不做跨查询重排，先到的查询会把 top5 占满，后面的改写查询召回的更优课程就没机会进结果。
    merged.sort(key=lambda r: r["score"], reverse=True)
    state["retrieved"] = merged[:5]
    return state
