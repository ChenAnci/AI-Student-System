"""web_search 节点单元测试（独立脚本，mock Tavily 客户端，不引入 pytest 依赖）。

用法：cd backend-ai && python test_websearch.py
运行方式：python test_websearch.py
"""
import agents.websearch as ws


class FakeResponse:
    def __init__(self, payload, status_code=200):
        self._payload = payload
        self._status = status_code

    def raise_for_status(self):
        if self._status >= 400:
            raise RuntimeError(f"HTTP {self._status}")

    def json(self):
        return self._payload


class FakeClient:
    """mock httpx：只实现 post(url, json=None, timeout=None)，记录调用参数。"""

    def __init__(self, response):
        self.response = response
        self.calls = []

    def post(self, url, json=None, timeout=None):
        self.calls.append({"url": url, "json": json, "timeout": timeout})
        return self.response


def test_parses_results_and_truncates_to_3():
    payload = {"results": [
        {"title": f"t{i}", "url": f"https://e{i}.com", "content": f"c{i}"} for i in range(5)
    ]}
    client = FakeClient(FakeResponse(payload))
    out = ws.tavily_search("java 就业前景", "key", client=client)
    assert len(out) == 3, f"应截断为 3 条，实际 {len(out)}"
    assert out[0] == {"title": "t0", "url": "https://e0.com", "snippet": "c0"}
    req = client.calls[0]
    assert req["json"]["query"] == "java 就业前景"
    assert req["json"]["max_results"] == 3
    assert req["json"]["search_depth"] == "basic"
    assert req["timeout"] == 8


def test_node_failure_degrades_to_empty():
    state = {"error": None, "intent": "FREE_QA", "query": "x", "web_results": None}
    client = FakeClient(FakeResponse({}, status_code=500))
    out = ws.web_search_node(state, client=client)
    assert out["web_results"] == []
    assert len(client.calls) == 1, "应调用一次 Tavily，随后异常被节点吞掉"


def test_node_skips_for_non_free_qa():
    state = {"error": None, "intent": "QUERY_STUDY", "query": "查成绩", "web_results": None}
    client = FakeClient(FakeResponse({"results": []}))
    out = ws.web_search_node(state, client=client)
    assert out["web_results"] == []
    assert not client.calls, "非 FREE_QA 不应调用 Tavily"


def test_node_skips_when_key_missing():
    state = {"error": None, "intent": "FREE_QA", "query": "x", "web_results": None}
    client = FakeClient(FakeResponse({"results": []}))
    old = ws.settings.tavily_api_key
    try:
        ws.settings.tavily_api_key = ""
        out = ws.web_search_node(state, client=client)
    finally:
        ws.settings.tavily_api_key = old
    assert out["web_results"] == []
    assert not client.calls, "未配置 Key 不应调用 Tavily"


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for t in tests:
        t()
        print(f"PASS {t.__name__}")
    print(f"全部 {len(tests)} 个测试通过")
