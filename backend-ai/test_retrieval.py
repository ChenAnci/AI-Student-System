"""混合检索评估：召回率 Recall@K / 准确率 Precision@K / MRR。

对比四种检索策略：
    1) 纯向量（ChromaDB + BGE-M3）
    2) 纯关键词（BM25 + jieba）
    3) 混合（向量 + BM25 → RRF 融合）
    4) 混合 + rerank（BGE-Reranker 重排）

用法：
    python test_retrieval.py               # 默认 top_k=5，跳过 rerank（不消耗 API）
    python test_retrieval.py --topk 5
    python test_retrieval.py --rerank      # 启用 SiliconFlow rerank（需 SILICONFLOW_API_KEY）
"""
from __future__ import annotations

import argparse
from collections import defaultdict

import db
import vectorstore

TOPK_DEFAULT = 5


def load_courses() -> list[dict]:
    """加载全部已发布课程作为评估语料。

    返回：已发布课程列表（来自 MySQL，供构造查询与 ground truth 使用）。
    """
    return db.get_all_published_courses()


def build_queries(courses: list[dict]) -> list[dict]:
    """基于课程语料自动构造查询 + ground truth（按查询文本去重）。

    4 类查询：
        - 课程名 → 期望命中该课程
        - 课程名 + 学分 → 期望命中该课程
        - 学分 → 期望命中所有该学分的课程（多相关，考验 Recall）
        - 教师名 → 期望命中该教师的所有课程（多相关）
    """
    by_credit: dict = defaultdict(set)
    by_teacher: dict = defaultdict(set)
    for c in courses:
        by_credit[c["credit"]].add(c["id"])
        by_teacher[c["teacher_name"]].add(c["id"])

    seen: dict[str, dict] = {}
    for c in courses:
        candidates = [
            (c["course_name"], {c["id"]}),
            (f"{c['course_name']} {c['credit']}学分", {c["id"]}),
            (f"{c['credit']}学分", by_credit[c["credit"]]),
            (c["teacher_name"], by_teacher[c["teacher_name"]]),
        ]
        for q, relevant in candidates:
            if q not in seen:
                seen[q] = {"q": q, "relevant": relevant}
    return list(seen.values())


def evaluate(predict, queries: list[dict], top_k: int) -> dict:
    """predict(query) -> list[int]（course_id，按相关度降序）。"""
    hits = recall_sum = precision_sum = mrr_sum = 0.0
    n = 0
    for item in queries:
        relevant = set(item["relevant"])
        if not relevant:
            continue
        n += 1
        preds = [int(x) for x in predict(item["q"])][:top_k]
        hit_set = [c for c in preds if c in relevant]
        if hit_set:
            hits += 1
            first = preds.index(hit_set[0])
            mrr_sum += 1.0 / (first + 1)
        recall_sum += len(hit_set) / len(relevant)
        denom = min(top_k, len(preds))
        precision_sum += len(hit_set) / denom if denom else 0.0

    if n == 0:
        return {"queries": 0, "hit@k": 0, "recall@k": 0, "precision@k": 0, "mrr": 0}
    return {
        "queries": n,
        "hit@k": round(hits / n, 4),
        "recall@k": round(recall_sum / n, 4),
        "precision@k": round(precision_sum / n, 4),
        "mrr": round(mrr_sum / n, 4),
    }


def main() -> None:
    """评估脚本入口：加载语料 → 构造查询 → 对比各检索策略的指标并打印表格。"""
    parser = argparse.ArgumentParser(description="混合检索召回率/准确率评估")
    parser.add_argument("--topk", type=int, default=TOPK_DEFAULT, help="评估 K 值（默认 5）")
    parser.add_argument("--rerank", action="store_true", help="启用 SiliconFlow rerank 评估")
    args = parser.parse_args()

    courses = load_courses()
    if not courses:
        print("未获取到已发布课程，无法评估")
        return
    print(f"已发布课程数：{len(courses)}")

    queries = build_queries(courses)
    print(f"评估查询数：{len(queries)}（K={args.topk}）\n")

    strategies: list[tuple[str, object]] = [
        ("纯向量 (BGE-M3)", lambda q: vectorstore._vector_candidates(q, args.topk)),
        ("纯关键词 (BM25)", lambda q: vectorstore._bm25_candidates(q, args.topk)),
        ("混合 (RRF)", lambda q: [c["course_id"] for c in vectorstore.hybrid_candidates(q, args.topk)]),
    ]
    if args.rerank:
        strategies.append(
            ("混合 + rerank", lambda q: [c["course_id"] for c in vectorstore.search_courses(q, args.topk)])
        )

    print(f"{'检索策略':<22}{'Hit@K':>9}{'Recall@K':>11}{'Precision@K':>14}{'MRR':>9}")
    print("-" * 66)
    for name, predict in strategies:
        r = evaluate(predict, queries, args.topk)
        print(
            f"{name:<22}{r['hit@k']:>9.4f}{r['recall@k']:>11.4f}"
            f"{r['precision@k']:>14.4f}{r['mrr']:>9.4f}"
        )


if __name__ == "__main__":
    main()
