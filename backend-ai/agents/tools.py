"""数据查询节点：按角色与目标学号拉取学生数据（强制绑定学号）。

- 学生：只能查自己（user_no）
- 管理员：查 target_no（页面选择），未传时从提问中提取学号（S 开头数字）
"""
# 模块 docstring：本文件实现"数据查询节点"，按角色与目标学号从数据库拉取学生数据。

# import 区域：正则（提取学号）、数据库访问模块、工作流状态类型。
import re

import db
from models.state import AIState


def _resolve_target_no(state: AIState) -> str | None:
    """确定本次要查询的学生学号：学生锁本人，管理员取页面选择或从提问中提取。

    入参 state：工作流状态（含 role、user_no、target_no、query）。
    返回：目标学号；无法确定时返回 None。
    """
    # 决定"本次要查哪个学生的数据"：
    # - 学生：强制锁定为令牌里的自己（user_no），无论请求里传了什么学号都无效；
    # - 管理员：优先用页面传入的 target_no，没传则从提问中正则提取 S 开头学号。
    # 学号格式 S + 至少 6 位数字，与业务侧学号规范一致，避免误匹配普通数字。
    if state.get("role") != "ADMIN":
        # 非管理员（学生）：直接锁定为本人学号，从根上防止越权查询。
        return state.get("user_no")
    # 页面传入的目标学号，先去首尾空白再判断是否为空。
    target = (state.get("target_no") or "").strip()
    if target:
        # 管理员显式选择了学生：直接用该学号。
        return target
    # 从提问文本中匹配 S 开头、至少 6 位数字的学号（管理员未选择学生时的兜底提取）。
    m = re.search(r"S\d{6,}", state["query"])
    # 命中返回学号本身，未命中返回 None（表示本次无法确定查询对象）。
    return m.group(0) if m else None


def query_node(state: AIState) -> AIState:
    """数据查询节点：确定目标学号 → 越权校验 → 拉取学生数据，结果写回 tool_results。

    入参 state：工作流状态（含 role、user_no、target_no、query、intent）。
    返回：写入 student_profile / tool_results 或置 error / answer 后的同一 state。
    """
    # 任一前置节点已置 error，直接短路返回。
    if state.get("error"):
        return state

    # 第一步：解析本次要查的目标学号。
    target = _resolve_target_no(state)
    if not target:
        # 管理员既没选学生、提问里也没带学号：无法确定查询对象，给出引导性错误提示。
        state["error"] = "no_target"
        state["answer"] = "请先选择要查询的学生（或在提问中包含学号），例如：查一下 S20230002 的成绩。"
        return state

    # 学生显式查询他人（页面选择了他人，或提问中携带他人学号）：明确拒绝，而非静默返回本人数据
    # 越权防线：学生端显式指定了"别人的学号"时直接拒绝。
    # 这里必须显式报错而不是忽略后返回本人数据——静默替代表面上"友好"，
    # 但会让学生误以为能查别人，且行为不一致难以审计。
    # 当前请求的角色。
    role = state.get("role")
    # 当前登录账号（学生学号），无则空串。
    user_no = state.get("user_no") or ""
    if role != "ADMIN":
        # 学生端额外做越权校验。
        # 页面显式传入的学号（去空白）。
        explicit = (state.get("target_no") or "").strip()
        # 提问中携带的学号。
        mentioned = re.search(r"S\d{6,}", state.get("query") or "")
        # 提取出的学号（没有则空串）。
        mentioned_no = mentioned.group(0) if mentioned else ""
        if (explicit and explicit != user_no) or (mentioned_no and mentioned_no != user_no):
            # 学生试图查询他人：页面选中学号 ≠ 本人，或提问中学号 ≠ 本人，均判定越权。
            state["error"] = "permission_denied"
            state["answer"] = "根据权限限制，您只能查询本人的学习情况，无法查看其他同学的信息。"
            return state

    # 按学号查学生基本信息表。
    student = db.get_student(target)
    if student is None:
        # 学号在库中不存在：直接报错，避免下游拿着空画像继续跑出"查无此人式"的泛泛回答。
        state["error"] = "student_not_found"
        state["answer"] = f"未找到学号为 {target} 的学生，请确认学号是否正确。"
        return state

    # 学生画像写入状态，供后续检索/生成节点使用。
    state["student_profile"] = student
    # 读取意图，按需拉取对应的数据集。
    intent = state["intent"]
    # 结果容器：先放入学生基本信息。
    results: dict = {"student": student}

    # 按意图拉取对应的数据集，只查本次回答需要的数据（省数据库开销、减少上下文体积）：
    # - 成绩/课表/自由问答：成绩单 + 课表；
    # - 选课建议：成绩单（做画像约束）+ 可选课程池；
    # - 课程分析：目标课程详情（关键词来自意图识别阶段的 target_course 或原提问）。
    if intent in ("QUERY_STUDY", "FREE_QA"):
        # 查成绩/课表类请求：拉取成绩单与课表。
        results["grades"] = db.get_grades(target)
        results["schedule"] = db.get_schedule(target)
    elif intent == "COURSE_RECOMMEND":
        # 选课建议：需要已修成绩（做约束）+ 可选课程池（做候选集）。
        results["grades"] = db.get_grades(target)
        results["available"] = db.get_available_courses(target)
    elif intent == "COURSE_ANALYSIS":
        # 课程分析：按目标课程关键词查课程详情（关键词取自意图识别或原提问）。
        keyword = state.get("target_course") or state["query"]
        results["course_detail"] = db.get_course_detail(keyword)

    # 数据结果整体写回状态，供检索/生成节点消费。
    state["tool_results"] = results
    return state
