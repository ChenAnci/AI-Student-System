"""FastAPI 入口：健康检查 + 学生问答。

身份由 Spring 后端签发并透传的 Bearer JWT 提供，本服务独立验签（HS256 + 过期校验），
角色从令牌 claims 中读取，不再信任任何自声明请求头。
"""
# ---- 标准库导入：提供 JWT 解码/验签、日志、线程锁、时间等基础能力 ----
import base64  # Base64/Base64URL 编解码（JWT 各段解码与签名编码）
import hashlib  # 摘要算法（HMAC-SHA256 验签用）
import hmac  # HMAC 签名计算与常量时间比较
import json  # JSON 解析（JWT header/payload 反序列化）
import logging  # 日志输出
import threading  # 线程锁（保护限流字典的并发访问）
import time  # 时间戳（JWT 过期校验、限流窗口）
from contextlib import asynccontextmanager  # 把异步生成器包装为异步上下文管理器（生命周期钩子）
from typing import Optional  # 类型标注：Optional[dict] 表示"可能是 dict 也可能是 None"

# ---- 第三方/项目内导入：FastAPI Web 框架、向量库、配置、数据模型、工作流 ----
from fastapi import FastAPI, Header, HTTPException, Request  # FastAPI 应用、请求头参数、HTTP 异常、请求对象
from fastapi.responses import JSONResponse  # 直接构造 JSON 响应（限流/超限时返回）

import vectorstore  # 课程向量库（启动时同步、检索时使用）
from config import settings  # 全局配置（JWT 密钥、端口等）
from models.schema import ChatRequest, ChatResponse  # 请求/响应数据模型（Pydantic）
from workflow import workflow  # 已编译的 LangGraph 问答工作流

# 配置全局日志：级别 INFO，控制台输出，供全服务共用
logging.basicConfig(level=logging.INFO)
# 创建名为 "sms-ai" 的日志器：按服务名收集日志，便于运维过滤
logger = logging.getLogger("sms-ai")


# FastAPI 生命周期钩子（async 上下文管理器）：启动前执行 yield 之前的代码，关闭后执行其后的代码
@asynccontextmanager
async def lifespan(_: FastAPI):
    """FastAPI 生命周期钩子：服务启动时同步课程向量库，关闭时清理。

    入参 _：FastAPI 应用实例（此处用不到，仅占位）。
    """
    # 启动时同步一次课程向量库（把 MySQL 中的已发布课程向量化写入 Chroma + 重建 BM25），
    # 保证服务上线即具备检索能力，无需等到第一次提问才初始化。
    try:
        # 调用向量库全量同步函数，返回本次同步的课程数量
        n = vectorstore.sync_catalog()
        # 记录同步成功的日志（%s 为占位符，避免手动字符串拼接）
        logger.info("课程向量库同步完成：%s 门", n)
    except Exception as e:  # 无 API Key 或 DB 暂不可用时不阻塞启动
        # 同步失败不阻断服务启动：向量检索不可用时检索链路会自动降级
        # （BM25 关键词检索 / 纯数据库查询兜底），因此这里只告警不抛错。
        logger.warning("课程向量库同步失败（启动后可重试）：%s", e)
    # 挂起生命周期：把控制权交还给 FastAPI，服务开始对外处理请求；服务关闭后继续执行剩余代码（此处无清理逻辑）
    yield


# 创建 FastAPI 应用实例：title 供文档展示，lifespan 指定启动/关闭钩子
app = FastAPI(
    title="AI 智能分析服务",
    lifespan=lifespan,
    # P-2：交互文档默认关闭（仅调试时 .env 设 ENABLE_DOCS=true 开启），与后端 knife4j 基线对齐
    # 三个文档路由（/docs、/redoc、/openapi.json）均按 enable_docs 开关决定是否暴露，生产环境默认不开放
    docs_url="/docs" if settings.enable_docs else None,  # Swagger 文档路由：开启开关才挂载
    redoc_url="/redoc" if settings.enable_docs else None,  # ReDoc 文档路由：同上
    openapi_url="/openapi.json" if settings.enable_docs else None,  # OpenAPI 规范路由：同上
)


