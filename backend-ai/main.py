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
    # 启动时同步一次课程向量库（把 MySQL 中的已发布课程向量化写入 Chroma + 重建 BM25），
    # 保证服务上线即具备检索能力，无需等到第一次提问才初始化。
    try:
        n = vectorstore.sync_catalog()
        logger.info("课程向量库同步完成：%s 门", n)
    except Exception as e:  # 无 API Key 或 DB 暂不可用时不阻塞启动
        # 同步失败不阻断服务启动：向量检索不可用时检索链路会自动降级
        # （BM25 关键词检索 / 纯数据库查询兜底），因此这里只告警不抛错。
        logger.warning("课程向量库同步失败（启动后可重试）：%s", e)
    yield


app = FastAPI(
    title="AI 智能分析服务",
    lifespan=lifespan,
    # P-2：交互文档默认关闭（仅调试时 .env 设 ENABLE_DOCS=true 开启），与后端 knife4j 基线对齐
    docs_url="/docs" if settings.enable_docs else None,
    redoc_url="/redoc" if settings.enable_docs else None,
    openapi_url="/openapi.json" if settings.enable_docs else None,
)


@app.middleware("http")
async def security_headers_middleware(request, call_next):
    """P-6：补齐安全响应头（部署到边缘网关前的服务内兜底）。"""
    response = await call_next(request)
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    response.headers.setdefault("Cache-Control", "no-store")
    return response


@app.get("/health")
def health():
    return {"status": "ok", "service": "sms-ai"}


def _b64url_decode(s: str) -> bytes:
    # JWT 的 header/payload 使用 Base64URL 编码（无填充、-/_ 代替 +/）。
    # 先按 4 字节对齐补齐 "=" 填充位，再转标准 urlsafe_b64decode 还原原始字节。
    pad = "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + pad)


def verify_jwt(token: str, secret: str) -> Optional[dict]:
    """校验 Spring 后端签发的 HS256 JWT（验签 + 过期校验），返回 claims 或 None。"""
    if not secret:
        # 未配置共享密钥时一律视为无效：宁可用不了，也不放行任何未验签的令牌。
        return None
    try:
        header, payload, signature = token.split(".")
        header_json = json.loads(_b64url_decode(header))
        # 只接受 HS256：防止攻击者把 alg 改成 none 或其它弱算法绕过验签（算法混淆攻击）。
        if header_json.get("alg") != "HS256":
            return None
        claims = json.loads(_b64url_decode(payload))
        exp = claims.get("exp")
        # 过期校验：exp 缺失或已过期都拒绝，防止伪造"永不过期"的令牌。
        if exp is None or exp < time.time():
            return None
        # 本地用共享密钥重算 HMAC-SHA256 签名，与令牌携带的签名比对。
        # 独立验签，不信任任何转发头（X-User-No 等）：身份只能来自 Spring 签发的令牌本身，
        # 避免网关转发链上被任意伪造身份头。
        signing_input = f"{header}.{payload}".encode("utf-8")
        expected = base64.urlsafe_b64encode(
            hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
        ).rstrip(b"=").decode("ascii")
        # compare_digest 为常量时间比较，避免时序侧信道泄露签名信息。
        if not hmac.compare_digest(expected, signature):
            return None
        return claims
    except Exception:
        # 任何解析/解密异常（格式错误、非法 base64、字段缺失）一律按无效令牌处理，不外泄细节。
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
        # 用双端队列记录该用户最近的时间戳，队头最旧、队尾最新。
        q = _rate_log.get(user_no)
        if q is None:
            q = deque()
            _rate_log[user_no] = q
        # 弹出窗口外的旧时间戳（队头 <= cutoff 即为过期），保持队列只含窗口内请求。
        while q and q[0] <= cutoff:
            q.popleft()
        # 窗口内请求数已满则拒绝；否则记录本次请求时间。
        # 滑动窗口（而非固定分钟窗口）能避免"整点边界前一秒狂刷、下一窗口重置"的绕过问题。
        if len(q) >= RATE_LIMIT_PER_MINUTE:
            return False
        q.append(now)
        return True


@app.post("/api/chat", response_model=ChatResponse)
def chat(
    req: ChatRequest,
    authorization: str = Header(default=""),
):
    # 认证三步：必须有 Bearer 前缀 → JWT 验签/过期校验 → 从 claims 取身份与角色。
    # 身份完全来自 Spring 签发的令牌 claims，接口参数与请求头里的学号均不可信，
    # 后续 DB 查询用的学号严格取自已验签的令牌（见 db 层强制绑定）。
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    claims = verify_jwt(authorization[len("Bearer "):].strip(), settings.jwt_secret)
    if claims is None:
        raise HTTPException(status_code=401, detail="认证令牌无效或已过期")

    user_no = str(claims.get("userNo") or "")
    if not user_no:
        raise HTTPException(status_code=401, detail="认证令牌缺少用户标识")
    # 角色只读可信来源（令牌），非法值一律回退为学生，避免被提权成管理员。
    role = str(claims.get("roleType") or "STUDENT").upper()
    if role not in ("STUDENT", "ADMIN"):
        role = "STUDENT"

    # 按用户限流：放在认证之后，只对已认证用户计数（未认证的恶意请求已被 401 拦掉）。
    if not rate_allowed(user_no):
        raise HTTPException(status_code=429, detail="请求过于频繁，请稍后再试")

    # 组装 LangGraph 初始状态：把 HTTP 请求映射成工作流需要的字段。
    # 历史只保留最近 6 条，控制传给 LLM 的上下文长度（防超长、省 token）。
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
        # 工作流内部未捕获异常统一在此兜底：记录堆栈后返回 503，不向客户端暴露内部细节。
        logger.exception("AI 工作流调用失败")
        raise HTTPException(status_code=503, detail="AI 服务内部错误，请稍后重试")

    answer = result.get("answer")
    if not answer:
        # 生成节点可能因 LLM 调用失败未产出回答，统一按服务不可用处理。
        raise HTTPException(status_code=503, detail="AI 服务暂不可用，请稍后重试")
    return ChatResponse(answer=answer)


if __name__ == "__main__":
    # 仅监听本机回环地址，端口由 .env 的 AI_PORT 控制（默认 8000）
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=settings.ai_port)
