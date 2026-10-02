package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入实现该过滤器所需的 Servlet API、Spring 组件与 IO 工具类（Servlet 3.1 规范下的标准过滤器写法） ----
import org.springframework.http.HttpStatus; // Spring 的 HTTP 状态码枚举：用于发送 413/400 等状态码
import org.springframework.stereotype.Component; // @Component 注解：将该过滤器声明为 Spring 容器管理的组件

import javax.servlet.Filter; // Servlet 过滤器顶层接口：本类通过实现它来参与请求拦截
import javax.servlet.FilterChain; // 过滤器链：把请求继续传递给下一个过滤器或目标 Servlet
import javax.servlet.FilterConfig; // 过滤器初始化配置对象（init 方法的入参）
import javax.servlet.ReadListener; // 异步读取监听器：ServletInputStream 中需实现的抽象方法之一
import javax.servlet.ServletException; // Servlet 通用异常类型
import javax.servlet.ServletInputStream; // Servlet 输入流抽象类：用于包装请求体读取
import javax.servlet.ServletRequest; // 通用请求接口（doFilter 方法的入参类型）
import javax.servlet.ServletResponse; // 通用响应接口（doFilter 方法的入参类型）
import javax.servlet.http.HttpServletRequest; // HTTP 请求类：用于读取 URI、Content-Length、输入流等信息
import javax.servlet.http.HttpServletRequestWrapper; // 请求包装类：用于在保持原请求信息的前提下替换/重建请求体
import javax.servlet.http.HttpServletResponse; // HTTP 响应类：用于发送错误状态码
import java.io.ByteArrayInputStream; // 内存字节数组输入流：把缓存的 body 字节数组转换为可读流
import java.io.IOException; // IO 异常类型
import java.io.InputStream; // 字节输入流抽象类：用于读取请求体

/**
 * /api/ai/chat 请求体大小限制（P-3 / S-6）：
 * Tomcat maxPostSize 仅约束表单提交，JSON 请求体不受限；此处对 /api/ai/chat 双重限制：
 * 1) Content-Length 预检：声明长度超限直接 413，不读 body 即可拦截超大 JSON；
 * 2) 流式截断（chunked 等无 Content-Length 场景）：读取时按实际字节数计数，
 *    超过 64KB 立即终止并 413，随后把已读（未超限）内容缓存并重建请求体供下游正常解析。
 */
@Component // 将该过滤器注册为 Spring 组件：容器启动时自动发现并生效，对所有请求统一拦截
public class AiBodySizeFilter implements Filter { // 实现 Filter 接口：定义在请求进入 Controller 之前的自定义拦截逻辑

    /** 与 AI 侧字段上限（message 500 + history 20×2000 字符）匹配的宽松上限：64KB。 */
    private static final long MAX_BODY_BYTES = 64L * 1024; // 请求体大小上限常量（64KB）：超过即拒绝，防止超大 JSON 占满内存

