"""请求 / 响应模型。"""
# 模块 docstring：本文件定义 FastAPI 聊天接口的请求/响应 Pydantic 模型。

# import 区域：Literal（字面量类型）与 pydantic 的模型/字段校验类。
from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """单条历史消息：限定 role 为 user/assistant，content 有长度上限。"""
    # 单条历史消息：role 用 Literal 限定只有 user/assistant，防止客户端塞入 system 等角色影响 LLM 提示注入。
    # role 字段：仅允许 "user" / "assistant" 两个值（pydantic 自动校验，非法值请求直接 422）。
    role: Literal["user", "assistant"]
    # content 字段：必填字符串，长度上限 2000 字符，防止超大历史内容撑爆 LLM 上下文。
    content: str = Field(..., max_length=2000, description="消息内容（上限 2000 字符）")


class ChatRequest(BaseModel):
    """聊天请求体：提问内容 + 历史 + 可选目标学号（管理员用），字段均有限制。"""
    # 请求体校验：pydantic 负责字段必填、长度上限、历史条数上限，
    # 超限请求在进入业务逻辑前即被 400 拒绝（长度上限防止超大正文打爆 LLM 上下文）。
    # message 字段：必填提问内容，长度上限 500 字符。
    message: str = Field(..., max_length=500, description="提问内容")
    # history 字段：默认空列表（default_factory 保证每个实例拥有独立空列表），列表长度上限 20 条。
    history: list[ChatMessage] = Field(
        default_factory=list, max_length=20, description="最近 6 轮以内的历史（列表上限 20 条）"
    )
    # target_student_no 字段：可选（默认 None），上限 20 字符，仅管理员查询指定学生时使用。
    target_student_no: str | None = Field(
        default=None, max_length=20, description="管理员查询指定学生时传入的学号；学生端无需传"
    )


class ChatResponse(BaseModel):
    """聊天响应体：只回答案文本，不回中间检索/画像等内部数据。"""
    # 响应只回答案文本，不回中间检索/画像数据，避免把个人数据或检索细节暴露给前端。
    # answer 字段：最终回答文本。
    answer: str
