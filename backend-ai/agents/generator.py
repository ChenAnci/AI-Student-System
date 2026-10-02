"""回答生成节点：组 prompt（系统提示 + 历史 + 数据）→ DeepSeek 官方模型生成回答。"""
# 模块 docstring：本文件实现"回答生成节点"，负责组装提示词并调用 DeepSeek 生成最终回答。

# import 区域：JSON 序列化、跨平台路径处理、langchain 消息类型、LLM 工厂与工作流状态类型。
import json
from pathlib import Path

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from llm import get_llm
from models.state import AIState

# 定位提示词目录：本文件位于 agents/ 子目录，上两级即项目根目录，再拼接 prompts 子目录。
PROMPTS = Path(__file__).resolve().parent.parent / "prompts"


def _dump(obj) -> str:
    """把对象序列化为 JSON 字符串（保留中文字符，不转义为 Unicode 转义形式）。

    入参 obj：任意可 JSON 序列化的对象（dict/list 等）。
    返回：ensure_ascii=False 的 JSON 字符串，便于 LLM 直接阅读中文内容。
    """
    # ensure_ascii=False：中文按原字符输出而非 \uXXXX 转义，方便 LLM 直接读懂中文内容。
    return json.dumps(obj, ensure_ascii=False)


def _build_body(state: AIState) -> str:
    """按意图组装本轮 LLM 的上下文正文（数据体）。

    入参 state：工作流状态（需含 intent、tool_results、student_profile、retrieved、web_results、query）。
    返回：拼装好的正文文本，追加在系统提示与历史之后。
    """
    # 读取当前意图：决定按哪种模板组装数据体（不同意图拼不同的上下文）。
    intent = state["intent"]
    # 读取数据查询节点的结果；没有则用空字典，避免后续 .get 出错。
    results = state.get("tool_results") or {}
    # 把学生画像序列化为 JSON 文本，作为各分支模板的公共输入。
    profile = _dump(state.get("student_profile"))

    if intent == "COURSE_RECOMMEND":
        # 选课建议：加载专用提示词（recommend.md），喂画像 + 已修成绩 + 可选课程 + 检索推荐课程，
        # 让 LLM 依据"学生已修情况 + 数据库可选池 + 向量检索偏好"综合给建议。
        # 读取选课专用的任务说明提示词，作为该分支的额外指令。
        extra = PROMPTS.joinpath("recommend.md").read_text(encoding="utf-8")
        return (
            f"{extra}\n\n## 学生画像\n{profile}\n"
            # 拼接：选课任务说明 + 学生画像。
            f"## 已修课程成绩\n{_dump(results.get('grades'))}\n"
            # 已修课程成绩：作为"已学过的课"约束，避免重复推荐。
            f"## 可选课程\n{_dump(results.get('available'))}\n"
            # 数据库中的可选课程池：作为候选集。
            f"## 向量检索优先推荐的课程\n{_dump([r['course_name'] for r in state.get('retrieved', [])])}"
            # 向量检索按画像/问题召回的优先课程（只取课程名列表，压缩上下文体积）。
        )
    if intent == "COURSE_ANALYSIS":
        # 课程分析：只需画像 + 目标课程详情，让 LLM 判断"这门课适不适合该学生"。
        return (
            f"\n## 学生画像\n{profile}\n"
            # 学生画像：分析"适合与否"的个体依据。
            f"## 目标课程信息\n{_dump(results.get('course_detail'))}\n"
            # 目标课程详情：被分析的那门课的信息。
            "\n请结合学生已修情况分析该课程是否适合，并给出选课注意事项。"
            # 任务指令：让 LLM 结合已修情况给出适配性结论与注意事项。
        )
    # 默认分支（QUERY_STUDY / FREE_QA）：画像 + 成绩单 + 课表 + 检索结果 + 原问题，
    # 覆盖"查成绩/课表/学习情况"和自由问答两类请求。
    body = (
        f"\n## 学生画像\n{profile}\n"
        # 学生画像：默认分支也带画像，便于回答个性化。
        f"## 成绩单\n{_dump(results.get('grades'))}\n"
        # 成绩单：查成绩/学习情况的核心数据。
        f"## 课表\n{_dump(results.get('schedule'))}\n"
        # 课表：上课时间安排。
        f"## 检索到的相关课程\n{_dump([r['course_name'] for r in state.get('retrieved', [])])}\n"
        # 检索节点召回的课程名列表（RAG 补充上下文）。
        f"## 学生问题\n{state['query']}"
        # 用户原始提问：作为最终的任务指令。
    )
    # 联网搜索资料仅 FREE_QA 时存在；作为补充上下文，并要求 LLM 标注来源（规则见 system.md）。
    web = state.get("web_results") or []
    if web:
        # 有联网资料时拼接到正文末尾，并要求引用时标注来源（外部信息不可当内部数据）。
        body += "\n## 联网搜索资料（外部信息，回答引用时须标注来源）\n" + _dump(web)
    # 返回组装好的正文文本，供 generate_node 追加到消息序列末尾。
    return body


def generate_node(state: AIState) -> AIState:
    """生成节点：组装消息序列并调用 LLM 产出最终回答，写回 state["answer"]。

    入参 state：工作流状态（含 error、history、query 及各节点产出数据）。
    返回：更新 answer 后的同一 state。
    """
    # 前置节点已置 error（权限拒绝/学生不存在/未指定目标）：不再调 LLM，
    # 直接回显前置节点写好的错误提示（answer），避免把错误状态当正常问题去生成回答。
    if state.get("error"):
        # 若前置节点没写 answer，则给一个兜底提示，保证响应非空。
        state["answer"] = state.get("answer") or "抱歉，暂时无法回答这个问题，请稍后重试。"
        return state

    # 组消息序列：系统提示（人设/边界）→ 历史对话（user/assistant 交替）→ 本次完整数据体。
    # 历史作为多轮上下文，让回答能引用之前聊过的内容；本轮数据体即 _build_body 组装的上下文。
    # 读取系统提示词：约定 LLM 的人设、回答边界与格式规则。
    system = PROMPTS.joinpath("system.md").read_text(encoding="utf-8")
    # 消息序列以系统提示开头，作为整段对话的"人设与边界"约束。
    messages = [SystemMessage(content=system)]
    for h in state.get("history") or []:
        # 遍历历史消息：按 role 还原为 Human/AI 消息，保持多轮上下文连贯。
        messages.append(
            HumanMessage(content=h["content"]) if h.get("role") == "user"
            else AIMessage(content=h["content"])
            # 三元表达式：user 角色还原为用户消息，其余还原为助手消息。
        )
    # 最后追加本轮完整数据体（用户消息），构成一次完整的 LLM 调用输入。
    messages.append(HumanMessage(content=_build_body(state)))

    # 调用 DeepSeek 生成最终回答；LLM 异常会向上抛，由 FastAPI 层统一捕获返回 503。
    resp = get_llm().invoke(messages)
    # 取出回答文本并去除首尾空白，写回状态供接口返回。
    state["answer"] = str(resp.content).strip()
    # 返回更新后的状态，由 LangGraph 结束本次工作流。
    return state
