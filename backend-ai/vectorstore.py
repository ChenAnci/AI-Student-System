"""ChromaDB 课程向量库 + BM25 关键词库：混合检索（向量 + BM25 → RRF 融合 → rerank）。"""
import chromadb
from chromadb.config import Settings as ChromaSettings

import db
from bm25 import BM25Index
from config import settings
from llm import get_embeddings, rerank

COLLECTION_NAME = "course_catalog"

# BM25 索引缓存：语料来自 MySQL，独立于向量库（embedding 不可用时关键词检索仍可用）
_bm25_index: BM25Index | None = None
_bm25_built = False


def _client() -> chromadb.ClientAPI:
    return chromadb.PersistentClient(
        path=settings.chroma_dir,
        settings=ChromaSettings(anonymized_telemetry=False),
    )


def _course_doc(c: dict) -> str:
    """课程文本（供向量化、BM25 与检索展示，两种检索共用同一格式）。"""
    return (
        f"课程名称：{c['course_name']}；课程编号：{c['course_code']}；"
        f"学分：{c['credit']}；学时：{c['hours']}；"
        f"授课教师：{c['teacher_name']}；上课时间：{c['schedule']}；"
        f"地点：{c['location']}；已选人数：{c['current_enrolled']}/{c['capacity']}"
    )


def _ensure_bm25() -> BM25Index | None:
    """按需从数据库构建 BM25 索引（幂等，课程变化时由 sync_catalog 重置）。"""
    global _bm25_index, _bm25_built
    if _bm25_built:
        return _bm25_index
    courses = db.get_all_published_courses()
    if courses:
        _bm25_index = BM25Index(
            docs=[_course_doc(c) for c in courses],
            ids=[str(c["id"]) for c in courses],
            metadatas=[
                {"course_id": c["id"], "course_name": c["course_name"], "credit": c["credit"]}
                for c in courses
            ],
        )
    _bm25_built = True
    return _bm25_index


def sync_catalog() -> int:
    """全量同步已发布课程到 ChromaDB（幂等 upsert）+ 重建 BM25 索引，返回同步数量。"""
    courses = db.get_all_published_courses()
    if not courses:
        _bm25_built = False
        return 0
    docs = [_course_doc(c) for c in courses]
    metadatas = [
        {"course_id": c["id"], "course_name": c["course_name"], "credit": c["credit"]}
        for c in courses
    ]
    embeddings = get_embeddings().embed_documents(docs)
    col = _client().get_or_create_collection(COLLECTION_NAME, metadata={"hnsw:space": "cosine"})
    col.upsert(
        ids=[str(c["id"]) for c in courses],
        documents=docs,
        embeddings=embeddings,
        metadatas=metadatas,
    )
    # 课程数据已变化，重建 BM25 索引
    _bm25_built = False
    _ensure_bm25()
    return len(courses)


def _vector_candidates(query_text: str, top_k: int = 20) -> list[str]:
    """向量召回：返回 top_k 个 course_id（字符串）；embedding 不可用时返回空。"""
    try:
        col = _client().get_or_create_collection(COLLECTION_NAME, metadata={"hnsw:space": "cosine"})
        query_emb = get_embeddings().embed_query(query_text)
        hits = col.query(query_embeddings=[query_emb], n_results=top_k)
        return hits.get("ids", [[]])[0] or []
    except Exception:
        return []


def _bm25_candidates(query_text: str, top_k: int = 20) -> list[str]:
    """关键词召回：BM25 返回 top_k 个 course_id（字符串）。"""
    idx = _ensure_bm25()
    if idx is None:
        return []
    return idx.search(query_text, top_k=top_k)


def _rrf_fuse(rankings: list[list[str]], k: int = 60, top_n: int = 20) -> list[str]:
    """Reciprocal Rank Fusion：融合多路排序结果（id 字符串），返回融合后 top_n。"""
    scores: dict[str, float] = {}
    for ranking in rankings:
        for rank, doc_id in enumerate(ranking):
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (k + rank + 1)
    ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return [doc_id for doc_id, _ in ranked[:top_n]]


def hybrid_candidates(query_text: str, top_k: int = 20) -> list[dict]:
    """混合召回：向量 + BM25 → RRF 融合（未重排）。返回 [{"course_id", "course_name", "doc"}]。"""
    fused = _rrf_fuse(
        [_vector_candidates(query_text, top_k), _bm25_candidates(query_text, top_k)],
        top_n=top_k,
    )
    if not fused:
        return []

    # 优先从向量库取文档/元数据；部分课程可能不在向量库（embedding 不可用），回退到 BM25 语料
    col = _client().get_or_create_collection(COLLECTION_NAME, metadata={"hnsw:space": "cosine"})
    got = col.get(ids=fused, include=["documents", "metadatas"])
    id_to_doc = dict(zip(got.get("ids") or [], got.get("documents") or []))
    id_to_meta = dict(zip(got.get("ids") or [], got.get("metadatas") or []))
    idx = _ensure_bm25()

    results: list[dict] = []
    for doc_id in fused:
        doc = id_to_doc.get(doc_id)
        meta = id_to_meta.get(doc_id)
        if doc is None and idx is not None:
            try:
                j = idx.ids.index(doc_id)
                doc, meta = idx.docs[j], idx.metadatas[j]
            except ValueError:
                continue
        if doc is None:
            continue
        results.append(
            {
                "course_id": int(meta.get("course_id") or doc_id),
                "course_name": meta.get("course_name") or doc_id,
                "doc": doc,
            }
        )
    return results


def search_courses(query_text: str, top_k: int = 20) -> list[dict]:
    """混合检索：向量 + BM25 → RRF 融合 → BGE-Reranker 重排，返回 top5。

    返回 [{"course_id": int, "course_name": str, "score": float, "doc": str}]。
    rerank 失败（如未配置 SiliconFlow Key）时降级为融合结果前 5 条。
    """
    candidates = hybrid_candidates(query_text, top_k=top_k)
    if not candidates:
        return []
    docs = [c["doc"] for c in candidates]
    try:
        ranked = rerank(query_text, docs, top_n=5)
    except Exception:
        ranked = [{"index": i, "relevance_score": 0.0} for i in range(min(5, len(docs)))]

    results = []
    for item in ranked:
        if item["index"] >= len(candidates):
            continue
        c = candidates[item["index"]]
        results.append(
            {
                "course_id": c["course_id"],
                "course_name": c["course_name"],
                "score": round(item["relevance_score"], 4),
                "doc": c["doc"],
            }
        )
    return results
