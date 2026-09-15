"""LangGraph 工作流状态定义。"""
from typing import Any, TypedDict


class AIState(TypedDict):
    user_no: str                      # 登录账号（学生学号 / 管理员工号）
    role: str                         # STUDENT | ADMIN
    target_no: str                    # 管理员指定查询的目标学号（学生端为空）
    query: str                        # 本次提问
    history: list[dict]               # 历史消息 [{role, content}]
    intent: str                       # QUERY_STUDY | COURSE_RECOMMEND | COURSE_ANALYSIS | FREE_QA
    target_course: str                # 课程分析意图下的目标课程关键词
    student_profile: dict[str, Any]   # 学生基本信息
    tool_results: dict[str, Any]      # 数据查询结果
    retrieved: list[dict]             # 向量检索 + 重排结果
    answer: str                       # 最终回答
    error: str | None                 # 错误信息
