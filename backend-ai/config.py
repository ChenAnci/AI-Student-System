"""全局配置：从 .env 读取，pydantic-settings 管理。"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # ---- DeepSeek 官方（对话模型，OpenAI 兼容接口）----
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com/v1"

    # ---- 硅基流动 SiliconFlow（向量检索：embedding / rerank）----
    # DeepSeek 官方不提供 embedding/rerank，选课建议的向量检索继续走 SiliconFlow
    siliconflow_api_key: str = ""
    siliconflow_base_url: str = "https://api.siliconflow.cn/v1"

    # ---- 模型 ----
    llm_model: str = "deepseek-v4-flash"
    embedding_model: str = "BAAI/bge-m3"
    rerank_model: str = "BAAI/bge-reranker-v2-m3"

    # ---- MySQL（只读）----
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = "root"
    # 密码必须从 .env 注入（默认留空，未配置则拒绝启动，避免弱默认凭据）
    mysql_password: str = ""
    mysql_db: str = "student_management"

    # ---- JWT 验证（与 Spring 后端共享密钥，用于校验转发来的 Bearer 令牌）----
    jwt_secret: str = ""

    # ---- 服务 ----
    chroma_dir: str = "./chroma"
    llm_max_tokens: int = 1500
    llm_temperature: float = 0.4
    # AI 服务监听端口（可通过 .env 的 AI_PORT 覆盖，避免与其它进程冲突）
    ai_port: int = 8000


settings = Settings()

# fail-fast：数据库密码与 DeepSeek 官方 Key 必须显式配置（.env），禁止空/弱默认值静默启动
if not settings.mysql_password:
    raise RuntimeError("MYSQL_PASSWORD 未配置：请在 backend-ai/.env 中设置数据库密码（禁止弱默认凭据启动）")
if not settings.deepseek_api_key:
    raise RuntimeError("DEEPSEEK_API_KEY 未配置：请在 backend-ai/.env 中设置 DeepSeek 官方 API Key")
