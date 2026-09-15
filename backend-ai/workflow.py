"""LangGraph 工作流组装。

流程：classify → fetch →（COURSE_RECOMMEND 时）retrieve → generate。
各节点入口均检查 error 短路，异常兜底在 FastAPI 层统一处理。
"""
from langgraph.graph import END, START, StateGraph

from agents.generator import generate_node
from agents.intent import intent_node
from agents.retrieve import retrieve_node
from agents.tools import query_node
from models.state import AIState


def _route_after_query(state: AIState) -> str:
    return "retrieve" if state.get("intent") == "COURSE_RECOMMEND" else "generate"


def build_workflow():
    graph = StateGraph(AIState)
    graph.add_node("classify", intent_node)
    graph.add_node("fetch", query_node)
    graph.add_node("retrieve", retrieve_node)
    graph.add_node("generate", generate_node)

    graph.add_edge(START, "classify")
    graph.add_edge("classify", "fetch")
    graph.add_conditional_edges("fetch", _route_after_query, {"retrieve": "retrieve", "generate": "generate"})
    graph.add_edge("retrieve", "generate")
    graph.add_edge("generate", END)
    return graph.compile()


workflow = build_workflow()