# 注册 HTTP 中间件：每个请求先进入本函数，再调用 call_next 交给后续路由处理
@app.middleware("http")
async def security_headers_middleware(request, call_next):
    """P-6：补齐安全响应头（部署到边缘网关前的服务内兜底）。"""
    # 先让后续处理链执行，拿到响应对象
    response = await call_next(request)
    # 设置响应头：禁止浏览器嗅探 MIME 类型，防止内容类型混淆攻击
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    # 设置响应头：禁止页面被嵌入 iframe，防点击劫持（clickjacking）
    response.headers.setdefault("X-Frame-Options", "DENY")
    # 设置响应头：不发送 Referer 头，避免 URL 参数（含 token）外泄到第三方
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    # 设置响应头：禁止缓存响应，避免敏感内容残留在浏览器/代理缓存中
    response.headers.setdefault("Cache-Control", "no-store")
    # 返回补全了安全头的响应
    return response


# 请求体上限（与 Spring 端 /api/ai/chat 的 64KB 限制对齐）：超限直接 413 拒绝，防内存 DoS
# 64 * 1024 = 65536 字节；聊天文本请求通常远小于此值，阈值足够宽松
MAX_BODY_BYTES = 64 * 1024


# 注册请求体大小限制中间件
@app.middleware("http")
async def body_size_limit_middleware(request: Request, call_next):
    """限制 /api/chat 请求体大小：防止超大 body 全量读入内存造成 DoS（H-2/S-4）。

    仅对聊天接口启用（健康检查等无需限制）。实现上优先用 Content-Length 头预检，
    未携带（如 chunked 传输）时按流式读取截断，读取超限即终止并返回 413。
    """
    # 只拦截 POST 且路径为 /api/chat 的请求；其它请求（如 /health）直接放行不检查
    if request.method != "POST" or request.url.path != "/api/chat":
        return await call_next(request)

    # 1) Content-Length 预检：声明长度超限直接拒绝，不读 body
    # 从请求头读取 Content-Length（客户端声明的请求体字节数）
    content_length = request.headers.get("content-length")
    # 声明长度是数字且超过上限：直接返回 413，完全不读取 body（省内存、防 DoS）
    if content_length and content_length.isdigit() and int(content_length) > MAX_BODY_BYTES:
        return JSONResponse(status_code=413, content={"detail": "请求体过大，最大 64KB"})

    # 2) 流式读取并截断：无 Content-Length（chunked）或声明值不可信时按实际字节数限制，
    #    读取到上限即终止并 413，避免 FastAPI 把整个 body 读入内存后再解析（内存 DoS 向量）。
    # 用 bytearray 累积读取到的字节（可变字节数组，适合流式逐块追加）
    received = bytearray()
    try:
        # 异步迭代请求流，逐块读取请求体内容
        async for chunk in request.stream():
            # 把当前块追加到累积缓冲区
            received.extend(chunk)
            # 累积长度超过上限：立即中断并返回 413，不再读取剩余数据
            if len(received) > MAX_BODY_BYTES:
                return JSONResponse(status_code=413, content={"detail": "请求体过大，最大 64KB"})
    except Exception:
        # 读取异常（连接中断等）按请求体错误处理，不进入业务逻辑
        return JSONResponse(status_code=400, content={"detail": "请求体读取失败"})

    # 3) 把已读的 body 放回请求对象，供后续 Pydantic 解析使用
    # 定义内部协程：把已读的 body 伪装成 ASGI http.request 消息回吐给下游，让后续解析"看到"完整请求体
    async def replay_body():
        """重放已读取的请求体：以 ASGI http.request 消息形式一次性回吐给下游。"""
        # 一次性产出整块 body；more_body=False 表示这是最后一帧（也是唯一一帧）
        yield {"type": "http.request", "body": bytes(received), "more_body": False}

    # 标记请求流已被消费，防止下游再次尝试流式读取（否则会读到空流）
    request._stream_consumed = True
    # 把已读的 body 直接挂到请求对象上，后续 Pydantic 解析时会使用这份数据
    request._body = bytes(received)
    # 继续把请求交给后续处理链
    return await call_next(request)


