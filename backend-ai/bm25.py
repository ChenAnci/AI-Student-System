"""BM25 关键词检索：jieba 中文分词 + rank_bm25。

与向量检索互补：向量捕获语义相近，BM25 捕获精确词项匹配（课程名/教师名/时间/地点等关键词）。
依赖优先使用系统 site-packages，缺失时回退到项目内 _deps（`pip install --target ./_deps jieba rank_bm25`）。
"""
from __future__ import annotations

from pathlib import Path

try:
    import jieba
    from rank_bm25 import BM25Okapi
except ImportError:  # 本机未全局安装时，从项目内 _deps 加载
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parent / "_deps"))
    import jieba
    from rank_bm25 import BM25Okapi

jieba.initialize()  # 预热词典，避免首次检索卡顿


class BM25Index:
    """课程文档语料的 BM25 索引。ids 使用与 Chroma 一致的字符串 course_id。"""

    def __init__(self, docs: list[str], ids: list[str], metadatas: list[dict]):
        if not docs:
            raise ValueError("BM25 语料为空")
        self.docs = docs
        self.ids = ids
        self.metadatas = metadatas
        # jieba 是中文分词器：BM25 基于词项统计，中文必须先分词才能计算词频。
        # 构建时一次性切好全部文档并缓存（tokenized），避免每次检索重复分词。
        self.tokenized = [list(jieba.cut(d)) for d in docs]
        self.bm25 = BM25Okapi(self.tokenized)

    def search(self, query: str, top_k: int = 20) -> list[str]:
        """返回按 BM25 分数降序的 doc id 列表（仅保留至少命中一个词项的文档）。"""
        # 查询同样要分词，且需与文档保持同一分词器/词典，否则词项对不上。
        q = list(jieba.cut(query))
        if not q:
            return []
        scores = self.bm25.get_scores(q)
        # 只保留 score > 0 的文档：分数为 0 说明查询词项一个都没命中，
        # 这种文档对用户没有关键词层面价值，排除掉可减少后续 rerank 的无效输入。
        ranked = sorted(
            (i for i, s in enumerate(scores) if s > 0),
            key=lambda i: scores[i],
            reverse=True,
        )
        return [self.ids[i] for i in ranked[:top_k]]
