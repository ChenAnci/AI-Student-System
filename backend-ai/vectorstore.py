"""ChromaDB 课程向量库：同步课程目录 + 按学生画像检索。"""
import chromadb
from chromadb.config import Settings as ChromaSettings

import db
from config import settings
from llm import get_embeddings, rerank

COLLECTION_NAME = "course_catalog"


def _client() -> chromadb.ClientAPI:
    return chromadb.PersistentClient(
        path=settings.chroma_dir,
        settings=ChromaSettings(anonymized_telemetry=False),
    )


def _course_doc(c: dict) -> str:
    """课程文本（供向量化与检索展示）。"""
    return (
        f"课程名称：{c['course_name']}；课程编号：{c['course_code']}；"
        f"学分：{c['credit']}；学时：{c['hours']}；"
        f"授课教师：{c['teacher_name']}；上课时间：{c['schedule']}；"
        f"地点：{c['location']}；已选人数：{c['current_enrolled']}/{c['capacity']}"
    )


def sync_catalog() -> int:
    """全量同步已发布课程到 ChromaDB（幂等 upsert），返回同步数量。"""
    courses = db.get_all_published_courses()
    if not courses:
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
    return len(courses)


def search_courses(query_text: str, top_k: int = 20) -> list[dict]:
    """按学生画像文本召回 top_k 课程，再经 bge-reranker 重排，返回 top5。

    返回 [{"course_id": int, "course_name": str, "score": float, "doc": str}]。
    """
    col = _client().get_or_create_collection(COLLECTION_NAME, metadata={"hnsw:space": "cosine"})
    query_emb = get_embeddings().embed_query(query_text)
    hits = col.query(query_embeddings=[query_emb], n_results=top_k)
    ids = hits.get("ids", [[]])[0]
    docs = hits.get("documents", [[]])[0]
    metas = hits.get("metadatas", [[]])[0]
    if not ids:
        return []
    ranked = rerank(query_text, docs, top_n=5)
    results = []
    for item in ranked:
        idx = item["index"]
        results.append(
            {
                "course_id": int(metas[idx].get("course_id")),
                "course_name": metas[idx].get("course_name"),
                "score": round(item["relevance_score"], 4),
                "doc": docs[idx],
            }
        )
    return results
