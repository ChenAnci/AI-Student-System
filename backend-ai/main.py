"""FastAPI 入口：健康检查 + 学生问答。

身份由 Spring 后端签发并透传的 Bearer JWT 提供，本服务独立验签（HS256 + 过期校验），
角色从令牌 claims 中读取，不再信任任何自声明请求头。
"""
import base64
import hashlib
import hmac
import json
import logging
import threading
import time
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, Header, HTTPException

import vectorstore
from config import settings
from models.schema import ChatRequest, ChatResponse
from workflow import workflow

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("sms-ai")


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        n = vectorstore.sync_catalog()
        logger.info("课程向量库同步完成：%s 门", n)
    except Exception as e:  # 无 API Key 或 DB 暂不可用时不阻塞启动
        logger.warning("课程向量库同步失败（启动后可重试）：%s", e)
    yield


app = FastAPI(title="AI 智能分析服务", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "service": "sms-ai"}


def _b64url_decode(s: str) -> bytes:
    pad = "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + pad)


def verify_jwt(token: str, secret: str) -> Optional[dict]:
    """校验 Spring 后端签发的 HS256 JWT（验签 + 过期校验），返回 claims 或 None。"""
    if not secret:
        return None
    try:
        header, payload, signature = token.split(".")
        header_json = json.loads(_b64url_decode(header))
        if header_json.get("alg") != "HS256":
            return None
        claims = json.loads(_b64url_decode(payload))
        exp = claims.get("exp")
        if exp is None or exp < time.time():
            return None
        signing_input = f"{header}.{payload}".encode("utf-8")
        expected = base64.urlsafe_b64encode(
            hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
        ).rstrip(b"=").decode("ascii")
        if not hmac.compare_digest(expected, signature):
            return None
        return claims
    except Exception:
        return None


# ===== 简单滑动窗口限流（按用户），防止刷接口消耗 LLM 额度 =====
RATE_LIMIT_PER_MINUTE = 10
RATE_WINDOW_SECONDS = 60
_rate_log: dict[str, "deque[float]"] = {}
_rate_lock = threading.Lock()


def rate_allowed(user_no: str) -> bool:
    """60 秒滑动窗口内最多 RATE_LIMIT_PER_MINUTE 次，无窗口边界绕过问题。"""
    from collections import deque

    now = time.time()
    cutoff = now - RATE_WINDOW_SECONDS
    with _rate_lock:
        # 惰性清理：仅当活跃用户数超阈值时，移除窗口内已无请求的过期条目，防止字典无界增长
        if len(_rate_log) > 1000:
            for k in [k for k, q in _rate_log.items() if not q or q[-1] <= cutoff]:
                del _rate_log[k]
        q = _rate_log.get(user_no)
        if q is None:
            q = deque()
            _rate_log[user_no] = q
        while q and q[0] <= cutoff:
            q.popleft()
        if len(q) >= RATE_LIMIT_PER_MINUTE:
            return False
        q.append(now)
        return True


@app.post("/api/chat", response_model=ChatResponse)
def chat(
    req: ChatRequest,
    authorization: str = Header(default=""),
):
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    claims = verify_jwt(authorization[len("Bearer "):].strip(), settings.jwt_secret)
    if claims is None:
        raise HTTPException(status_code=401, detail="认证令牌无效或已过期")

    user_no = str(claims.get("userNo") or "")
    if not user_no:
        raise HTTPException(status_code=401, detail="认证令牌缺少用户标识")
    role = str(claims.get("roleType") or "STUDENT").upper()
    if role not in ("STUDENT", "ADMIN"):
        role = "STUDENT"

    if not rate_allowed(user_no):
        raise HTTPException(status_code=429, detail="请求过于频繁，请稍后再试")

    state = {
        "user_no": user_no,
        "role": role,
        "target_no": req.target_student_no or "",
        "query": req.message,
        "history": [{"role": m.role, "content": m.content} for m in req.history[-6:]],
        "intent": "",
        "target_course": "",
        "student_profile": {},
        "tool_results": {},
        "retrieved": [],
        "answer": "",
        "error": None,
    }
    try:
        result = workflow.invoke(state)
    except Exception:
        logger.exception("AI 工作流调用失败")
        raise HTTPException(status_code=503, detail="AI 服务内部错误，请稍后重试")

    answer = result.get("answer")
    if not answer:
        raise HTTPException(status_code=503, detail="AI 服务暂不可用，请稍后重试")
    return ChatResponse(answer=answer)


if __name__ == "__main__":
    # 仅监听本机回环地址，端口由 .env 的 AI_PORT 控制（默认 8000）
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=settings.ai_port)
