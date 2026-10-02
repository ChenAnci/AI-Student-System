package com.example.sms.config; // 声明包名：本测试类与被测过滤器 AiBodySizeFilter 位于同一个 config 包下

// import 区：引入 JUnit5 测试注解与 Spring MVC 提供的 Mock 请求/响应/过滤器链，用于在单元测试中模拟一次 HTTP 请求的完整链路
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// import 区：引入 Servlet 输入流相关接口与 HttpServletRequestWrapper，用于构造"chunked（无 Content-Length）"模拟请求
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

// import 区：静态导入 AssertJ 断言库，使断言写法更简洁（assertThat 直接可用）
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiBodySizeFilter 单元测试：Content-Length 预检与 chunked（无 Content-Length）实际读取截断
 */
class AiBodySizeFilterTest { // 测试类声明（包级可见）：直接 new 被测过滤器实例，不依赖 Spring 容器，运行更快

    private final AiBodySizeFilter filter = new AiBodySizeFilter(); // 创建被测过滤器实例，供各用例复用

    /** 模拟带 Content-Length 的普通请求 */
    private MockHttpServletRequest requestWithLength(String body) { // 工具方法：构造一个带 Content-Length 的普通 POST 请求
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ai/chat"); // 创建 POST 请求，URL 指向被限流拦截的 AI 聊天接口
        req.setContent(body.getBytes()); // 设置请求体字节内容，Mock 容器会依据内容自动算出 Content-Length
        return req; // 返回构造好的请求对象
    }

    /** 模拟 chunked 请求：getContentLength() 恒为 -1，body 走输入流 */
    private HttpServletRequest chunkedRequest(String body) { // 工具方法：构造一个模拟 chunked 传输的请求（无 Content-Length 头）
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ai/chat"); // 同样创建指向 AI 接口的 POST 请求
        req.setContent(body.getBytes()); // 先把 body 存进 Mock 请求，供后续包装类读取
        // 包装：强制 getContentLength() 返回 -1（chunked 无长度头），但保留真实 body 输入流
        return new HttpServletRequestWrapper(req) { // 用包装类覆盖长度相关方法，模拟 chunked 场景下的请求行为
            @Override
            public int getContentLength() { // 覆盖：始终返回 -1，模拟"没有 Content-Length 头"的情况
                return -1;
            }

            @Override
            public long getContentLengthLong() { // 覆盖：长整型版本同样返回 -1，保持一致
                return -1L;
            }

            @Override
            public ServletInputStream getInputStream() throws IOException { // 覆盖：返回一个可读取真实 body 的输入流
                ByteArrayInputStream in = new ByteArrayInputStream(body.getBytes()); // 把 body 字节包装成内存输入流，便于逐字节读取
                return new ServletInputStream() { // 返回匿名 ServletInputStream，实现 Servlet 输入流约定
                    @Override
                    public int read() { // 实现单字节读取，直接委托给内存输入流
                        return in.read();
                    }

                    @Override
                    public boolean isFinished() { // 判断是否读完：底层流无剩余字节即视为结束
                        return in.available() == 0;
                    }

                    @Override
                    public boolean isReady() { // 是否可读：同步读取场景下始终返回 true
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) { // 异步读取监听回调：本测试不用异步，留空实现
                    }
                };
            }
        };
    }

    @Test
    @DisplayName("Content-Length 超限：直接 413，不进入后续链")
    /** 验证场景：带 Content-Length 的请求体超过上限时直接返回 413，且不进入后续过滤器链 */
    void contentLengthOverLimit_shouldReject() throws Exception { // 用例1：Content-Length 超限应被直接拒绝
        MockHttpServletRequest req = requestWithLength("{\"message\":\"" + "A".repeat(100 * 1024) + "\"}"); // 构造约 100KB 的超大请求体（超过过滤器上限）
        MockHttpServletResponse resp = new MockHttpServletResponse(); // 创建 Mock 响应对象，用于捕获过滤器写出的状态码
        MockFilterChain chain = new MockFilterChain(); // 创建 Mock 过滤器链，用于记录请求是否被放行到下游

        filter.doFilter(req, resp, chain); // 调用被测过滤器的核心处理方法（即测试触发点）

        assertThat(resp.getStatus()).isEqualTo(413); // 断言：响应状态码为 413（Payload Too Large，请求体过大）
        assertThat(chain.getRequest()).isNull(); // 未放行到下游 // 断言：请求未被传给后续链（过滤器直接截断）
    }