    /**
     * 调用逻辑：Servlet 过滤器，由 Servlet 容器在请求体到达 Controller 之前调用，位于拦截器更前一层；
     * 仅对 /api/ai/chat 生效：Content-Length 预检 → 流式读取按实际字节截断 → 用缓存请求体重建流后交给过滤器链。
     * 为什么：Tomcat maxPostSize 只约束表单、管不住 JSON body，故自行双重限制——
     * Content-Length 预检可在不读 body 时直接 413；chunked 等无 Content-Length 的传输也能按实际读取字节数拦截，
     * 防止超大请求体占满内存（防内存 DoS）。
     */
    @Override // 重写 Filter 接口的 doFilter 方法：核心拦截逻辑入口
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) // 方法签名：通用请求、通用响应、过滤器链
            throws IOException, ServletException { // 声明该方法可能抛出的 IO/ Servlet 异常（接口约定）
        // 仅拦截 /api/ai/chat 请求：其它接口不做请求体体积限制，直接放行
        if (!(request instanceof HttpServletRequest httpReq) // 先判断请求是否为 HTTP 请求（Java 16+ 模式匹配变量 httpReq）
                || !httpReq.getRequestURI().startsWith("/api/ai/chat")) { // 再判断请求 URI 是否以 /api/ai/chat 开头；任一不满足即不拦截
            chain.doFilter(request, response); // 非目标请求：原样放行给后续过滤器/目标 Servlet 处理
            return; // 本次过滤结束，直接返回
        }
        HttpServletResponse httpResp = (HttpServletResponse) response; // 将响应强转为 HTTP 响应，便于后续发送错误状态码

        // 1) Content-Length 预检：声明长度超限直接 413（无需读 body）
        int contentLength = httpReq.getContentLength(); // 读取请求头声明的 Content-Length（未声明时为 -1）
        if (contentLength > MAX_BODY_BYTES) { // 声明长度已超过 64KB 上限
            httpResp.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "request body too large"); // 不读取 body，直接返回 413 状态码
            return; // 结束本次过滤，请求被拒绝
        }

        // 2) 读取并计数：无论 Content-Length 是否存在，都按实际读取字节数限制。
        //    chunked（getContentLength() == -1）场景只有读到才能知道大小，超限即终止。
        //    未超限则把读到的 body 缓存，重建请求流供下游（Jackson 解析）读取——避免"读后流为空"。
        byte[] body = readLimited(httpReq.getInputStream(), httpResp); // 实际读取请求体并按字节数计数；超限或出错时返回 null
        if (body == null) { // 读取过程中超限或发生异常（此时已向响应写入 4xx 状态码）
            return; // 已写 413
        }
        chain.doFilter(new CachedBodyRequest(httpReq, body), response); // 用"缓存了 body 的请求包装对象"继续走过滤器链，保证下游能读到请求体
    }

    /** 读取输入流并按上限计数；超限写 413 并返回 null，正常返回已读内容（可能为空数组） */
    private byte[] readLimited(InputStream in, HttpServletResponse response) throws IOException { // 私有方法：带大小限制地读取请求体
        byte[] buf = new byte[8192]; // 8KB 读缓冲区：每次 read 最多读这么多字节
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); // 内存输出流：暂存"已读且未超限"的内容
        long count = 0; // 已读字节累计计数
        int n; // 记录单次 read 实际读到的字节数
        try { // 开始读取循环
            while ((n = in.read(buf)) != -1) { // 循环读取，直到流末尾（read 返回 -1）
                count += n; // 累加本次读取的字节数
                if (count > MAX_BODY_BYTES) { // 累计字节数已超过 64KB 上限
                    if (!response.isCommitted()) { // 响应尚未提交（还没写出任何内容）
                        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "request body too large"); // 发送 413 状态码
                    }
                    return null; // 返回 null 表示本次读取超限
                }
                out.write(buf, 0, n); // 未超限：把本次读到的内容写入缓存输出流
            }
        } catch (IOException e) { // 读取过程中发生连接中断等 IO 异常
            // 连接中断等读取异常：按请求体错误处理（不进入业务逻辑）
            if (!response.isCommitted()) { // 响应尚未提交
                response.sendError(HttpStatus.BAD_REQUEST.value(), "request body read error"); // 发送 400 状态码
            }
            return null; // 返回 null 表示读取失败
        }
        return out.toByteArray(); // 正常读完：返回缓存的完整请求体字节数组（可能为空数组）
    }

    /** 缓存请求体：getInputStream() 返回内存中的已读内容，供下游多次读取 */
    private static class CachedBodyRequest extends HttpServletRequestWrapper { // 内部包装类：把已读的 body 缓存下来并重建请求流
        private final byte[] body; // 缓存的请求体字节数组

        CachedBodyRequest(HttpServletRequest request, byte[] body) { // 构造方法：接收原始请求对象与缓存的 body
            super(request); // 调用父类构造器：完整保留原始请求的 header、URI 等信息
            this.body = body; // 保存缓存的请求体内容
        }

        @Override // 重写 getInputStream 方法：下游（Jackson 等）通过它读取请求体
        public ServletInputStream getInputStream() { // 返回基于缓存 body 的输入流
            ByteArrayInputStream in = new ByteArrayInputStream(body); // 把缓存的字节数组包装成内存输入流
            return new ServletInputStream() { // 返回匿名 ServletInputStream 实现
                @Override // 重写单字节读取方法
                public int read() { // ServletInputStream 的抽象方法
                    return in.read(); // 委托给内存流读取下一个字节（-1 表示已读完）
                }

                @Override // 重写 isFinished 方法
                public boolean isFinished() { // Servlet 3.1 规范要求实现的异步读取状态方法
                    return in.available() == 0; // 剩余可读字节为 0 即视为读取完毕
                }

                @Override // 重写 isReady 方法
                public boolean isReady() { // Servlet 3.1 规范要求实现的异步读取状态方法
                    return true; // 内存流始终立即可读
                }

                @Override // 重写 setReadListener 方法
                public void setReadListener(ReadListener readListener) { // 本项目采用同步读取，无需监听器，留空实现
                }
            };
        }

        @Override // 重写 getContentLength 方法：返回缓存 body 的真实长度
        public int getContentLength() { // 下游解析时可能依据该值分配缓冲区
            return body.length; // 返回缓存 body 的字节长度
        }

        @Override // 重写 getContentLengthLong 方法（长整型版本）
        public long getContentLengthLong() { // Servlet 3.1 新增接口
            return body.length; // 返回缓存 body 的字节长度
        }
    }

    @Override // 重写 init 方法：过滤器初始化回调
    public void init(FilterConfig filterConfig) { // 本过滤器无需读取配置参数，留空实现
    }

    @Override // 重写 destroy 方法：过滤器销毁回调
    public void destroy() { // 本过滤器没有需要释放的资源，留空实现
    }
}
