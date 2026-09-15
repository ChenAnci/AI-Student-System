# GitHub OAuth 登录实现计划

> **For agentic workers:** 按任务顺序逐个实现，每完成一个任务提交一次。步骤使用 `- [ ]` 跟踪。

**Goal:** 为本系统新增"使用 GitHub 账号登录"，支持首次登录绑定现有工号/学号账号，绑定后一键登录。

**Architecture:** 复用现有 JWT 认证体系（`JwtUtil` + `JwtInterceptor`）。新增 `oauth_binding` 表存储 GitHub openid 与系统账号的绑定关系；新增 `OAuthController` 提供 authorize / callback / bind 三个无鉴权端点（WebConfig 放行），授权流程采用"整页跳转 + 302 回前端回调页"。GitHub 侧仅需 OAuth App（Client ID/Secret），个人开发者免费。

**Tech Stack:** Spring Boot 2.7（Java 17, MyBatis-Plus, JDK HttpClient）+ Vue 3（Vite, Vue Router, Pinia）+ MySQL 8 + GitHub OAuth App。

---

## 文件结构

**后端（backend/）**
- Create: `src/main/java/com/example/sms/entity/OAuthBinding.java`
- Create: `src/main/java/com/example/sms/mapper/OAuthBindingMapper.java`
- Create: `src/main/java/com/example/sms/dto/OAuthCallbackVO.java`
- Create: `src/main/java/com/example/sms/dto/OAuthBindDTO.java`
- Create: `src/main/java/com/example/sms/service/GithubOAuthService.java`
- Create: `src/main/java/com/example/sms/controller/OAuthController.java`
- Modify: `src/main/resources/application.yml`（oauth.github 配置）
- Modify: `src/main/java/com/example/sms/config/WebConfig.java`（放行 `/api/oauth/**`）

**数据库**
- Create: `sql/oauth_binding.sql`（新增表，不破坏 init.sql）

**前端（frontend/）**
- Create: `src/api/oauth.ts`
- Create: `src/views/OAuthCallback.vue`
- Create: `src/views/OAuthBind.vue`
- Modify: `src/views/LoginView.vue`（GitHub 登录按钮）
- Modify: `src/router/index.ts`（/oauth/callback、/oauth/bind 路由，public）
- Modify: `src/types/index.ts`（OAuth 类型）

---

### Task 1: 数据库表 oauth_binding

**Files:**
- Create: `sql/oauth_binding.sql`

- [ ] 创建 SQL 脚本（建表 + 索引，需在 MySQL 手动执行一次）

```sql
-- 第三方 OAuth 绑定关系表（GitHub 登录）
CREATE TABLE IF NOT EXISTS oauth_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_no VARCHAR(20) NOT NULL COMMENT '系统账号（工号/学号，对应 staff.staff_no / student.student_no）',
    provider VARCHAR(20) NOT NULL COMMENT 'OAuth 提供方：github',
    provider_uid VARCHAR(64) NOT NULL COMMENT 'GitHub 用户唯一 id',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_uid (provider, provider_uid),
    UNIQUE KEY uk_user_provider (user_no, provider)
) COMMENT 'OAuth 登录绑定关系表';
```

- [ ] 提交

```bash
git add sql/oauth_binding.sql
git commit -m "feat: 新增 oauth_binding 建表脚本（GitHub 登录绑定关系）"
```

---

### Task 2: 后端配置（application.yml + 环境变量）

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] 在 `jwt` 配置块后追加 OAuth 配置（密钥走环境变量，与 JWT 一致；未配置不阻断启动，接口层提示）

```yaml
# GitHub OAuth 登录（密钥通过环境变量注入；未配置时 authorize 接口返回提示，不影响主流程）
oauth:
  github:
    client-id: ${GITHUB_CLIENT_ID:}
    client-secret: ${GITHUB_CLIENT_SECRET:}
    redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8080/api/oauth/github/callback}
```

- [ ] 提交

```bash
git add backend/src/main/resources/application.yml
git commit -m "feat: 新增 GitHub OAuth 配置项（环境变量注入）"
```

---

