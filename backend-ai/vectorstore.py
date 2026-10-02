"""ChromaDB 课程向量库 + BM25 关键词库：混合检索（向量 + BM25 → RRF 融合 → rerank）。"""
# chromadb：向量数据库客户端（PersistentClient 支持本地持久化存储）
import chromadb
# Chroma 自身的 Settings 配置类：这里用于关闭匿名遥测上报
from chromadb.config import Settings as ChromaSettings

# 项目内模块：db 提供课程数据查询，bm25 提供关键词索引，config 提供配置，llm 提供向量/重排能力
import db
from bm25 import BM25Index
from config import settings
from llm import get_embeddings, rerank

# Chroma 集合名：所有课程目录向量统一存放在该集合中
COLLECTION_NAME = "course_catalog"

# BM25 索引缓存：语料来自 MySQL，独立于向量库（embedding 不可用时关键词检索仍可用）
# 全局 BM25 索引对象缓存（首次检索时才构建，懒加载）
_bm25_index: BM25Index | None = None
# 是否已构建 BM25 索引的标记（用于幂等：已构建则跳过重复构建）
_bm25_built = False


# Chroma 客户端工厂函数
def _client() -> chromadb.ClientAPI:
    """创建/复用 ChromaDB 持久化客户端（数据目录由 config.chroma_dir 指定）。

    返回：chromadb 客户端实例。
    """
    # 创建持久化客户端：向量数据落盘到 chroma_dir 目录（进程重启后数据仍保留）
    return chromadb.PersistentClient(
        path=settings.chroma_dir,  # 向量库数据目录
        # 关闭匿名遥测：不向 Chroma 官方上报使用数据（隐私/合规考虑）
        settings=ChromaSettings(anonymized_telemetry=False),
    )


# 课程文档格式化函数
def _course_doc(c: dict) -> str:
    """课程文本（供向量化、BM25 与检索展示，两种检索共用同一格式）。"""
    # 课程各字段拼成一段自然语言：同时喂给向量模型和 BM25 分词，
    # 保证两路检索针对同一份语料，召回结果才能用同一个 course_id 做 RRF 融合。
    # 用 f-string 把课程关键字段拼成一句结构化自然语言，作为两路检索共用的语料
    return (
        f"课程名称：{c['course_name']}；课程编号：{c['course_code']}；"
        f"学分：{c['credit']}；学时：{c['hours']}；"
        f"授课教师：{c['teacher_name']}；上课时间：{c['schedule']}；"
        f"地点：{c['location']}；已选人数：{c['current_enrolled']}/{c['capacity']}"
    )


# BM25 索引懒加载函数
def _ensure_bm25() -> BM25Index | None:
    """按需从数据库构建 BM25 索引（幂等，课程变化时由 sync_catalog 重置）。"""
    # 声明修改模块级全局变量（索引对象与构建标记）
    global _bm25_index, _bm25_built
    # 懒加载 + 幂等：第一次检索时才建索引（避免启动即全量分词拖慢启动）；
    # sync_catalog 同步课程后会把 _bm25_built 置 False 强制重建，保证与数据库一致。
    # 已构建过：直接返回缓存的索引（幂等，不重复构建）
    if _bm25_built:
        return _bm25_index
    # 首次使用：从数据库全量拉取已发布课程作为语料
    courses = db.get_all_published_courses()
    # 有课程数据才构建索引（空库不构建，索引保持为 None）
    if courses:
        _bm25_index = BM25Index(
            # 每门课程格式化为同一份检索语料文本
            docs=[_course_doc(c) for c in courses],
            # 文档 ID 统一为字符串形式的课程主键，与 Chroma 的 id 约定保持一致
            ids=[str(c["id"]) for c in courses],
            # 元数据：课程 id/名称/学分，供检索后回查与展示
            metadatas=[
                {"course_id": c["id"], "course_name": c["course_name"], "credit": c["credit"]}
                for c in courses
            ],
        )
    # 即使语料为空也要标记已构建：避免每次检索都重复查库（空库也只会构建一次）。
    # 无论是否成功构建都置标记：防止空库时每次检索都重复查询数据库
    _bm25_built = True
    # 返回索引（语料为空时为 None）
    return _bm25_index


