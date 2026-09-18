"""LangGraph 工作流组装。

流程：classify → fetch →（选课建议 / 自由问答时）retrieve → generate。
选课建议触发提问重写 + 混合检索；自由问答也检索课程目录（RAG 覆盖面更广）。
各节点入口均检查 error 短路，异常兜底在 FastAPI 层统一处理。
"""
from langgraph.graph import END, START, StateGraph

from agents.generator import generate_node
from agents.intent import intent_node
from agents.retrieve import retrieve_node
from agents.tools import query_node
from agents.websearch import web_search_node
from models.state import AIState


# 条件路由：fetch 节点拿到意图后决定下一步走向。
# - COURSE_RECOMMEND（选课建议）与 FREE_QA（自由问答）需要先检索课程目录：
#   选课建议要以学生画像改写查询做 RAG 召回，自由问答也需要课程目录兜底扩大覆盖面，
#   因此这两个意图必须经过 retrieve 节点。
# - 其余意图（QUERY_STUDY 成绩/课表查询、COURSE_ANALYSIS 课程分析）所需数据已在
#   fetch 阶段通过 SQL 拉取到 tool_results，无需再走检索，直接进 generate 生成回答，
#   这样可避免无谓的 LLM 改写与向量检索开销（省时省 token）。
def _route_after_query(state: AIState) -> str:
    return "retrieve" if state.get("intent") in ("COURSE_RECOMMEND", "FREE_QA") else "generate"


# 检索后的二次路由：FREE_QA 除内库课程检索外，还需联网搜索补充外部资料；
# 其余意图（COURSE_RECOMMEND）检索完直接生成，不联网。
def _route_after_retrieve(state: AIState) -> str:
    return "web_search" if state.get("intent") == "FREE_QA" else "generate"


def build_workflow():
    # 用 LangGraph StateGraph 把 4 个节点串成有向图，状态统一为 AIState（TypedDict）。
    graph = StateGraph(AIState)
    # classify：LLM 意图识别；fetch：按角色/学号拉取学生数据（SQL）；
    # retrieve：改写查询 + 混合检索；generate：组装 prompt 生成最终回答。
    graph.add_node("classify", intent_node)
    graph.add_node("fetch", query_node)
    graph.add_node("retrieve", retrieve_node)
    graph.add_node("web_search", web_search_node)
    graph.add_node("generate", generate_node)

    # 固定边：进入先分类，分类后先取数，检索完成后必然生成回答。
    graph.add_edge(START, "classify")
    graph.add_edge("classify", "fetch")
    # 唯一的分支点：fetch 之后按意图分流（见 _route_after_query）。
    # 条件边返回字符串，通过映射表落到具体节点，实现"需要检索才走 retrieve"。
    graph.add_conditional_edges("fetch", _route_after_query, {"retrieve": "retrieve", "generate": "generate"})
    # 二次分流：retrieve 之后 FREE_QA 再走 web_search（联网），其余直接生成。
    graph.add_conditional_edges("retrieve", _route_after_retrieve, {"web_search": "web_search", "generate": "generate"})
    graph.add_edge("web_search", "generate")
    graph.add_edge("generate", END)
    # compile() 把图定义编译成可调用的 Runnable，供 FastAPI 层 workflow.invoke(state) 执行。
    return graph.compile()


workflow = build_workflow()