### Task 3: OAuthBinding 实体 / Mapper / DTO

**Files:**
- Create: `backend/src/main/java/com/example/sms/entity/OAuthBinding.java`
- Create: `backend/src/main/java/com/example/sms/mapper/OAuthBindingMapper.java`
- Create: `backend/src/main/java/com/example/sms/dto/OAuthCallbackVO.java`
- Create: `backend/src/main/java/com/example/sms/dto/OAuthBindDTO.java`

- [ ] 实体（对应表字段，Lombok @Data）

```java
package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oauth_binding")
public class OAuthBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userNo;
    private String provider;
    private String providerUid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] Mapper（MyBatis-Plus 继承 BaseMapper）

```java
package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.OAuthBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OAuthBindingMapper extends BaseMapper<OAuthBinding> {
}
```

- [ ] 回调结果 VO（登录成功 / 待绑定）

```java
package com.example.sms.dto;

import lombok.Data;

@Data
public class OAuthCallbackVO {
    /** LOGIN_SUCCESS 直接登录成功 | NEED_BIND 需绑定现有账号 */
    private String status;
    /** 登录成功时的系统 JWT（与 LoginResponse.token 一致） */
    private String token;
    /** 待绑定时用于关联回调的 GitHub 用户唯一 id */
    private String providerUid;
}
```

- [ ] 绑定请求 DTO（账号密码 + 回调凭证）

```java
package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class OAuthBindDTO {
    @NotBlank(message = "账号不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "绑定凭证缺失")
    private String providerUid;
}
```

- [ ] 提交

```bash
git add backend/src/main/java/com/example/sms/entity/OAuthBinding.java backend/src/main/java/com/example/sms/mapper/OAuthBindingMapper.java backend/src/main/java/com/example/sms/dto/OAuthCallbackVO.java backend/src/main/java/com/example/sms/dto/OAuthBindDTO.java
git commit -m "feat: 新增 OAuth 绑定实体/Mapper/DTO"
```

---

### Task 4: GithubOAuthService

**Files:**
- Create: `backend/src/main/java/com/example/sms/service/GithubOAuthService.java`

- [ ] 服务实现：state 生成与一次性校验（内存缓存，10 分钟过期）、code 换 token、拉取 GitHub 用户、绑定校验与登录签发 JWT。使用 JDK 17 `java.net.http.HttpClient`（无额外依赖）。绑定账号校验复用 `AuthService` 的账号密码校验逻辑（新增一个按工号/学号+密码校验并返回 LoginResponse 的方法）。

先修改 `AuthService` 暴露一个校验方法，供绑定复用（避免重复密码校验逻辑）：

```java
// AuthService 中新增（复用 loginStaff/loginStudent 的校验与签发逻辑）
public LoginResponse verifyAndLogin(String username, String password) {
    // 不做锁定计数（OAuth 绑定场景），只校验账号密码与启用状态
    if (username.toUpperCase().startsWith("S") && !username.equalsIgnoreCase("admin")) {
        return loginStudent(username, password);
    }
    return loginStaff(username, password);
}
```

`GithubOAuthService` 核心代码：

```java
package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.LoginResponse;
import com.example.sms.dto.OAuthCallbackVO;
import com.example.sms.entity.OAuthBinding;
import com.example.sms.mapper.OAuthBindingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GithubOAuthService {

    private static final String PROVIDER = "github";
    private static final String AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final long STATE_TTL_MILLIS = 10 * 60 * 1000L;

    @Value("${oauth.github.client-id:}")
    private String clientId;
    @Value("${oauth.github.client-secret:}")
    private String clientSecret;
    @Value("${oauth.github.redirect-uri:}")
    private String redirectUri;

    @Autowired
    private OAuthBindingMapper bindingMapper;
    @Autowired
    private AuthService authService;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    /** state 缓存：state -> 过期时间戳（一次性使用） */
    private final ConcurrentHashMap<String, Long> stateStore = new ConcurrentHashMap<>();

    /** 1. 生成授权 URL（未配置时抛业务异常提示） */
    public String buildAuthorizeUrl() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new BusinessException("GitHub 登录未启用，请联系管理员配置 GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET");
        }
        String state = genState();
        return AUTH_URL + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=read:user&state=" + state;
    }

    /** 2. 回调处理：code+state → 已绑定返回登录成功，否则待绑定 */
    public OAuthCallbackVO handleCallback(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("授权回调缺少 code");
        }
        if (!consumeState(state)) {
            throw new BusinessException("回调 state 无效或已过期，请重新发起登录");
        }
        String accessToken = exchangeToken(code);
        JsonNode user = fetchGithubUser(accessToken);
        String uid = String.valueOf(user.path("id").asLong());
        OAuthBinding binding = findBinding(PROVIDER, uid);
        OAuthCallbackVO vo = new OAuthCallbackVO();
        if (binding != null) {
            vo.setStatus("LOGIN_SUCCESS");
            vo.setToken(issueTokenByUserNo(binding.getUserNo()));
        } else {
            vo.setStatus("NEED_BIND");
            vo.setProviderUid(uid);
        }
        return vo;
    }

    /** 3. 绑定现有账号 */
    public LoginResponse bind(String username, String password, String providerUid) {
        if (providerUid == null || providerUid.isBlank()) {
            throw new BusinessException("绑定凭证缺失，请重新发起 GitHub 登录");
        }
        // 防止一码多用：该 providerUid 必须尚未绑定
        if (findBinding(PROVIDER, providerUid) != null) {
            throw new BusinessException("该 GitHub 账号已绑定其他账号，请直接登录");
        }
        LoginResponse resp = authService.verifyAndLogin(username.trim(), password);
        OAuthBinding b = new OAuthBinding();
        b.setUserNo(resp.getUserNo());
        b.setProvider(PROVIDER);
        b.setProviderUid(providerUid);
        bindingMapper.insert(b);
        return resp;
    }

    // ===== 内部工具 =====

    private OAuthBinding findBinding(String provider, String uid) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<OAuthBinding>()
                .eq(OAuthBinding::getProvider, provider)
                .eq(OAuthBinding::getProviderUid, uid));
    }

    private LoginResponse issueTokenByUserNo(String userNo) {
        // 直接签发（已绑定账号必然存在且启用）
        return authService.issueByUserNo(userNo);
    }

    private String exchangeToken(String code) {
        String body = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri);
        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode json = sendJson(req);
        String token = json.path("access_token").asText(null);
        if (token == null) {
            throw new BusinessException("GitHub 授权失败：" + json.path("error_description").asText("获取 access_token 失败"));
        }
        return token;
    }

    private JsonNode fetchGithubUser(String accessToken) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(USER_URL))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .GET().build();
        return sendJson(req);
    }

    private JsonNode sendJson(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BusinessException("GitHub 接口调用失败（HTTP " + resp.statusCode() + "）");
            }
            return mapper.readTree(resp.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("GitHub 服务调用异常，请稍后重试");
        }
    }

    private String genState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stateStore.put(state, System.currentTimeMillis() + STATE_TTL_MILLIS);
        // 惰性清理过期 state，防止无界增长
        if (stateStore.size() > 1000) {
            stateStore.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis());
        }
        return state;
    }

    private boolean consumeState(String state) {
        if (state == null) return false;
        Long expire = stateStore.remove(state);
        return expire != null && expire > System.currentTimeMillis();
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
```

> `issueByUserNo` 需要在 `AuthService` 中新增：按 userNo 查对应 staff/student 并签发（复用 loginStaff/loginStudent 的构造，仅跳过密码校验）。实现：

```java
// AuthService 新增：OAuth 已绑定账号直接签发（账号已由绑定校验保证存在且启用）
public LoginResponse issueByUserNo(String userNo) {
    if (userNo.toUpperCase().startsWith("S") && !userNo.equalsIgnoreCase("admin")) {
        return issueStudent(userNo);
    }
    return issueStaff(userNo);
}

private LoginResponse issueStaff(String staffNo) {
    Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, staffNo));
    if (staff == null || !"ENABLED".equals(staff.getStatus())) {
        throw new BusinessException("账号不存在或已停用");
    }
    LoginResponse resp = new LoginResponse();
    resp.setUserId(staff.getId());
    resp.setUserNo(staff.getStaffNo());
    resp.setRealName(staff.getRealName());
    resp.setRoleType(staff.getRoleType());
    resp.setDepartment(staff.getDepartment());
    resp.setToken(jwtUtil.generateToken(staff.getId(), staff.getStaffNo(), staff.getRealName(), staff.getRoleType()));
    return resp;
}