# 课程目录全量同步函数（服务启动时调用）
def sync_catalog() -> int:
    """全量同步已发布课程到 ChromaDB（幂等 upsert）+ 重建 BM25 索引，返回同步数量。"""
    # 拉取全部已发布课程
    courses = db.get_all_published_courses()
    # 没有课程数据
    if not courses:
        # 数据库无已发布课程时视为"语料已清空"：标记 BM25 需重建，避免沿用旧索引。
        _bm25_built = False
        # 同步数量为 0
        return 0
    # 把所有课程格式化为检索语料文本列表
    docs = [_course_doc(c) for c in courses]
    # 构造与文档一一对应的元数据列表
    metadatas = [
        {"course_id": c["id"], "course_name": c["course_name"], "credit": c["credit"]}
        for c in courses
    ]
    # 调用向量模型批量生成所有文档的 embedding 向量
    embeddings = get_embeddings().embed_documents(docs)
    # get_or_create_collection：集合不存在则自动创建；hnsw:space=cosine 表示用余弦相似度做近邻检索，
    # 与 BGE-M3 向量归一化后的余弦度量匹配。
    # 取（或创建）课程集合：HNSW 索引空间设为余弦相似度，与 BGE-M3 的向量度量方式一致
    col = _client().get_or_create_collection(COLLECTION_NAME, metadata={"hnsw:space": "cosine"})
    # upsert 幂等：id 相同则覆盖，天然支持重复同步，不会产生重复文档。
    # 批量写入/覆盖：id 相同时覆盖旧文档（幂等），课程更新后重复同步不会产生重复向量
    col.upsert(
        ids=[str(c["id"]) for c in courses],  # 文档 ID（字符串形式的课程主键）
        documents=docs,  # 文档文本
        embeddings=embeddings,  # 预计算的向量
        metadatas=metadatas,  # 元数据
    )
    # 课程数据已变化，重建 BM25 索引
    # 置为未构建：下次检索时按最新语料重建 BM25
    _bm25_built = False
    # 立即触发一次重建，保证 BM25 索引与刚同步的向量库保持一致
    _ensure_bm25()
    # 返回本次同步的课程数量
    return len(courses)


# 向量召回函数
def _vector_candidates(query_text: str, top_k: int = 20) -> list[str]:
    """向量召回：返回 top_k 个 course_id（字符串）；embedding 不可用时返回空。"""
    try:
        # 取课程集合（不存在则自动创建，保持幂等）
        col = _client().get_or_create_collection(COLLECTION_NAME, metadata={"hnsw:space": "cosine"})
        # 用向量模型把查询文本编码成向量
        query_emb = get_embeddings().embed_query(query_text)
        # 在向量库中做近邻检索，返回与查询最相似的 top_k 条
        hits = col.query(query_embeddings=[query_emb], n_results=top_k)
        # 提取命中的文档 id 列表（无命中时返回空列表）
        return hits.get("ids", [[]])[0] or []
    except Exception:
        # 向量召回失败（如未配置 SiliconFlow Key、embedding 服务不可用）时返回空列表，
        # 让混合检索退化为纯 BM25，而不是整个问答链路报错。
        return []


# BM25 关键词召回函数
def _bm25_candidates(query_text: str, top_k: int = 20) -> list[str]:
    """关键词召回：BM25 返回 top_k 个 course_id（字符串）。"""
    # 确保 BM25 索引已构建（懒加载）
    idx = _ensure_bm25()
    # 索引为空（语料为空）时返回空列表
    if idx is None:
        return []
    # 调用 BM25 检索，返回 top_k 个文档 id
    return idx.search(query_text, top_k=top_k)


# RRF 融合函数
def _rrf_fuse(rankings: list[list[str]], k: int = 60, top_n: int = 20) -> list[str]:
    """Reciprocal Rank Fusion：融合多路排序结果（id 字符串），返回融合后 top_n。"""
    # RRF 不依赖各检索器打出的绝对分数（向量相似度与 BM25 分数量纲/分布完全不同，无法直接相加），
    # 只依赖"文档在各自排序中的名次"：rank 越靠前，贡献 1/(k+rank) 越大。
    # 这样无需归一化分数即可公平融合两路召回，且对某一路召回失败（空列表）天然健壮。
    # 累加器：文档 id → 融合得分（跨多路排序累加）
    scores: dict[str, float] = {}
    # 遍历每一路排序结果（向量路 + BM25 路）
    for ranking in rankings:
        # enumerate 同时取到名次（从 0 开始）与文档 id
        for rank, doc_id in enumerate(ranking):
            # k=60 是 RRF 论文常用常数，用于压低高名次文档的边际优势，防止某一路独大。
            # 累加该文档的 RRF 得分：名次越靠前（rank 越小），贡献 1/(k+rank+1) 越大
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (k + rank + 1)
    # 按融合得分降序排序（分数高说明在两路中都排名靠前）
    ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    # 取前 top_n 个文档 id（丢弃分数，只保留顺序）
    return [doc_id for doc_id, _ in ranked[:top_n]]


