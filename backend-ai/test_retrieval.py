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
# 模块 docstring：混合检索评估脚本，对比四种检索策略的 Recall@K / Precision@K / MRR 指标。

# 延迟注解求值：让 list[dict] 等内建泛型注解在旧版 Python 中也能直接使用。
from __future__ import annotations

# import 区域：命令行参数解析、默认字典（自动初始化缺失键）、数据库与向量检索模块。
import argparse
from collections import defaultdict

import db
import vectorstore

# 默认评估 K 值：取前 5 条预测结果计算指标。
TOPK_DEFAULT = 5


def load_courses() -> list[dict]:
    """加载全部已发布课程作为评估语料。

    返回：已发布课程列表（来自 MySQL，供构造查询与 ground truth 使用）。
    """
    # 从数据库读取全部已发布课程，作为评估语料。
    return db.get_all_published_courses()


def build_queries(courses: list[dict]) -> list[dict]:
    """基于课程语料自动构造查询 + ground truth（按查询文本去重）。

    4 类查询：
        - 课程名 → 期望命中该课程
        - 课程名 + 学分 → 期望命中该课程
        - 学分 → 期望命中所有该学分的课程（多相关，考验 Recall）
        - 教师名 → 期望命中该教师的所有课程（多相关）
    """
    # 学分 → 课程 id 集合（"学分"类查询的期望相关集，多相关）。
    by_credit: dict = defaultdict(set)
    # 教师名 → 课程 id 集合（"教师名"类查询的期望相关集，多相关）。
    by_teacher: dict = defaultdict(set)
    for c in courses:
        # 遍历全部课程，按学分、教师名建立倒排索引。
        by_credit[c["credit"]].add(c["id"])
        by_teacher[c["teacher_name"]].add(c["id"])

    # 按查询文本去重的容器：同一查询文本只保留一次。
    seen: dict[str, dict] = {}
    for c in courses:
        # 为每门课程生成 4 类查询及其期望命中的课程 id 集合。
        candidates = [
            (c["course_name"], {c["id"]}),  # 查询1：课程名 → 期望命中该课程
            (f"{c['course_name']} {c['credit']}学分", {c["id"]}),  # 查询2：课程名+学分 → 期望命中该课程
            (f"{c['credit']}学分", by_credit[c["credit"]]),  # 查询3：学分 → 期望命中该学分所有课程（多相关）
            (c["teacher_name"], by_teacher[c["teacher_name"]]),  # 查询4：教师名 → 期望命中该教师所有课程（多相关）
        ]
        for q, relevant in candidates:
            if q not in seen:
                # 同一查询文本只保留第一次（不同课程可能生成相同查询，避免重复评估）。
                seen[q] = {"q": q, "relevant": relevant}
    # 返回去重后的查询列表（含查询文本与期望相关课程集合）。
    return list(seen.values())


def evaluate(predict, queries: list[dict], top_k: int) -> dict:
    """predict(query) -> list[int]（course_id，按相关度降序）。"""
    # 累加器：命中查询数 / Recall 总和 / Precision 总和 / MRR 总和。
    hits = recall_sum = precision_sum = mrr_sum = 0.0
    # 有效查询计数（排除期望相关集为空的查询）。
    n = 0
    for item in queries:
        # 期望相关的课程 id 集合（ground truth）。
        relevant = set(item["relevant"])
        if not relevant:
            continue  # 期望集合为空：跳过，无法计算相关指标
        n += 1
        # 调用检索策略得到预测课程 id 列表，截断到前 top_k 条。
        preds = [int(x) for x in predict(item["q"])][:top_k]
        # 命中的课程 id 列表（预测结果 ∩ 期望相关集）。
        hit_set = [c for c in preds if c in relevant]
        if hit_set:
            # 至少命中一条：计入命中数并累加 MRR。
            hits += 1
            # 第一条命中所处的排名（0 起）。
            first = preds.index(hit_set[0])
            # MRR 单条贡献：1 / (排名+1)，排名越靠前值越大。
            mrr_sum += 1.0 / (first + 1)
        # Recall@K 单条贡献：命中数 / 期望相关总数。
        recall_sum += len(hit_set) / len(relevant)
        # Precision 的分母：实际返回的条数（不超过 top_k）。
        denom = min(top_k, len(preds))
        # Precision@K 单条贡献：命中数 / 返回条数；分母为 0 时记 0 避免除零。
        precision_sum += len(hit_set) / denom if denom else 0.0

    if n == 0:
        # 没有有效查询：返回全 0 指标，避免除零错误。
        return {"queries": 0, "hit@k": 0, "recall@k": 0, "precision@k": 0, "mrr": 0}
    return {
        "queries": n,  # 有效查询数
        "hit@k": round(hits / n, 4),  # 至少命中一条的查询占比
        "recall@k": round(recall_sum / n, 4),  # 平均召回率
        "precision@k": round(precision_sum / n, 4),  # 平均准确率
        "mrr": round(mrr_sum / n, 4),  # 平均倒数排名（MRR）
    }