private LoginResponse issueStudent(String studentNo) {
    Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));
    if (student == null || !"ENABLED".equals(student.getStatus())) {
        throw new BusinessException("账号不存在或已停用");
    }
    LoginResponse resp = new LoginResponse();
    resp.setUserId(student.getId());
    resp.setUserNo(student.getStudentNo());
    resp.setRealName(student.getRealName());
    resp.setRoleType("STUDENT");
    resp.setDepartment(student.getDepartment());
    resp.setMajor(student.getMajor());
    resp.setClassName(student.getClassName());
    resp.setToken(jwtUtil.generateToken(student.getId(), student.getStudentNo(), student.getRealName(), "STUDENT"));
    return resp;
}
```

- [ ] 提交

```bash
git add backend/src/main/java/com/example/sms/service/GithubOAuthService.java backend/src/main/java/com/example/sms/service/AuthService.java
git commit -m "feat: 实现 GitHub OAuth 服务（authorize/callback/bind + state 防 CSRF）"
```

---

### Task 5: OAuthController + 拦截器放行

**Files:**
- Create: `backend/src/main/java/com/example/sms/controller/OAuthController.java`
- Modify: `backend/src/main/java/com/example/sms/config/WebConfig.java`

- [ ] Controller（authorize 返回跳转地址；callback 302 到前端回调页；bind 签发登录）

```java
package com.example.sms.controller;

