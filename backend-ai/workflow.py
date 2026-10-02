"""LangGraph 工作流组装。

流程：classify → fetch →（选课建议 / 自由问答时）retrieve → generate。
选课建议触发提问重写 + 混合检索；自由问答也检索课程目录（RAG 覆盖面更广）。
各节点入口均检查 error 短路，异常兜底在 FastAPI 层统一处理。
"""
# LangGraph 图构建原语：START/END 为图的起点/终点哨兵节点，StateGraph 为状态机图（节点间共享状态字典）
from langgraph.graph import END, START, StateGraph

# 导入各业务节点函数：意图识别、SQL 取数、混合检索、联网搜索、回答生成
from agents.generator import generate_node
from agents.intent import intent_node
from agents.retrieve import retrieve_node
from agents.tools import query_node
from agents.websearch import web_search_node
# 工作流状态类型（TypedDict）：所有节点读写同一个状态字典
from models.state import AIState


# 条件路由：fetch 节点拿到意图后决定下一步走向。
# - COURSE_RECOMMEND（选课建议）与 FREE_QA（自由问答）需要先检索课程目录：
#   选课建议要以学生画像改写查询做 RAG 召回，自由问答也需要课程目录兜底扩大覆盖面，
#   因此这两个意图必须经过 retrieve 节点。
# - 其余意图（QUERY_STUDY 成绩/课表查询、COURSE_ANALYSIS 课程分析）所需数据已在
#   fetch 阶段通过 SQL 拉取到 tool_results，无需再走检索，直接进 generate 生成回答，
#   这样可避免无谓的 LLM 改写与向量检索开销（省时省 token）。
# 路由函数：入参为工作流状态字典，返回下一步节点的路由键（字符串）
def _route_after_query(state: AIState) -> str:
    """fetch 之后的意图分流：按意图决定下一步是检索还是直接生成。

    入参 state：工作流状态（需含 intent 字段）。
    返回："retrieve"（需要 RAG 检索）或 "generate"（直接生成回答）。
    """
    # 意图为选课建议或自由问答 → 走检索；其余意图 → 直接生成（条件表达式一行完成分流）
    return "retrieve" if state.get("intent") in ("COURSE_RECOMMEND", "FREE_QA") else "generate"


# 检索后的二次路由：FREE_QA 除内库课程检索外，还需联网搜索补充外部资料；
# 其余意图（COURSE_RECOMMEND）检索完直接生成，不联网。
# 二次路由函数：检索完成后决定是否继续联网
def _route_after_retrieve(state: AIState) -> str:
    """检索之后的二次分流：FREE_QA 意图还需联网搜索，其余直接生成。

    入参 state：工作流状态（需含 intent 字段）。
    返回："web_search"（联网搜索补充外部资料）或 "generate"（直接生成）。
    """
    # 仅自由问答需要联网补充外部资料；选课建议等直接使用内库检索结果生成回答
    return "web_search" if state.get("intent") == "FREE_QA" else "generate"


# 工作流构建函数：组装并编译 LangGraph 图
def build_workflow():
    """组装并编译 LangGraph 工作流图，返回可调用的 Runnable。

    返回：graph.compile() 的结果，供 FastAPI 层 workflow.invoke(state) 执行。
    """
    # 用 LangGraph StateGraph 把 4 个节点串成有向图，状态统一为 AIState（TypedDict）。
    # 创建状态图：所有节点共享一个 AIState 类型的状态字典，节点函数返回部分更新的状态
    graph = StateGraph(AIState)
    # classify：LLM 意图识别；fetch：按角色/学号拉取学生数据（SQL）；
    # retrieve：改写查询 + 混合检索；generate：组装 prompt 生成最终回答。
    # 依次注册 5 个节点：节点名 → 处理函数（每个函数入参为状态字典、返回需更新的字段）
    graph.add_node("classify", intent_node)  # 意图识别节点
    graph.add_node("fetch", query_node)  # SQL 取数节点
    graph.add_node("retrieve", retrieve_node)  # 检索节点
    graph.add_node("web_search", web_search_node)  # 联网搜索节点
    graph.add_node("generate", generate_node)  # 回答生成节点

    # 固定边：进入先分类，分类后先取数，检索完成后必然生成回答。
    # 起点 → classify：所有请求都必须先做意图识别
    graph.add_edge(START, "classify")
    # classify → fetch：识别完意图后立即进入取数阶段
    graph.add_edge("classify", "fetch")
    # 唯一的分支点：fetch 之后按意图分流（见 _route_after_query）。
    # 条件边返回字符串，通过映射表落到具体节点，实现"需要检索才走 retrieve"。
    # fetch 后添加条件边：按路由函数返回值映射到 retrieve 或 generate 节点
    graph.add_conditional_edges("fetch", _route_after_query, {"retrieve": "retrieve", "generate": "generate"})
    # 二次分流：retrieve 之后 FREE_QA 再走 web_search（联网），其余直接生成。
    # retrieve 后添加条件边：按路由函数返回值映射到 web_search 或 generate 节点
    graph.add_conditional_edges("retrieve", _route_after_retrieve, {"web_search": "web_search", "generate": "generate"})
    # web_search → generate：联网搜索结束后进入生成节点
    graph.add_edge("web_search", "generate")
    # generate → END：生成回答后流程结束
    graph.add_edge("generate", END)
    # compile() 把图定义编译成可调用的 Runnable，供 FastAPI 层 workflow.invoke(state) 执行。
    return graph.compile()


# 模块加载时即构建并编译工作流（模块级单例）：全局唯一，避免每次请求重复编译图
workflow = build_workflow()