# 注册 GET /health 路由：健康检查接口
@app.get("/health")
def health():
    """健康检查接口：返回服务存活状态，供负载均衡/探活使用。

    返回：dict，包含 status="ok" 与服务名。
    """
    # 返回固定存活标记：负载均衡器/监控据此判断实例是否可用
    return {"status": "ok", "service": "sms-ai"}


# JWT 段解码辅助函数：把 Base64URL 字符串还原为原始字节
def _b64url_decode(s: str) -> bytes:
    """将 Base64URL 字符串解码为原始字节。

    入参 s：JWT 的 header/payload 段（Base64URL 编码，无填充、-/_ 代替 +/）。
    返回：解码后的原始字节。
    """
    # JWT 的 header/payload 使用 Base64URL 编码（无填充、-/_ 代替 +/）。
    # 先按 4 字节对齐补齐 "=" 填充位，再转标准 urlsafe_b64decode 还原原始字节。
    # 计算需要补齐的填充符个数：Base64 要求长度是 4 的倍数，-len(s) % 4 得到还差几个字符
    pad = "=" * (-len(s) % 4)
    # urlsafe_b64decode 兼容 -_ 字符集；补齐填充后解码为原始字节返回
    return base64.urlsafe_b64decode(s + pad)


# JWT 验签核心函数：验签 + 过期校验，通过则返回令牌声明，否则返回 None
def verify_jwt(token: str, secret: str) -> Optional[dict]:
    """校验 Spring 后端签发的 HS256 JWT（验签 + 过期校验），返回 claims 或 None。"""
    # 未配置共享密钥时一律视为无效：宁可用不了，也不放行任何未验签的令牌
    if not secret:
        return None
    try:
        # JWT 由三段组成（header.payload.signature），按 "." 分割成三部分
        header, payload, signature = token.split(".")
        # 解码并解析 header 段 JSON，读取算法声明（alg）
        header_json = json.loads(_b64url_decode(header))
        # 只接受 HS256：防止攻击者把 alg 改成 none 或其它弱算法绕过验签（算法混淆攻击）。
        # 算法不是 HS256：直接拒绝
        if header_json.get("alg") != "HS256":
            return None
        # 解码并解析 payload 段 JSON，得到令牌声明（含过期时间、用户标识、角色等）
        claims = json.loads(_b64url_decode(payload))
        # 取出过期时间戳（Unix 秒）
        exp = claims.get("exp")
        # 过期校验：exp 缺失或已过期都拒绝，防止伪造"永不过期"的令牌。
        # exp 为空或已过当前时间：视为过期令牌
        if exp is None or exp < time.time():
            return None
        # 本地用共享密钥重算 HMAC-SHA256 签名，与令牌携带的签名比对。
        # 独立验签，不信任任何转发头（X-User-No 等）：身份只能来自 Spring 签发的令牌本身，
        # 避免网关转发链上被任意伪造身份头。
        # 用 UTF-8 编码拼接 "header.payload" 作为 HMAC 的输入数据
        signing_input = f"{header}.{payload}".encode("utf-8")
        # 用共享密钥计算 HMAC-SHA256 摘要，再做 Base64URL 编码并去掉尾部 "=" 填充，得到期望签名
        expected = base64.urlsafe_b64encode(
            hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
        ).rstrip(b"=").decode("ascii")
        # compare_digest 为常量时间比较，避免时序侧信道泄露签名信息。
        # 常量时间比较：即使不匹配，耗时也不随内容差异变化，防止时序攻击逐步猜出签名
        if not hmac.compare_digest(expected, signature):
            return None
        # 验签与过期校验全部通过：返回令牌声明
        return claims
    except Exception:
        # 任何解析/解密异常（格式错误、非法 base64、字段缺失）一律按无效令牌处理，不外泄细节。
        return None