def main() -> None:
    """评估脚本入口：加载语料 → 构造查询 → 对比各检索策略的指标并打印表格。"""
    # 命令行解析器：提供 --topk（K 值）与 --rerank（是否启用重排）两个可选参数。
    parser = argparse.ArgumentParser(description="混合检索召回率/准确率评估")
    parser.add_argument("--topk", type=int, default=TOPK_DEFAULT, help="评估 K 值（默认 5）")
    parser.add_argument("--rerank", action="store_true", help="启用 SiliconFlow rerank 评估")
    args = parser.parse_args()  # 解析命令行参数

    # 加载课程语料。
    courses = load_courses()
    if not courses:
        # 数据库为空：直接提示并退出，避免构造出空评估集。
        print("未获取到已发布课程，无法评估")
        return
    print(f"已发布课程数：{len(courses)}")

    # 自动构造查询与期望相关集合。
    queries = build_queries(courses)
    print(f"评估查询数：{len(queries)}（K={args.topk}）\n")

    # 待评估的策略列表：名称 + 预测函数（输入查询，输出 course_id 列表）。
    strategies: list[tuple[str, object]] = [
        ("纯向量 (BGE-M3)", lambda q: vectorstore._vector_candidates(q, args.topk)),
        # 策略1：纯向量检索（ChromaDB + BGE-M3 向量召回）。
        ("纯关键词 (BM25)", lambda q: vectorstore._bm25_candidates(q, args.topk)),
        # 策略2：纯关键词检索（BM25 + jieba 分词）。
        ("混合 (RRF)", lambda q: [c["course_id"] for c in vectorstore.hybrid_candidates(q, args.topk)]),
        # 策略3：向量 + BM25 经 RRF 融合（无重排）。
    ]
    if args.rerank:
        # 传了 --rerank 才追加策略4（需调用 SiliconFlow rerank API，消耗额度）。
        strategies.append(
            ("混合 + rerank", lambda q: [c["course_id"] for c in vectorstore.search_courses(q, args.topk)])
            # 策略4：混合检索后再用 BGE-Reranker 重排（最终线上方案）。
        )

    # 表格表头：左对齐策略名，右对齐各指标列。
    print(f"{'检索策略':<22}{'Hit@K':>9}{'Recall@K':>11}{'Precision@K':>14}{'MRR':>9}")
    # 分隔线。
    print("-" * 66)
    for name, predict in strategies:
        # 逐个策略跑评估。
        r = evaluate(predict, queries, args.topk)
        print(
            f"{name:<22}{r['hit@k']:>9.4f}{r['recall@k']:>11.4f}"
            f"{r['precision@k']:>14.4f}{r['mrr']:>9.4f}"
            # 按列宽格式化打印该策略的四个指标。
        )


if __name__ == "__main__":
    # 直接运行本脚本时执行主流程。
    main()
