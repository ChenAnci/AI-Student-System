package com.example.sms.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * /api/ai/chat 请求体大小限制（P-3 / S-6）：
 * Tomcat maxPostSize 仅约束表单提交，JSON 请求体不受限；此处对 /api/ai/chat 双重限制：
 * 1) Content-Length 预检：声明长度超限直接 413，不读 body 即可拦截超大 JSON；
 * 2) 流式截断（chunked 等无 Content-Length 场景）：读取时按实际字节数计数，
 *    超过 64KB 立即终止并 413，随后把已读（未超限）内容缓存并重建请求体供下游正常解析。
 */
@Component
public class AiBodySizeFilter implements Filter {

    /** 与 AI 侧字段上限（message 500 + history 20×2000 字符）匹配的宽松上限：64KB。 */
    private static final long MAX_BODY_BYTES = 64L * 1024;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpReq)
                || !httpReq.getRequestURI().startsWith("/api/ai/chat")) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // 1) Content-Length 预检：声明长度超限直接 413（无需读 body）
        int contentLength = httpReq.getContentLength();
        if (contentLength > MAX_BODY_BYTES) {
            httpResp.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "request body too large");
            return;
        }

        // 2) 读取并计数：无论 Content-Length 是否存在，都按实际读取字节数限制。
        //    chunked（getContentLength() == -1）场景只有读到才能知道大小，超限即终止。
        //    未超限则把读到的 body 缓存，重建请求流供下游（Jackson 解析）读取——避免"读后流为空"。
        byte[] body = readLimited(httpReq.getInputStream(), httpResp);
        if (body == null) {
            return; // 已写 413
        }
        chain.doFilter(new CachedBodyRequest(httpReq, body), response);
    }

    /** 读取输入流并按上限计数；超限写 413 并返回 null，正常返回已读内容（可能为空数组） */
    private byte[] readLimited(InputStream in, HttpServletResponse response) throws IOException {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        long count = 0;
        int n;
        try {
            while ((n = in.read(buf)) != -1) {
                count += n;
                if (count > MAX_BODY_BYTES) {
                    if (!response.isCommitted()) {
                        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "request body too large");
                    }
                    return null;
                }
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            // 连接中断等读取异常：按请求体错误处理（不进入业务逻辑）
            if (!response.isCommitted()) {
                response.sendError(HttpStatus.BAD_REQUEST.value(), "request body read error");
            }
            return null;
        }
        return out.toByteArray();
    }

    /** 缓存请求体：getInputStream() 返回内存中的已读内容，供下游多次读取 */
    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
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

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
