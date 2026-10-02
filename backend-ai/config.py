"""全局配置：从 .env 读取，pydantic-settings 管理。"""
# 导入 pydantic-settings：BaseSettings 提供"类字段 ↔ 环境变量"自动映射能力，SettingsConfigDict 提供模型配置项
from pydantic_settings import BaseSettings, SettingsConfigDict


# 定义配置类：所有配置项以类字段形式声明，实例化时自动从环境变量 / .env 文件装载
class Settings(BaseSettings):
    """服务全局配置：字段与 .env 环境变量一一对应，由 pydantic-settings 自动装载。"""
    # pydantic-settings：字段名大写化后与 .env 中的环境变量一一对应（如 mysql_password ↔ MYSQL_PASSWORD）。
    # env_file 指定读取 backend-ai/.env；extra="ignore" 忽略 .env 里未声明字段，避免多余变量报错。
    # 模型配置：指定读取 .env（UTF-8 编码），未声明的额外字段静默忽略，避免多余变量导致报错
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # ---- DeepSeek 官方（对话模型，OpenAI 兼容接口）----
    # DeepSeek 官方 API Key（默认空串，启动时校验必须显式配置）
    deepseek_api_key: str = ""
    # DeepSeek 官方 API 基地址（走 OpenAI 兼容协议）
    deepseek_base_url: str = "https://api.deepseek.com/v1"

    # ---- 硅基流动 SiliconFlow（向量检索：embedding / rerank）----
    # DeepSeek 官方不提供 embedding/rerank，选课建议的向量检索继续走 SiliconFlow
    # SiliconFlow API Key（默认空串；未配置时向量检索链路自动降级为纯 BM25 关键词检索）
    siliconflow_api_key: str = ""
    # SiliconFlow API 基地址
    siliconflow_base_url: str = "https://api.siliconflow.cn/v1"

    # ---- Tavily（联网搜索，仅自由问答使用；可选依赖，未配置时联网跳过）----
    # Tavily 搜索 API Key（可选配置；为空时自由问答的联网检索步骤自动跳过）
    tavily_api_key: str = ""

    # ---- 模型 ----
    # 对话模型名（DeepSeek 官方）
    llm_model: str = "deepseek-v4-flash"
    # 向量模型名（BGE-M3，SiliconFlow 提供）
    embedding_model: str = "BAAI/bge-m3"
    # 重排序模型名（BGE-Reranker-v2-M3，SiliconFlow 提供）
    rerank_model: str = "BAAI/bge-reranker-v2-m3"

    # ---- MySQL（只读）----
    # MySQL 主机地址
    mysql_host: str = "localhost"
    # MySQL 端口
    mysql_port: int = 3306
    # 专用低权账号（见 sql/create_app_user.sql），禁止 root 直连；用户名可用 MYSQL_USER 覆盖
    # 数据库用户名：专用低权账号，禁止使用 root 直连
    mysql_user: str = "sms_app"
    # 密码必须从 .env 注入（默认留空，未配置则拒绝启动，避免弱默认凭据）
    # 数据库密码：必须从 .env 显式注入（默认留空，启动时强校验）
    mysql_password: str = ""
    # 数据库名
    mysql_db: str = "student_management"

    # ---- JWT 验证（与 Spring 后端共享密钥，用于校验转发来的 Bearer 令牌）----
    # JWT 共享密钥：与 Spring 后端保持一致（必须配置，缺失时一切请求都会被拒绝）
    jwt_secret: str = ""

    # ---- 服务 ----
    # ChromaDB 向量库持久化数据目录
    chroma_dir: str = "./chroma"
    # LLM 单次生成的最大 token 数（防止超长输出、控制成本）
    llm_max_tokens: int = 1500
    # LLM 采样温度（值越低回答越保守稳定）
    llm_temperature: float = 0.4
    # AI 服务监听端口（可通过 .env 的 AI_PORT 覆盖，避免与其它进程冲突）
    ai_port: int = 8000
    # 是否启用 /docs 等交互文档（生产默认关闭；仅调试时在 .env 设 ENABLE_DOCS=true 开启）
    enable_docs: bool = False


# 实例化全局唯一配置对象：其余模块通过 from config import settings 引用同一份配置
settings = Settings()

# fail-fast：数据库密码、DeepSeek 官方 Key 与 JWT 共享密钥必须显式配置（.env / 环境变量），禁止空/弱默认值静默启动
# 启动即校验关键凭据：与其带着空密码/空 Key 跑起来、运行时才报一堆晦涩错误，
# 不如进程一启动就明确失败，逼迫运维显式配置凭据（避免弱默认凭据上线）。
# 数据库密码为空：直接抛异常终止启动（fail-fast）
if not settings.mysql_password:
    raise RuntimeError("MYSQL_PASSWORD 未配置：请在 backend-ai/.env 中设置数据库密码（禁止弱默认凭据启动）")
# DeepSeek 官方 Key 为空：直接抛异常终止启动
if not settings.deepseek_api_key:
    raise RuntimeError("DEEPSEEK_API_KEY 未配置：请在 backend-ai/.env 中设置 DeepSeek 官方 API Key")
# JWT 共享密钥为空：直接抛异常终止启动
if not settings.jwt_secret:
    # S-7：JWT 密钥与 Spring 后端共享（环境变量 JWT_SECRET），必须显式配置。
    # 缺失时 AI 服务将拒绝一切请求（verify_jwt 对空密钥恒返回 None），
    # 但提前在此失败可让运维立即发现配置遗漏，而不是上线后所有调用 401。
    raise RuntimeError("JWT_SECRET 未配置：请在 backend-ai/.env 中设置与 Spring 后端一致的 JWT_SECRET")
