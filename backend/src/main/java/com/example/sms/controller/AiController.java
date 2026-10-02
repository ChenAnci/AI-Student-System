// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：统一响应体 Result、用户上下文工具、Swagger 注解、Spring Web 注解与 RestTemplate、Servlet 与集合工具类 =====
import com.example.sms.common.Result;
import com.example.sms.util.UserContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 智能分析：学生/管理员问答（转发到独立 Python 服务，透传 Bearer JWT，由 AI 服务独立验签）
 */
// Swagger 注解：把本控制器在文档中归入"AI 智能分析"分组
@Api(tags = "AI 智能分析")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/ai 开头
@RequestMapping("/api/ai")
// AI 智能分析控制器：负责鉴权与请求转发，把学生/管理员的问答请求透传给独立的 AI 服务
public class AiController {

    // 读取配置项 ai.base-url：独立 AI Python 服务的基地址（来自 application 配置文件）
    @Value("${ai.base-url}")
    private String aiBaseUrl;

    // 注入专用 RestTemplate：用于向 AI 服务发起 HTTP 请求（独立配置，避免与默认实例混用）
    @Autowired
    private RestTemplate aiRestTemplate;

    // Swagger 接口说明：AI 问答（学生查本人 / 管理员查指定学生）
    @ApiOperation("AI 问答（学生查本人 / 管理员查指定学生）")
    // POST 映射：访问 /api/ai/chat 进入 AI 问答
    @PostMapping("/chat")
    // 处理 AI 问答请求：body 为前端传来的问题参数，request 用于读取原始请求头（Bearer 令牌）
    public Result<Map<String, String>> chat(@RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        // AI 问答接口：仅学生/管理员可用；后端只做鉴权与透传转发，不接触 AI 业务逻辑
        // 从登录上下文（ThreadLocal）取出当前用户信息
        UserContext.CurrentUser user = UserContext.get();
        // 未登录（用户为空或角色为空）则返回 401
        if (user == null || user.getRoleType() == null) {
            return Result.error(401, "未登录");
        }
        // 取出当前用户的角色类型
        String role = user.getRoleType();
        // 仅允许学生和管理员使用，其他角色返回 403 无权限
        if (!"STUDENT".equals(role) && !"ADMIN".equals(role)) {
            return Result.error(403, "无权限使用 AI 助手");
        }
        // 透传原始 Bearer 令牌，由 AI 服务独立验签，杜绝伪造身份头
        // 从原始请求头中取出 Authorization（Bearer JWT）令牌
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        // 令牌缺失或不是 Bearer 格式则视为未登录
        if (auth == null || !auth.startsWith("Bearer ")) {
            return Result.error(401, "未登录");
        }
        // 转发逻辑放在 try-catch 中：任何异常统一返回 502，避免把底层错误细节暴露给前端
        try {
            // targetStudentNo → target_student_no（Python 端字段）
            // 复制一份请求体，避免直接修改前端传入的原始 Map
            Map<String, Object> forward = new HashMap<>(body);
            // 读取可选参数 targetStudentNo（管理员查询指定学生时携带）
            Object target = body.get("targetStudentNo");
            // 从副本中移除驼峰命名字段，准备替换为 Python 端的下划线字段名
            forward.remove("targetStudentNo");
            // 仅当确实携带了目标学号时才放入转发字段
            if (target != null) {
                forward.put("target_student_no", target);
            }

            // 构造转发请求的 HTTP 头
            HttpHeaders headers = new HttpHeaders();
            // 声明请求体内容类型为 JSON
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 把原始 Authorization 头原样透传，由 AI 服务自行验签
            headers.set(HttpHeaders.AUTHORIZATION, auth);
            // 组装携带请求头与请求体的 HttpEntity 实体
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(forward, headers);
            // 向 AI 服务发送 POST 请求（路径 /api/chat），响应体按 Map 类型反序列化
            ResponseEntity<Map> resp = aiRestTemplate.postForEntity(aiBaseUrl + "/api/chat", entity, Map.class);
            // 响应状态为 2xx 且响应体非空才认为是成功
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                // 构造返回给前端的结果数据
                Map<String, String> data = new HashMap<>();
                // 取出 AI 的回答文本并统一转为字符串（防止 null 或数字等类型引发序列化问题）
                data.put("answer", String.valueOf(resp.getBody().get("answer")));
                // 返回成功响应
                return Result.success(data);
            }
            // AI 服务返回了非成功状态码，返回 502 表示上游异常
            return Result.error(502, "AI 服务响应异常");
        } catch (Exception e) {
            // 网络异常、超时等：统一返回 502，并提示用户稍后重试
            return Result.error(502, "AI 服务暂不可用，请稍后重试");
        }
    }
}