import com.example.sms.common.Result;
import com.example.sms.dto.LoginResponse;
import com.example.sms.dto.OAuthBindDTO;
import com.example.sms.dto.OAuthCallbackVO;
import com.example.sms.service.GithubOAuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Api(tags = "OAuth 登录")
@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    /** 前端回调页基址（整页跳转；开发走 vite 5173，生产替换为部署域名） */
    private static final String FRONT_BASE = "http://localhost:5173";

    @Autowired
    private GithubOAuthService githubOAuthService;

    @ApiOperation("获取 GitHub 授权跳转地址")
    @GetMapping("/github/authorize")
    public Result<Map<String, String>> authorize() {
        return Result.success(Map.of("url", githubOAuthService.buildAuthorizeUrl()));
    }

    @ApiOperation("GitHub 授权回调（302 重定向到前端回调页）")
    @GetMapping("/github/callback")
    public RedirectView callback(@RequestParam(value = "code", required = false) String code,
                                 @RequestParam(value = "state", required = false) String state) {
        OAuthCallbackVO vo = githubOAuthService.handleCallback(code, state);
        if ("LOGIN_SUCCESS".equals(vo.getStatus())) {
            return new RedirectView(FRONT_BASE + "/oauth/callback?token=" + enc(vo.getToken()));
        }
        return new RedirectView(FRONT_BASE + "/oauth/callback?needBind=1&providerUid=" + enc(vo.getProviderUid()));
    }

    @ApiOperation("绑定现有账号并登录")
    @PostMapping("/github/bind")
    public Result<LoginResponse> bind(@Valid @RequestBody OAuthBindDTO dto) {
        return Result.success(githubOAuthService.bind(dto.getUsername(), dto.getPassword(), dto.getProviderUid()));
    }

    private String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
