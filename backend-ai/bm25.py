"""BM25 关键词检索：jieba 中文分词 + rank_bm25。

与向量检索互补：向量捕获语义相近，BM25 捕获精确词项匹配（课程名/教师名/时间/地点等关键词）。
依赖优先使用系统 site-packages，缺失时回退到项目内 _deps（`pip install --target ./_deps jieba rank_bm25`）。
"""
# 未来版本标注特性：开启后允许在运行时较旧的 Python 上使用 list[str] 等新式类型注解（延迟求值为字符串）
from __future__ import annotations

# pathlib.Path：跨平台路径处理（用于定位项目内 _deps 依赖目录的绝对路径）
from pathlib import Path

# 优先尝试从系统环境（site-packages）导入第三方依赖：jieba（中文分词）、rank_bm25（BM25 算法实现）
try:
    import jieba
    from rank_bm25 import BM25Okapi
except ImportError:  # 本机未全局安装时，从项目内 _deps 加载
    # 系统环境缺少依赖：改为从项目内 _deps 目录加载（动态扩展模块搜索路径）
    import sys

    # 把当前文件所在目录下的 _deps 子目录插到 sys.path 最前面（优先级最高，import 优先命中）
    sys.path.insert(0, str(Path(__file__).resolve().parent / "_deps"))
    # 重新导入 jieba（这次会命中 _deps 下的安装包）
    import jieba
    # 重新导入 rank_bm25（同样从 _deps 加载）
    from rank_bm25 import BM25Okapi

# 模块加载时预热 jieba 词典：提前把分词词典载入内存，避免首次检索时卡顿
jieba.initialize()  # 预热词典，避免首次检索卡顿


# BM25 索引类：对课程文档语料做 jieba 分词并建立可检索的索引
class BM25Index:
    """课程文档语料的 BM25 索引。ids 使用与 Chroma 一致的字符串 course_id。"""

    # 构造函数：接收文档/ID/元数据三份按位置一一对应的列表
    def __init__(self, docs: list[str], ids: list[str], metadatas: list[dict]):
        """构建 BM25 索引：对文档语料做 jieba 分词并预计算词频统计。

        入参 docs：课程文档文本列表（与向量库共用同一份语料格式）。
        入参 ids：与 docs 一一对应的文档 ID（字符串 course_id）。
        入参 metadatas：与 docs 一一对应的元数据列表。
        异常：docs 为空时抛出 ValueError。
        """
        # 语料为空是非法状态：直接抛错，避免索引在空语料上构建后检索行为异常
        if not docs:
            raise ValueError("BM25 语料为空")
        # 保存原始文档文本（供检索命中后回查正文）
        self.docs = docs
        # 保存文档 ID 列表（与 docs 按下标一一对应）
        self.ids = ids
        # 保存元数据列表（同样按下标对应）
        self.metadatas = metadatas
        # jieba 是中文分词器：BM25 基于词项统计，中文必须先分词才能计算词频。
        # 构建时一次性切好全部文档并缓存（tokenized），避免每次检索重复分词。
        # 对每篇文档做 jieba 分词并缓存结果：构建时分词一次，后续检索直接复用
        self.tokenized = [list(jieba.cut(d)) for d in docs]
        # 用全部分词结果构建 BM25Okapi 索引（内部预计算文档词频、逆文档频率等统计量）
        self.bm25 = BM25Okapi(self.tokenized)

    # 检索方法：按 BM25 分数降序返回命中的文档 id
    def search(self, query: str, top_k: int = 20) -> list[str]:
        """返回按 BM25 分数降序的 doc id 列表（仅保留至少命中一个词项的文档）。"""
        # 查询同样要分词，且需与文档保持同一分词器/词典，否则词项对不上。
        # 对查询文本做同样的 jieba 分词，保证与文档的词项口径一致（同一词典）
        q = list(jieba.cut(query))
        # 查询分词结果为空（如纯空白字符串）：直接返回空列表
        if not q:
            return []
        # 计算查询与每篇文档的 BM25 相关性分数（返回列表，按下标与 docs 对应）
        scores = self.bm25.get_scores(q)
        # 只保留 score > 0 的文档：分数为 0 说明查询词项一个都没命中，
        # 这种文档对用户没有关键词层面价值，排除掉可减少后续 rerank 的无效输入。
        # 筛选出分数大于 0 的文档下标，再按分数降序排序（生成器筛选 + sorted 一次完成）
        ranked = sorted(
            (i for i, s in enumerate(scores) if s > 0),
            key=lambda i: scores[i],  # 以该下标的 BM25 分数作为排序键
            reverse=True,  # 降序：分数最高的排最前
        )
        # 按下标映射回文档 id，只取前 top_k 个返回
        return [self.ids[i] for i in ranked[:top_k]]
