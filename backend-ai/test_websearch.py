"""web_search 节点单元测试（独立脚本，mock Tavily 客户端，不引入 pytest 依赖）。

用法：cd backend-ai && python test_websearch.py
运行方式：python test_websearch.py
"""
# 模块 docstring：web_search 节点的独立单元测试，用 mock 客户端验证，不依赖 pytest。

# 导入被测模块（别名 ws）：便于调用 tavily_search / web_search_node 并访问其 settings。
import agents.websearch as ws


class FakeResponse:
    """mock httpx 响应：固定返回预设的 JSON payload，可模拟指定 HTTP 状态码。"""

    def __init__(self, payload, status_code=200):
        """构造 mock 响应。

        入参 payload：json() 返回的预设数据。
        入参 status_code：模拟的 HTTP 状态码（>=400 时 raise_for_status 抛错）。
        """
        self._payload = payload  # 保存预设的 JSON 数据
        self._status = status_code  # 保存模拟的 HTTP 状态码

    def raise_for_status(self):
        """状态码 >= 400 时抛异常，模拟 httpx 的非 2xx 报错行为。"""
        if self._status >= 400:
            # 状态码 >= 400：与 httpx 行为一致地抛出异常，供被测代码捕获。
            raise RuntimeError(f"HTTP {self._status}")

    def json(self):
        """返回预设的 JSON 数据。"""
        return self._payload  # 返回构造时预设的 JSON 数据


class FakeClient:
    """mock httpx：只实现 post(url, json=None, timeout=None)，记录调用参数。"""

    def __init__(self, response):
        """构造 mock 客户端。

        入参 response：每次 post 都返回的预设响应对象。
        """
        self.response = response  # 预设响应对象（每次 post 都返回它）
        self.calls = []  # 记录每次 post 的调用参数，供断言使用

    def post(self, url, json=None, timeout=None):
        """记录本次调用参数并返回预设响应（入参同 httpx.Client.post）。"""
        # 记录调用参数，便于断言请求是否满足约定（URL/JSON 体/超时）。
        self.calls.append({"url": url, "json": json, "timeout": timeout})
        return self.response  # 返回预设响应


def test_parses_results_and_truncates_to_3():
    """tavily_search：正确解析 results 并截断为最多 MAX_RESULTS=3 条，请求参数符合约定。"""
    # 构造 5 条模拟结果（超过上限 3，用于验证截断逻辑）。
    payload = {"results": [
        {"title": f"t{i}", "url": f"https://e{i}.com", "content": f"c{i}"} for i in range(5)
    ]}
    # 组装 mock 客户端。
    client = FakeClient(FakeResponse(payload))
    # 调用被测函数：查询词 + 假 key + 注入 mock 客户端。
    out = ws.tavily_search("java 就业前景", "key", client=client)
    # 断言：结果被截断为最多 3 条。
    assert len(out) == 3, f"应截断为 3 条，实际 {len(out)}"
    # 断言：字段映射正确（content → snippet）且内容与输入一致。
    assert out[0] == {"title": "t0", "url": "https://e0.com", "snippet": "c0"}
    # 取出第一次请求记录。
    req = client.calls[0]
    # 断言请求携带了正确的查询词。
    assert req["json"]["query"] == "java 就业前景"
    # 断言 max_results 为 3。
    assert req["json"]["max_results"] == 3
    # 断言搜索深度为 basic。
    assert req["json"]["search_depth"] == "basic"
    # 断言超时时间为 8 秒。
    assert req["timeout"] == 8


def test_node_failure_degrades_to_empty():
    """web_search_node：Tavily 返回 500 时降级为空列表，不阻断工作流。"""
    # 构造最小状态：FREE_QA 意图，模拟正常触发条件。
    state = {"error": None, "intent": "FREE_QA", "query": "x", "web_results": None}
    # mock 返回 500 状态码的客户端。
    client = FakeClient(FakeResponse({}, status_code=500))
    # 执行节点（注入 mock 客户端）。
    out = ws.web_search_node(state, client=client)
    # 断言：失败后降级为空列表。
    assert out["web_results"] == []
    # 断言：确实发起过一次请求，随后异常被节点内部吞掉。
    assert len(client.calls) == 1, "应调用一次 Tavily，随后异常被节点吞掉"


def test_node_skips_for_non_free_qa():
    """web_search_node：非 FREE_QA 意图（如 QUERY_STUDY）不触发联网搜索。"""
    # 非 FREE_QA 意图的状态（模拟查成绩场景）。
    state = {"error": None, "intent": "QUERY_STUDY", "query": "查成绩", "web_results": None}
    client = FakeClient(FakeResponse({"results": []}))
    out = ws.web_search_node(state, client=client)
    # 断言：web_results 被置为空列表。
    assert out["web_results"] == []
    # 断言：从未发起过 Tavily 请求。
    assert not client.calls, "非 FREE_QA 不应调用 Tavily"


def test_node_skips_when_key_missing():
    """web_search_node：TAVILY_API_KEY 未配置时跳过联网搜索，不发起请求。"""
    state = {"error": None, "intent": "FREE_QA", "query": "x", "web_results": None}
    client = FakeClient(FakeResponse({"results": []}))
    # 保存原配置值（测试结束后恢复）。
    old = ws.settings.tavily_api_key
    try:
        # 临时清空密钥，模拟未配置场景。
        ws.settings.tavily_api_key = ""
        out = ws.web_search_node(state, client=client)
    finally:
        # 无论成败都恢复原密钥，避免污染其他测试。
        ws.settings.tavily_api_key = old
    # 断言：未配置时降级为空列表。
    assert out["web_results"] == []
    # 断言：未配置 Key 时从未发起请求。
    assert not client.calls, "未配置 Key 不应调用 Tavily"


if __name__ == "__main__":
    # 收集模块内所有以 test_ 开头的函数（排序保证执行顺序稳定）。
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for t in tests:
        t()  # 逐个执行测试函数
        print(f"PASS {t.__name__}")  # 打印通过的测试名
    # 汇总打印通过数量。
    print(f"全部 {len(tests)} 个测试通过")