```

- [ ] WebConfig 放行 OAuth 端点（无 token 即可访问 authorize/callback/bind）

```java
// WebConfig excludePathPatterns 追加
.excludePathPatterns(
        "/api/auth/login",
        "/api/oauth/**",
        "/favicon.ico"
);
```

- [ ] 提交

```bash
git add backend/src/main/java/com/example/sms/controller/OAuthController.java backend/src/main/java/com/example/sms/config/WebConfig.java
git commit -m "feat: 新增 OAuth 控制器并放行授权回调端点"
```

---

### Task 6: 前端 API 与类型

**Files:**
- Create: `frontend/src/api/oauth.ts`
- Modify: `frontend/src/types/index.ts`

- [ ] 类型（OAuth 回调与绑定请求）

```ts
// types/index.ts 追加
export interface OAuthCallbackResult {
  status: 'LOGIN_SUCCESS' | 'NEED_BIND'
  token?: string
  providerUid?: string
}
```

- [ ] API

```ts
// api/oauth.ts
import http from './http'
import type { LoginResponse } from '@/types'

export function getGithubAuthorizeUrl() {
  return http.get('/oauth/github/authorize') as Promise<{ url: string }>
}

export function bindGithub(data: { username: string; password: string; providerUid: string }) {
  return http.post('/oauth/github/bind', data) as Promise<LoginResponse>
}
```

- [ ] 提交

```bash
git add frontend/src/api/oauth.ts frontend/src/types/index.ts
git commit -m "feat: 前端新增 OAuth API 与类型"
```

---

### Task 7: 前端回调页 / 绑定页 / 路由

**Files:**
- Create: `frontend/src/views/OAuthCallback.vue`
- Create: `frontend/src/views/OAuthBind.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] 回调页（登录页整页跳转 GitHub → 后端 302 回本页：token 直接入库跳工作台；needBind 转绑定页）

```vue
<!-- OAuthCallback.vue -->
<template>
  <div class="oauth-page">
    <el-card class="oauth-card">
      <el-icon class="spin" :size="30" color="#409eff"><Loading /></el-icon>
      <p>{{ loading ? '正在完成登录...' : '登录成功，即将跳转' }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import type { LoginResponse } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)

function homeByRole(role: string): string {
  if (role === 'ADMIN') return '/admin/accounts'
  if (role === 'TEACHER') return '/teacher/courses'
  return '/student/dashboard'
}

onMounted(async () => {
  const token = route.query.token as string | undefined
  const needBind = route.query.needBind as string | undefined
  const providerUid = route.query.providerUid as string | undefined

  if (token) {
    const data = { token } as LoginResponse
    userStore.setLogin(data)
    loading.value = false
    ElMessage.success('登录成功')
    router.replace(homeByRole(data.roleType ?? 'STUDENT'))
    return
  }
  if (needBind && providerUid) {
    loading.value = false
    router.replace({ path: '/oauth/bind', query: { providerUid } })
    return
  }
  loading.value = false
  ElMessage.error('登录失败，请重试')
  router.replace('/login')
})
</script>

<style scoped>
.oauth-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f2f7ff 0%, #eaf1fc 55%, #f6f9ff 100%);
}
.oauth-card {
  width: 320px;
  text-align: center;
  padding: 8px;
}
.spin {
  animation: rotate 1s linear infinite;
  margin-bottom: 12px;
}
@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>
```

> 注：回调页 `setLogin(data)` 后 `user.roleType` 缺失（后端回调只带 token）。为获取用户信息，回调页可再调用一次 `/api/accounts/me` 补全，或后端回调 302 时同时携带 userNo/realName/roleType。**采用后端 302 携带完整登录信息**（修改 Task 5 的 callback：302 query 追加 `&userNo=&realName=&roleType=`），回调页构造完整 LoginResponse 后 setLogin，避免额外请求。

- [ ] 绑定页（输入工号/学号+密码，绑定当前 GitHub）