# 混合召回函数（召回阶段，尚未重排）
def hybrid_candidates(query_text: str, top_k: int = 20) -> list[dict]:
    """混合召回：向量 + BM25 → RRF 融合（未重排）。返回 [{"course_id", "course_name", "doc"}]。"""
    # 混合召回流水线第 1 步：两路召回（向量语义 + BM25 关键词）→ RRF 融合。
    # 向量捕获语义相近（如"想学机器学习"→相关课程），BM25 捕获精确词项
    # （如教师名"王老师"、地点"A101"这类向量容易漏掉的词），互补提高召回率。
    # 并行执行两路召回并把结果交给 RRF 融合，取前 top_k 个候选 id
    fused = _rrf_fuse(
        [_vector_candidates(query_text, top_k), _bm25_candidates(query_text, top_k)],
        top_n=top_k,
    )
    # 融合结果为空：直接返回空列表
    if not fused:
        return []

    # 第 2 步：按融合后的 course_id 回查文档与元数据（名称、学分），供后续 rerank 与展示。
    # 优先从向量库取文档/元数据；部分课程可能不在向量库（embedding 不可用），回退到 BM25 语料
    # 打开向量库集合（存在则复用）
    col = _client().get_or_create_collection(COLLECTION_NAME, metadata={"hnsw:space": "cosine"})
    # 按 id 批量取文档与元数据
    got = col.get(ids=fused, include=["documents", "metadatas"])
    # 建立 id → 文档文本 的映射（zip 把两个列表按位置配对成字典）
    id_to_doc = dict(zip(got.get("ids") or [], got.get("documents") or []))
    # 建立 id → 元数据 的映射
    id_to_meta = dict(zip(got.get("ids") or [], got.get("metadatas") or []))
    # 取 BM25 索引（作为向量库缺失数据的兜底来源）
    idx = _ensure_bm25()

    # 结果列表：按融合后的顺序组装候选
    results: list[dict] = []
    # 遍历融合后的每个课程 id
    for doc_id in fused:
        # 从映射中取文档文本（可能为 None：向量库中不存在该 id）
        doc = id_to_doc.get(doc_id)
        # 取元数据（同样可能为 None）
        meta = id_to_meta.get(doc_id)
        # 向量库中缺失的课程（可能因 embedding 服务临时故障只写入了 BM25）：
        # 从 BM25 索引的内存语料补齐文档与元数据，保证融合结果都能拿到正文。
        # 向量库缺文档但 BM25 索引存在时，尝试从 BM25 语料补齐
        if doc is None and idx is not None:
            try:
                # 在 BM25 索引中找到该 id 的下标位置
                j = idx.ids.index(doc_id)
                # 用 BM25 语料中的文档与元数据补齐
                doc, meta = idx.docs[j], idx.metadatas[j]
            except ValueError:
                # BM25 索引里也没有该 id：跳过这个候选
                continue
        # 两边都拿不到文档：跳过该候选
        if doc is None:
            continue
        # 组装候选：course_id 转回 int、course_name、文档文本
        results.append(
            {
                "course_id": int(meta.get("course_id") or doc_id),
                "course_name": meta.get("course_name") or doc_id,
                "doc": doc,
            }
        )
    # 返回融合后的候选列表
    return results


# 对外主入口：混合检索 + 重排精排
def search_courses(query_text: str, top_k: int = 20) -> list[dict]:
    """混合检索：向量 + BM25 → RRF 融合 → BGE-Reranker 重排，返回 top5。

    返回 [{"course_id": int, "course_name": str, "score": float, "doc": str}]。
    rerank 失败（如未配置 SiliconFlow Key）时降级为融合结果前 5 条。
    """
    # 混合检索完整流水线：召回（向量+BM25→RRF 融合，宽召回 top_k）→ 重排（rerank 精排 top5）。
    # RRF 只负责"把两路的候选并到一起"，排序精度有限；rerank 用交叉编码器对 query-文档逐对打分，
    # 才是最终决定 top5 顺序的精排层，二者职责分离。
    # 第一步：混合召回得到候选列表（宽召回，候选可能多于 5 条）
    candidates = hybrid_candidates(query_text, top_k=top_k)
    # 无候选：直接返回空列表
    if not candidates:
        return []
    # 抽取候选文档文本列表（作为 rerank 的输入）
    docs = [c["doc"] for c in candidates]
    try:
        # 调用重排服务：对候选按与 query 的相关性精排，取前 5 条
        ranked = rerank(query_text, docs, top_n=5)
    except Exception:
        # 降级：rerank 服务不可用时（未配 Key / 超时），退回融合结果前 5 条，
        # 保证问答链路在精排层故障时仍可用（牺牲精度换可用性）。
        # 构造降级结果：按候选原有顺序取前 5 条，分数统一填 0（保持数据结构一致）
        ranked = [{"index": i, "relevance_score": 0.0} for i in range(min(5, len(docs)))]

    # 最终结果列表
    results = []
    # 遍历重排结果（按相关性从高到低）
    for item in ranked:
        # index 是 rerank 返回结果在传入 docs 列表中的下标，越界即跳过（防御性检查）。
        # 下标越界防御：异常数据直接跳过，避免 IndexError 拖垮整个请求
        if item["index"] >= len(candidates):
            continue
        # 按下标取回原始候选
        c = candidates[item["index"]]
        # 组装最终结果：课程 id/名称/重排分数（保留 4 位小数）/文档文本
        results.append(
            {
                "course_id": c["course_id"],
                "course_name": c["course_name"],
                "score": round(item["relevance_score"], 4),
                "doc": c["doc"],
            }
        )
    # 返回按相关性降序排列的最终结果
    return results
