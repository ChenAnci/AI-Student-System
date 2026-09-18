package com.example.sms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiBodySizeFilter 单元测试：Content-Length 预检与 chunked（无 Content-Length）实际读取截断
 */
class AiBodySizeFilterTest {

    private final AiBodySizeFilter filter = new AiBodySizeFilter();

    /** 模拟带 Content-Length 的普通请求 */
    private MockHttpServletRequest requestWithLength(String body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ai/chat");
        req.setContent(body.getBytes());
        return req;
    }

    /** 模拟 chunked 请求：getContentLength() 恒为 -1，body 走输入流 */
    private HttpServletRequest chunkedRequest(String body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ai/chat");
        req.setContent(body.getBytes());
        // 包装：强制 getContentLength() 返回 -1（chunked 无长度头），但保留真实 body 输入流
        return new HttpServletRequestWrapper(req) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }

            @Override
            public ServletInputStream getInputStream() throws IOException {
                ByteArrayInputStream in = new ByteArrayInputStream(body.getBytes());
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        return in.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return in.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                    }
                };
            }
        };
    }

    @Test
    @DisplayName("Content-Length 超限：直接 413，不进入后续链")
    /** 验证场景：带 Content-Length 的请求体超过上限时直接返回 413，且不进入后续过滤器链 */
    void contentLengthOverLimit_shouldReject() throws Exception {
        MockHttpServletRequest req = requestWithLength("{\"message\":\"" + "A".repeat(100 * 1024) + "\"}");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull(); // 未放行到下游
    }

    @Test
    @DisplayName("chunked 超限（无 Content-Length 但实际字节数超限）：读取时截断返回 413")
    /** 验证场景：chunked（无 Content-Length）请求实际字节数超限时，按实际读取截断并返回 413 */
    void chunkedOverLimit_shouldRejectByActualRead() throws Exception {
        HttpServletRequest req = chunkedRequest("{\"message\":\"" + "A".repeat(100 * 1024) + "\"}");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("合法小请求：正常放行到后续链（body 被缓存重建供下游读取）")
    /** 验证场景：合法的较小请求正常放行，且 body 被缓存重建后下游可再次读取 */
    void smallBody_shouldPassThrough() throws Exception {
        MockHttpServletRequest req = requestWithLength("{\"message\":\"hi\"}");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200); // 链上无处理器时 mock 默认 200
        // 包装后的请求应能读到原始 body（缓存重建），且读取不报错
        assertThat(chain.getRequest()).isNotNull();
        byte[] read = chain.getRequest().getInputStream().readAllBytes();
        assertThat(new String(read)).isEqualTo("{\"message\":\"hi\"}");
    }

    @Test
    @DisplayName("非 /api/ai/chat 路径：不拦截")
    /** 验证场景：非 /api/ai/chat 路径的请求不被限流拦截，原样放行 */
    void otherPath_shouldPassThrough() throws Exception {
        MockHttpServletRequest req = requestWithLength("{\"a\":\"" + "A".repeat(100 * 1024) + "\"}");
        req.setRequestURI("/api/courses");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(chain.getRequest()).isSameAs(req);
    }
}