```vue
<!-- OAuthBind.vue -->
<template>
  <div class="bind-page">
    <el-card class="bind-card">
      <h2>绑定账号</h2>
      <p class="tip">首次使用 GitHub 登录，请绑定您的系统账号（工号/学号）</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleBind">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入工号/学号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="bind-btn" :loading="loading" @click="handleBind">绑定并登录</el-button>
      </el-form>
      <el-button text type="primary" @click="goLogin">返回账号密码登录</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { bindGithub } from '@/api/oauth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const providerUid = String(route.query.providerUid || '')

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入工号/学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function homeByRole(role: string): string {
  if (role === 'ADMIN') return '/admin/accounts'
  if (role === 'TEACHER') return '/teacher/courses'
  return '/student/dashboard'
}

async function handleBind() {
  if (!providerUid) {
    ElMessage.error('绑定凭证缺失，请重新通过 GitHub 登录')
    return
  }
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = await bindGithub({ ...form, providerUid })
    userStore.setLogin(data)
    ElMessage.success(`绑定成功，欢迎 ${data.realName}`)
    router.replace(homeByRole(data.roleType))
  } finally {
    loading.value = false
  }
}

function goLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.bind-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f2f7ff 0%, #eaf1fc 55%, #f6f9ff 100%);
}
.bind-card {
  width: 400px;
  padding: 8px;
  text-align: center;
}
.bind-card h2 {
  margin-bottom: 6px;
}
.tip {
  color: #5a6b87;
  font-size: 13px;
  margin-bottom: 24px;
}
.bind-btn {
  width: 100%;
  margin-bottom: 8px;
}
</style>
```

- [ ] 路由（public 放行，回调与绑定页无需登录）

```ts
// router/index.ts 在 Login 路由后追加
{
  path: '/oauth/callback',
  name: 'OAuthCallback',
  component: () => import('@/views/OAuthCallback.vue'),
  meta: { public: true, title: '登录中' }
},
{
  path: '/oauth/bind',
  name: 'OAuthBind',
  component: () => import('@/views/OAuthBind.vue'),
  meta: { public: true, title: '绑定账号' }
},
```

- [ ] 提交

```bash
git add frontend/src/views/OAuthCallback.vue frontend/src/views/OAuthBind.vue frontend/src/router/index.ts
git commit -m "feat: 前端新增 OAuth 回调页与绑定页路由"
```

---

### Task 8: 登录页增加 GitHub 登录入口

**Files:**
- Modify: `frontend/src/views/LoginView.vue`

- [ ] 表单下方增加 GitHub 登录按钮与分隔线

```vue
<!-- 在 .login-btn 的 el-form-item 之后、.login-tips 之前追加 -->
<el-divider class="oauth-divider"><span class="divider-text">或使用以下方式登录</span></el-divider>

<el-button class="github-btn" :loading="githubLoading" @click="handleGithubLogin">
  <svg class="gh-icon" viewBox="0 0 16 16" aria-hidden="true"><path fill="currentColor" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>
  使用 GitHub 登录
</el-button>
```

- [ ] script 增加处理函数与样式

```ts
import { getGithubAuthorizeUrl } from '@/api/oauth'

const githubLoading = ref(false)

async function handleGithubLogin() {
  githubLoading.value = true
  try {
    const data = await getGithubAuthorizeUrl()
    // 整页跳转到 GitHub 授权（完成后由后端 302 回 /oauth/callback）
    window.location.href = data.url
  } catch {
    githubLoading.value = false
  }
}
```

```css
.oauth-divider {
  margin: 4px 0 18px;
  --el-border-color: #d9e2f0;
}
.divider-text {
  font-size: 12px;
  color: #8a97ad;
  padding: 0 10px;
}
.github-btn {
  width: 100%;
  height: 44px;
  border-radius: var(--radius-md);
  border: 1px solid #d9e2f0;
  background: #fff;
  color: #1f2d3d;
  font-size: 14px;
  font-weight: 600;
}
.github-btn:hover {
  border-color: var(--brand-blue);
  color: var(--brand-blue);
}
.gh-icon {
  width: 18px;
  height: 18px;
  margin-right: 8px;
  vertical-align: -3px;
}
```

- [ ] 提交

```bash
git add frontend/src/views/LoginView.vue
git commit -m "feat: 登录页新增 GitHub 登录入口"
```

---

### Task 9: 后端回调 302 携带完整登录信息（补全回调页用户态）

**Files:**
- Modify: `backend/src/main/java/com/example/sms/controller/OAuthController.java`
- Modify: `frontend/src/views/OAuthCallback.vue`