# ===== 简单滑动窗口限流（按用户），防止刷接口消耗 LLM 额度 =====
# 每用户每分钟允许的最大请求次数
RATE_LIMIT_PER_MINUTE = 10
# 限流滑动窗口长度（秒）：60 秒
RATE_WINDOW_SECONDS = 60
# 用户学号 → 该用户最近请求时间戳的双端队列（deque 用字符串标注，避免模块顶部循环依赖）
_rate_log: dict[str, "deque[float]"] = {}
# 全局互斥锁：保护 _rate_log 的并发读写（FastAPI 多线程并发处理请求）
_rate_lock = threading.Lock()


# 限流判断函数：返回该用户当前是否被允许发起本次请求
def rate_allowed(user_no: str) -> bool:
    """60 秒滑动窗口内最多 RATE_LIMIT_PER_MINUTE 次，无窗口边界绕过问题。"""
    # 局部导入 deque（双端队列，两端 O(1) 增删）：放在函数内避免模块顶部循环依赖
    from collections import deque

    # 当前时间戳（秒）
    now = time.time()
    # 窗口起点：当前时间往前推 60 秒，早于该时刻的请求时间戳视为过期
    cutoff = now - RATE_WINDOW_SECONDS
    # 加锁：整个"检查 + 更新"过程必须原子完成，防止并发请求同时通过限流
    with _rate_lock:
        # 惰性清理：仅当活跃用户数超阈值时，移除窗口内已无请求的过期条目，防止字典无界增长
        # 只有用户表超过 1000 条时才做清理（阈值内不清理，避免每次请求都全表扫描）
        if len(_rate_log) > 1000:
            # 用列表推导式收集待删键，再逐个删除（避免在迭代过程中直接修改字典）
            for k in [k for k, q in _rate_log.items() if not q or q[-1] <= cutoff]:
                del _rate_log[k]
        # 用双端队列记录该用户最近的时间戳，队头最旧、队尾最新。
        # 取出该用户的时间戳队列
        q = _rate_log.get(user_no)
        # 该用户首次访问：创建空队列并登记到映射表
        if q is None:
            q = deque()
            _rate_log[user_no] = q
        # 弹出窗口外的旧时间戳（队头 <= cutoff 即为过期），保持队列只含窗口内请求。
        # 队列按时间升序（队头最旧）：队头还在窗口起点之前说明已过期，逐一出队
        while q and q[0] <= cutoff:
            q.popleft()
        # 窗口内请求数已满则拒绝；否则记录本次请求时间。
        # 滑动窗口（而非固定分钟窗口）能避免"整点边界前一秒狂刷、下一窗口重置"的绕过问题。
        # 窗口内请求数已达到上限：拒绝本次请求
        if len(q) >= RATE_LIMIT_PER_MINUTE:
            return False
        # 记录本次请求时间戳到队尾（供下一次判断滑动窗口）
        q.append(now)
        # 未超限：放行
        return True


