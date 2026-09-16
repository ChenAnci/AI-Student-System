"""请求 / 响应模型。"""
from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    # 单条历史消息：role 用 Literal 限定只有 user/assistant，防止客户端塞入 system 等角色影响 LLM 提示注入。
    role: Literal["user", "assistant"]
    content: str = Field(..., max_length=2000, description="消息内容（上限 2000 字符）")


class ChatRequest(BaseModel):
    # 请求体校验：pydantic 负责字段必填、长度上限、历史条数上限，
    # 超限请求在进入业务逻辑前即被 400 拒绝（长度上限防止超大正文打爆 LLM 上下文）。
    message: str = Field(..., max_length=500, description="提问内容")
    history: list[ChatMessage] = Field(
        default_factory=list, max_length=20, description="最近 6 轮以内的历史（列表上限 20 条）"
    )
    target_student_no: str | None = Field(
        default=None, max_length=20, description="管理员查询指定学生时传入的学号；学生端无需传"
    )


class ChatResponse(BaseModel):
    # 响应只回答案文本，不回中间检索/画像数据，避免把个人数据或检索细节暴露给前端。
    answer: str