- [ ] `OAuthCallbackVO` 增加 `userNo / realName / roleType` 字段，回调 302 一并携带，前端直接构造 LoginResponse，避免回调页再请求 `/api/accounts/me`

```java
// OAuthCallbackVO 追加字段
private String userNo;
private String realName;
private String roleType;
```

`GithubOAuthService.handleCallback` 的 LOGIN_SUCCESS 分支填充：

```java
LoginResponse resp = issueByUserNo(binding.getUserNo());
vo.setStatus("LOGIN_SUCCESS");
vo.setToken(resp.getToken());
vo.setUserNo(resp.getUserNo());
vo.setRealName(resp.getRealName());
vo.setRoleType(resp.getRoleType());
```

`OAuthController.callback` 302 追加参数：

```java
return new RedirectView(FRONT_BASE + "/oauth/callback?token=" + enc(vo.getToken())
        + "&userNo=" + enc(vo.getUserNo())
        + "&realName=" + enc(vo.getRealName())
        + "&roleType=" + enc(vo.getRoleType()));
```

回调页构造完整数据：

```ts
const data = {
  token,
  userNo: route.query.userNo as string,
  realName: route.query.realName as string,
  roleType: route.query.roleType as LoginResponse['roleType']
} as LoginResponse
userStore.setLogin(data)
router.replace(homeByRole(data.roleType))
```

- [ ] 提交

```bash
git add backend/src/main/java/com/example/sms/dto/OAuthCallbackVO.java backend/src/main/java/com/example/sms/service/GithubOAuthService.java backend/src/main/java/com/example/sms/controller/OAuthController.java frontend/src/views/OAuthCallback.vue
git commit -m "feat: OAuth 回调携带完整登录信息，免二次请求"
```

---

### Task 10: 编译与功能验证

**Files:**
- 验证（无真实 GitHub 凭据时可验证：未配置分支、bind 分支）

- [ ] 后端编译

```bash
cd backend && mvn -q clean compile
```

预期：BUILD SUCCESS（含 OAuthController / GithubOAuthService / OAuthBinding 等新类）。

- [ ] 前端类型检查（vite 模块请求 200）

```bash
# 前端 vite dev 已运行，请求新模块确认无编译错误
Invoke-WebRequest http://localhost:5173/src/views/OAuthCallback.vue  # 200
Invoke-WebRequest http://localhost:5173/src/views/OAuthBind.vue      # 200
Invoke-WebRequest http://localhost:5173/src/api/oauth.ts             # 200
```

- [ ] 后端接口逻辑验证（未配置 GitHub 时 authorize 返回业务提示）

```bash
curl http://localhost:8080/api/oauth/github/authorize
# 期望：code=200, data.url 或 message 提示未配置（取决于 GITHUB_CLIENT_ID 是否注入）
```

- [ ] 绑定流程验证（无真实 GitHub 时，直接 POST bind 用现成 providerUid 模拟）

```bash
curl -X POST http://localhost:8080/api/oauth/github/bind \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456","providerUid":"test-uid-001"}'
# 期望：code=200，返回 LoginResponse（token）；再次用同 uid 绑定应报"已绑定"
```

- [ ] 提交（验证通过无代码改动则跳过；有修复则提交）

---

## 部署前提（不阻塞开发）
1. 在 GitHub → Settings → Developer settings → OAuth Apps 创建应用：
   - Homepage URL: `http://localhost:5173`（生产替换）
   - Authorization callback URL: `http://localhost:8080/api/oauth/github/callback`（生产替换为公网 HTTPS 域名）
2. 环境变量注入：`GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`（同 JWT_SECRET 方式），可选 `GITHUB_REDIRECT_URI`
3. MySQL 执行一次 `sql/oauth_binding.sql`

## 风险与注意
- 回调依赖公网 HTTPS（生产）；本地 localhost 可联调
- `FRONT_BASE` 硬编码 5173，生产需改为部署域名（计划内已标注，后续可抽为配置）
- 绑定时 `providerUid` 一次性防重（bind 时检查未绑定），防他人抢占
- state 内存级（重启失效），多实例部署建议换 Redis（同登录锁定一致的可接受范围）
