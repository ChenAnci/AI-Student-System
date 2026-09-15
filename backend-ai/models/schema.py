"""请求 / 响应模型。"""
from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(..., max_length=2000, description="消息内容（上限 2000 字符）")


class ChatRequest(BaseModel):
    message: str = Field(..., max_length=500, description="提问内容")
    history: list[ChatMessage] = Field(
        default_factory=list, max_length=20, description="最近 6 轮以内的历史（列表上限 20 条）"
    )
    target_student_no: str | None = Field(
        default=None, max_length=20, description="管理员查询指定学生时传入的学号；学生端无需传"
    )


class ChatResponse(BaseModel):
    answer: str