# 注册 POST /api/chat 路由：学生问答主接口；response_model 声明响应结构，用于校验与文档生成
@app.post("/api/chat", response_model=ChatResponse)
def chat(
    req: ChatRequest,  # 请求体：问题、历史、目标学生学号等
    authorization: str = Header(default=""),  # Authorization 请求头（Bearer JWT）
):
    """学生问答主接口：JWT 验签鉴权 → 按用户限流 → 执行 LangGraph 工作流 → 返回回答。

    入参 req：聊天请求体（问题、历史、目标学生学号等）。
    入参 authorization：Bearer JWT（由 Spring 后端签发并透传）。
    返回：ChatResponse，包含最终回答文本。
    异常：401 认证失败 / 429 触发限流 / 413 请求体过大 / 503 服务内部错误。
    """
    # 认证三步：必须有 Bearer 前缀 → JWT 验签/过期校验 → 从 claims 取身份与角色。
    # 身份完全来自 Spring 签发的令牌 claims，接口参数与请求头里的学号均不可信，
    # 后续 DB 查询用的学号严格取自已验签的令牌（见 db 层强制绑定）。
    # 校验 Authorization 头必须以 "Bearer " 开头（标准 Bearer 认证格式）
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    # 去掉 "Bearer " 前缀并去除首尾空白，得到纯 token，再用共享密钥验签；验签失败返回 None
    claims = verify_jwt(authorization[len("Bearer "):].strip(), settings.jwt_secret)
    # 验签不通过（无效或已过期令牌）
    if claims is None:
        raise HTTPException(status_code=401, detail="认证令牌无效或已过期")

    # 从令牌声明中取用户学号，统一转成字符串（claims 里的值可能不是字符串类型）
    user_no = str(claims.get("userNo") or "")
    # 令牌里没有学号：视为无效令牌，拒绝服务
    if not user_no:
        raise HTTPException(status_code=401, detail="认证令牌缺少用户标识")
    # 角色只读可信来源（令牌），非法值一律回退为学生，避免被提权成管理员。
    # 取角色并转大写（STUDENT/ADMIN），缺省按 STUDENT 处理
    role = str(claims.get("roleType") or "STUDENT").upper()
    # 只承认两种合法角色值，其余一律回退为学生（防伪造角色提权）
    if role not in ("STUDENT", "ADMIN"):
        role = "STUDENT"

    # 按用户限流：放在认证之后，只对已认证用户计数（未认证的恶意请求已被 401 拦掉）。
    # 该用户已触发限流：返回 429
    if not rate_allowed(user_no):
        raise HTTPException(status_code=429, detail="请求过于频繁，请稍后再试")

    # 组装 LangGraph 初始状态：把 HTTP 请求映射成工作流需要的字段。
    # 历史只保留最近 6 条，控制传给 LLM 的上下文长度（防超长、省 token）。
    # 构造工作流初始状态字典：包含身份、问题、历史、意图等字段，供各节点读写与填充
    state = {
        "user_no": user_no,  # 已验签的学号（可信身份）
        "role": role,  # 已规整的角色（STUDENT/ADMIN）
        "target_no": req.target_student_no or "",  # 目标学生学号（管理员代查他人时使用）
        "query": req.message,  # 用户问题文本
        "history": [{"role": m.role, "content": m.content} for m in req.history[-6:]],  # 最近 6 条对话历史
        "intent": "",  # 意图识别结果（由 classify 节点填充）
        "target_course": "",  # 目标课程（课程分析场景使用）
        "student_profile": {},  # 学生画像（fetch 节点填充）
        "tool_results": {},  # SQL 查询结果（fetch 节点填充）
        "retrieved": [],  # 检索到的课程资料（retrieve 节点填充）
        "web_results": [],  # 联网搜索结果（web_search 节点填充）
        "answer": "",  # 最终回答（generate 节点填充）
        "error": None,  # 错误信息（异常兜底使用）
    }
    try:
        # 把初始状态喂给编译好的 LangGraph 工作流，返回最终状态字典
        result = workflow.invoke(state)
    except Exception:
        # 工作流内部未捕获异常统一在此兜底：记录堆栈后返回 503，不向客户端暴露内部细节。
        # 打印完整异常堆栈到日志，便于事后排查问题根因
        logger.exception("AI 工作流调用失败")
        raise HTTPException(status_code=503, detail="AI 服务内部错误，请稍后重试")

    # 从结果状态中取出最终回答文本
    answer = result.get("answer")
    # 生成节点可能因 LLM 调用失败未产出回答，统一按服务不可用处理。
    # 回答为空：视为生成失败
    if not answer:
        raise HTTPException(status_code=503, detail="AI 服务暂不可用，请稍后重试")
    # 把回答封装进响应模型返回给客户端
    return ChatResponse(answer=answer)


# 脚本直接运行时（python main.py）才执行；被作为模块导入时跳过（避免启动副作用）
if __name__ == "__main__":
    # 仅监听本机回环地址，端口由 .env 的 AI_PORT 控制（默认 8000）
    # 局部导入 uvicorn（ASGI 服务器）：只有直接运行时才需要，避免模块导入时加载无关依赖
    import uvicorn

    # 启动 uvicorn：绑定回环地址 + 配置端口；生产部署时通常由外部进程（systemd/Nginx 反代）托管
    uvicorn.run(app, host="127.0.0.1", port=settings.ai_port)
