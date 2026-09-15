package com.example.sms.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * /api/ai/chat 请求体大小限制（P-3）：
 * Tomcat maxPostSize 仅约束表单提交，JSON 请求体不受限；此处对 /api/ai/chat 按 Content-Length 预检，
 * 超限直接返回 413，避免超大 JSON 包被完整读入内存后再转发。
 */
@Component
public class AiBodySizeFilter implements Filter {

    /** 与 AI 侧字段上限（message 500 + history 20×2000 字符）匹配的宽松上限：64KB。 */
    private static final long MAX_BODY_BYTES = 64L * 1024;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpReq
                && httpReq.getRequestURI().startsWith("/api/ai/chat")
                && httpReq.getContentLength() > MAX_BODY_BYTES) {
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "request body too large");
            return;
        }
        chain.doFilter(request, response);
    }
}