    @Test
    @DisplayName("chunked 超限（无 Content-Length 但实际字节数超限）：读取时截断返回 413")
    /** 验证场景：chunked（无 Content-Length）请求实际字节数超限时，按实际读取截断并返回 413 */
    void chunkedOverLimit_shouldRejectByActualRead() throws Exception { // 用例2：chunked 请求按实际读取字节判断超限
        HttpServletRequest req = chunkedRequest("{\"message\":\"" + "A".repeat(100 * 1024) + "\"}"); // 构造无 Content-Length 但实际约 100KB 的 chunked 请求
        MockHttpServletResponse resp = new MockHttpServletResponse(); // 创建 Mock 响应对象
        MockFilterChain chain = new MockFilterChain(); // 创建 Mock 过滤器链

        filter.doFilter(req, resp, chain); // 调用过滤器：由于没有长度头，过滤器需边读边累计字节数

        assertThat(resp.getStatus()).isEqualTo(413); // 断言：累计读取超限后返回 413
        assertThat(chain.getRequest()).isNull(); // 断言：超限请求未放行到下游
    }

    @Test
    @DisplayName("合法小请求：正常放行到后续链（body 被缓存重建供下游读取）")
    /** 验证场景：合法的较小请求正常放行，且 body 被缓存重建后下游可再次读取 */
    void smallBody_shouldPassThrough() throws Exception { // 用例3：合法小请求应正常放行且 body 可被下游读取
        MockHttpServletRequest req = requestWithLength("{\"message\":\"hi\"}"); // 构造内容很小的合法请求体
        MockHttpServletResponse resp = new MockHttpServletResponse(); // 创建 Mock 响应对象
        MockFilterChain chain = new MockFilterChain(); // 创建 Mock 过滤器链

        filter.doFilter(req, resp, chain); // 调用过滤器：小请求应被放行到下游

        assertThat(resp.getStatus()).isEqualTo(200); // 链上无处理器时 mock 默认 200 // 断言：默认状态码为 200（放行成功）
        // 包装后的请求应能读到原始 body（缓存重建），且读取不报错
        assertThat(chain.getRequest()).isNotNull(); // 断言：请求确实被传到了下游链
        byte[] read = chain.getRequest().getInputStream().readAllBytes(); // 从放行后的请求输入流中读回全部字节
        assertThat(new String(read)).isEqualTo("{\"message\":\"hi\"}"); // 断言：读到的内容与原始 body 一致（缓存重建正确）
    }

    @Test
    @DisplayName("非 /api/ai/chat 路径：不拦截")
    /** 验证场景：非 /api/ai/chat 路径的请求不被限流拦截，原样放行 */
    void otherPath_shouldPassThrough() throws Exception { // 用例4：非 AI 接口路径不受限流拦截
        MockHttpServletRequest req = requestWithLength("{\"a\":\"" + "A".repeat(100 * 1024) + "\"}"); // 构造超大的请求体（即使超限也不该被拦截，因为路径不匹配）
        req.setRequestURI("/api/courses"); // 把请求路径改成非 AI 接口的路径，验证过滤器不做限流
        MockHttpServletResponse resp = new MockHttpServletResponse(); // 创建 Mock 响应对象
        MockFilterChain chain = new MockFilterChain(); // 创建 Mock 过滤器链

        filter.doFilter(req, resp, chain); // 调用过滤器：路径不匹配时应原样放行

        assertThat(chain.getRequest()).isSameAs(req); // 断言：放行的是同一个请求对象（未被包装或截断）
    }
}
