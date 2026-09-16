package com.example.sms.controller;

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
@Api(tags = "AI 智能分析")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Value("${ai.base-url}")
    private String aiBaseUrl;

    @Autowired
    private RestTemplate aiRestTemplate;

    @ApiOperation("AI 问答（学生查本人 / 管理员查指定学生）")
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        // AI 问答接口：仅学生/管理员可用；后端只做鉴权与透传转发，不接触 AI 业务逻辑
        UserContext.CurrentUser user = UserContext.get();
        if (user == null || user.getRoleType() == null) {
            return Result.error(401, "未登录");
        }
        String role = user.getRoleType();
        if (!"STUDENT".equals(role) && !"ADMIN".equals(role)) {
            return Result.error(403, "无权限使用 AI 助手");
        }
        // 透传原始 Bearer 令牌，由 AI 服务独立验签，杜绝伪造身份头
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return Result.error(401, "未登录");
        }
        try {
            // targetStudentNo → target_student_no（Python 端字段）
            Map<String, Object> forward = new HashMap<>(body);
            Object target = body.get("targetStudentNo");
            forward.remove("targetStudentNo");
            if (target != null) {
                forward.put("target_student_no", target);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, auth);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(forward, headers);
            ResponseEntity<Map> resp = aiRestTemplate.postForEntity(aiBaseUrl + "/api/chat", entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map<String, String> data = new HashMap<>();
                data.put("answer", String.valueOf(resp.getBody().get("answer")));
                return Result.success(data);
            }
            return Result.error(502, "AI 服务响应异常");
        } catch (Exception e) {
            return Result.error(502, "AI 服务暂不可用，请稍后重试");
        }
    }
}
