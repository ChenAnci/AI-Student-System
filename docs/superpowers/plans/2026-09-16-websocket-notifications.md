# 站内通知系统（WebSocket）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理员/老师向学生发送站内通知（手动 + 成绩发布/调课/选课成功自动触发），学生通过 WebSocket 实时接收，含未读角标、通知中心、已读管理。

**Architecture:** Spring Boot 2.7 原生 WebSocket（`spring-boot-starter-websocket`）+ 内存会话注册表；通知落库（notification + notification_receiver 两张表）；REST 走既有 JwtInterceptor fail-closed 规则；前端 Vue3 Pinia store 管理连接与未读数，顶栏铃铛 + 通知中心页。已发布课程放开"上课时间/地点"编辑（调课业务），保存时自动通知选课学生。

**Tech Stack:** Spring Boot 2.7.18 / Java 17 / MyBatis-Plus 3.5.5 / WebSocket / Redis（不参与本功能）/ Vue3 + Pinia + Element Plus + Vite

**前置：** 分支 `feature/websocket-notifications` 已从 main 创建，设计文档已提交（`docs/superpowers/specs/2026-09-16-websocket-notifications-design.md`）。

---

### Task 1: 数据库表 + 实体 + Mapper

**Files:**
- Create: `sql/notifications.sql`
- Create: `backend/src/main/java/com/example/sms/entity/Notification.java`
- Create: `backend/src/main/java/com/example/sms/entity/NotificationReceiver.java`
- Create: `backend/src/main/java/com/example/sms/mapper/NotificationMapper.java`
- Create: `backend/src/main/java/com/example/sms/mapper/NotificationReceiverMapper.java`
- Modify: `backend/pom.xml`

- [ ] **Step 1: pom.xml 添加 WebSocket 依赖**

在 `backend/pom.xml` 的 `spring-boot-starter-validation` 依赖之前插入：

```xml
        <!-- WebSocket：站内通知实时推送 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
```

- [ ] **Step 2: 建表 SQL**

创建 `sql/notifications.sql`（与 `oauth_binding.sql` 同级的独立脚本，幂等）：

```sql
-- ============================================================
-- 站内通知系统
-- MySQL 8.0 | 库名: student_management
-- ============================================================
USE student_management;

CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    type        VARCHAR(20)  NOT NULL COMMENT 'MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL',
    title       VARCHAR(100) NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    sender_type VARCHAR(10)  NOT NULL COMMENT 'ADMIN | TEACHER | SYSTEM',
    sender_id   BIGINT       NULL,
    sender_name VARCHAR(50)  NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notification_created (created_at)
) COMMENT '站内通知主表';

CREATE TABLE IF NOT EXISTS notification_receiver (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id BIGINT  NOT NULL,
    student_id      BIGINT  NOT NULL,
    is_read         TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    read_at         DATETIME NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_receiver_student (student_id, is_read),
    KEY idx_receiver_notification (notification_id)
) COMMENT '通知接收明细';
```

- [ ] **Step 3: 实体 Notification.java**

创建 `backend/src/main/java/com/example/sms/entity/Notification.java`：

```java
package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知主表实体
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL */
    private String type;

    private String title;

    private String content;

    /** ADMIN | TEACHER | SYSTEM */
    private String senderType;

    private Long senderId;

    private String senderName;

    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: 实体 NotificationReceiver.java**

创建 `backend/src/main/java/com/example/sms/entity/NotificationReceiver.java`：

```java
package com.example.sms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知接收明细实体（一对多：一条通知对应多个学生）
 */
@Data
@TableName("notification_receiver")
public class NotificationReceiver {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long notificationId;

    private Long studentId;

    /** 0未读 1已读 */
    @TableField("is_read")
    private Boolean read;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Mapper 两个**

创建 `backend/src/main/java/com/example/sms/mapper/NotificationMapper.java`：

```java
package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
```

创建 `backend/src/main/java/com/example/sms/mapper/NotificationReceiverMapper.java`：

```java
package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.NotificationReceiver;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationReceiverMapper extends BaseMapper<NotificationReceiver> {
}
```

- [ ] **Step 6: 编译验证**

Run: `cd backend; mvn -q compile`
Expected: exit 0，无编译错误。

- [ ] **Step 7: 提交**

```bash
git add sql/notifications.sql backend/pom.xml backend/src/main/java/com/example/sms/entity/Notification.java backend/src/main/java/com/example/sms/entity/NotificationReceiver.java backend/src/main/java/com/example/sms/mapper/NotificationMapper.java backend/src/main/java/com/example/sms/mapper/NotificationReceiverMapper.java
git commit -m "feat: 通知表结构、实体与 Mapper，引入 WebSocket 依赖"
```

---

### Task 2: WebSocket 基础设施

**Files:**
- Create: `backend/src/main/java/com/example/sms/websocket/WsSessionRegistry.java`
- Create: `backend/src/main/java/com/example/sms/websocket/WsAuthHandshakeInterceptor.java`
- Create: `backend/src/main/java/com/example/sms/websocket/NotificationWebSocketHandler.java`
- Create: `backend/src/main/java/com/example/sms/config/WebSocketConfig.java`

- [ ] **Step 1: 会话注册表**

创建 `backend/src/main/java/com/example/sms/websocket/WsSessionRegistry.java`：

```java
package com.example.sms.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 在线会话注册表（单实例内存实现，支持同一用户多标签页）
 */
@Component
public class WsSessionRegistry {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return;
        set.remove(session);
        if (set.isEmpty()) sessions.remove(userId);
    }

    /** 向指定用户的所有在线会话推送消息；失效会话静默清理 */
    public void sendToUser(Long userId, String json) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return;
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    // 发送失败由 close/error 回调清理，消息已落库不重试
                }
            } else {
                set.remove(session);
            }
        }
    }
}
```

- [ ] **Step 2: 握手认证拦截器**

创建 `backend/src/main/java/com/example/sms/websocket/WsAuthHandshakeInterceptor.java`：

```java
package com.example.sms.websocket;

import com.example.sms.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手认证：从 ?token= 解析 JWT，仅允许学生连接；失败拒绝握手
 */
@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = ((ServletServerHttpRequest) request).getServletRequest().getParameter("token");
        Claims claims = (token != null) ? jwtUtil.parseToken(token) : null;
        if (claims == null || !"STUDENT".equals(claims.get("roleType"))) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("userId", ((Number) claims.get("userId")).longValue());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
```

- [ ] **Step 3: 消息处理器**

创建 `backend/src/main/java/com/example/sms/websocket/NotificationWebSocketHandler.java`：

```java
package com.example.sms.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * 通知 WebSocket 处理器：连接注册/断开清理；心跳 PING/PONG
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private WsSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object raw = session.getAttributes().get("userId");
        if (!(raw instanceof Long)) {
            closeQuietly(session);
            return;
        }
        registry.add((Long) raw, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("{\"type\":\"PING\"}".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object raw = session.getAttributes().get("userId");
        if (raw instanceof Long) registry.remove((Long) raw, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Object raw = session.getAttributes().get("userId");
        if (raw instanceof Long) registry.remove((Long) raw, session);
        closeQuietly(session);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
            // no-op
        }
    }
}
```

- [ ] **Step 4: WebSocket 配置**

创建 `backend/src/main/java/com/example/sms/config/WebSocketConfig.java`：

```java
package com.example.sms.config;

import com.example.sms.websocket.NotificationWebSocketHandler;
import com.example.sms.websocket.WsAuthHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册通知端点（握手由 WsAuthHandshakeInterceptor 认证）
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Autowired
    private WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(wsAuthHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `cd backend; mvn -q compile`
Expected: exit 0。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/example/sms/websocket backend/src/main/java/com/example/sms/config/WebSocketConfig.java
git commit -m "feat: WebSocket 基础设施（握手认证/会话注册表/心跳）"
```

---

### Task 3: 通知服务 + REST 接口 + 角色规则

**Files:**
- Create: `backend/src/main/java/com/example/sms/dto/SendNotificationDTO.java`
- Create: `backend/src/main/java/com/example/sms/vo/NotificationVO.java`
- Create: `backend/src/main/java/com/example/sms/service/NotificationService.java`
- Create: `backend/src/main/java/com/example/sms/controller/NotificationController.java`
- Modify: `backend/src/main/java/com/example/sms/config/JwtInterceptor.java`

- [ ] **Step 1: 发送请求 DTO**

创建 `backend/src/main/java/com/example/sms/dto/SendNotificationDTO.java`：

```java
package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 手动发送通知请求
 */
@Data
public class SendNotificationDTO {

    @NotBlank(message = "通知标题不能为空")
    private String title;

    @NotBlank(message = "通知内容不能为空")
    private String content;

    @NotNull(message = "接收对象不能为空")
    private Target target;

    @Data
    public static class Target {
        /** STUDENT_IDS | CLASS | MAJOR | DEPARTMENT | COURSE | ALL */
        @NotBlank(message = "接收方式不能为空")
        private String kind;

        private List<Long> studentIds;
        private String className;
        private String major;
        private String department;
        private Long courseId;
    }
}
```

- [ ] **Step 2: 返回 VO**

创建 `backend/src/main/java/com/example/sms/vo/NotificationVO.java`：

```java
package com.example.sms.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象
 */
@Data
public class NotificationVO {

    /** 通知主表 id */
    private Long id;

    /** 接收明细 id（学生用于标记已读；发件箱为 null） */
    private Long receiverId;

    private String type;

    private String title;

    private String content;

    private String senderName;

    /** 当前学生是否已读（发件箱为 null） */
    private Boolean read;

    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 通知服务**

创建 `backend/src/main/java/com/example/sms/service/NotificationService.java`：

```java
package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.SendNotificationDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.Notification;
import com.example.sms.entity.NotificationReceiver;
import com.example.sms.entity.Student;
import com.example.sms.entity.StudentCourse;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.NotificationMapper;
import com.example.sms.mapper.NotificationReceiverMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.UserContext;
import com.example.sms.vo.NotificationVO;
import com.example.sms.websocket.WsSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知服务：发送（手动/系统）、收件箱、未读数、已读、发件箱
 */
@Slf4j
@Service
public class NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private NotificationReceiverMapper receiverMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private WsSessionRegistry wsSessionRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== 发送 =====

    /** 手动发送（发送者取自 UserContext，老师仅可发给自己的课程学生） */
    @Transactional
    public void send(SendNotificationDTO dto) {
        List<Long> studentIds = resolveTargets(dto.getTarget());
        if (studentIds.isEmpty()) {
            throw new BusinessException("未匹配到任何学生");
        }
        UserContext.CurrentUser user = UserContext.get();
        doSend("MANUAL", dto.getTitle().trim(), dto.getContent().trim(),
                user.getRoleType(), user.getUserId(), user.getRealName(), studentIds);
    }

    /** 系统自动触发（成绩发布/调课/选课成功）；接收人为空时静默跳过 */
    @Transactional
    public void sendSystem(String type, String title, String content, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return;
        doSend(type, title, content, "SYSTEM", null, "系统", studentIds);
    }

    private void doSend(String type, String title, String content, String senderType,
                        Long senderId, String senderName, List<Long> studentIds) {
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setSenderType(senderType);
        n.setSenderId(senderId);
        n.setSenderName(senderName);
        notificationMapper.insert(n);

        List<Long> distinctIds = studentIds.stream().distinct().collect(Collectors.toList());
        for (Long sid : distinctIds) {
            NotificationReceiver r = new NotificationReceiver();
            r.setNotificationId(n.getId());
            r.setStudentId(sid);
            r.setRead(false);
            receiverMapper.insert(r);
        }

        // 事务提交后推送（保证落库可见后再通知前端）
        Runnable push = () -> pushToOnline(n, distinctIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    private void pushToOnline(Notification n, List<Long> studentIds) {
        try {
            Map<Long, Long> sid2Rid = receiverMapper.selectList(new LambdaQueryWrapper<NotificationReceiver>()
                            .eq(NotificationReceiver::getNotificationId, n.getId()))
                    .stream().collect(Collectors.toMap(NotificationReceiver::getStudentId, NotificationReceiver::getId));
            for (Long sid : studentIds) {
                Long receiverId = sid2Rid.get(sid);
                if (receiverId == null) continue;
                String json = objectMapper.writeValueAsString(Map.of(
                        "type", "NOTIFICATION",
                        "data", Map.of(
                                "receiverId", receiverId,
                                "notificationId", n.getId(),
                                "title", n.getTitle(),
                                "content", n.getContent(),
                                "type", n.getType(),
                                "createdAt", n.getCreatedAt() == null ? LocalDateTime.now().toString() : n.getCreatedAt().toString())));
                wsSessionRegistry.sendToUser(sid, json);
            }
        } catch (Exception e) {
            log.warn("通知推送失败（消息已落库）：{}", e.getMessage());
        }
    }

    // ===== 接收人解析 =====

    private List<Long> resolveTargets(SendNotificationDTO.Target target) {
        String kind = target.getKind();
        switch (kind) {
            case "STUDENT_IDS":
                if (target.getStudentIds() == null || target.getStudentIds().isEmpty()) {
                    throw new BusinessException("请选择接收学生");
                }
                return target.getStudentIds();
            case "CLASS":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getClassName, target.getClassName()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "MAJOR":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getMajor, target.getMajor()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "DEPARTMENT":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getDepartment, target.getDepartment()))
                        .stream().map(Student::getId).collect(Collectors.toList());
            case "COURSE": {
                if (target.getCourseId() == null) throw new BusinessException("请选择课程");
                Course course = courseMapper.selectById(target.getCourseId());
                if (course == null) throw new BusinessException("课程不存在");
                if ("TEACHER".equals(UserContext.getRole())
                        && !Objects.equals(course.getTeacherId(), UserContext.getUserId())) {
                    throw new BusinessException(403, "只能向自己授课课程的学生发送通知");
                }
                return studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>()
                                .eq(StudentCourse::getCourseId, target.getCourseId()))
                        .stream().map(StudentCourse::getStudentId).collect(Collectors.toList());
            }
            case "ALL":
                return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .eq(Student::getStatus, "ENABLED"))
                        .stream().map(Student::getId).collect(Collectors.toList());
            default:
                throw new BusinessException("不支持的接收方式：" + kind);
        }
    }

    // ===== 学生收件箱 =====

    public Page<NotificationVO> inbox(int page, int size) {
        Long userId = UserContext.getUserId();
        Page<NotificationReceiver> p = receiverMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<NotificationReceiver>()
                        .eq(NotificationReceiver::getStudentId, userId)
                        .orderByDesc(NotificationReceiver::getId));
        return toVoPage(p);
    }

    public long unreadCount() {
        return receiverMapper.selectCount(new LambdaQueryWrapper<NotificationReceiver>()
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId())
                .eq(NotificationReceiver::getRead, false));
    }

    public void markRead(Long receiverId) {
        Long userId = UserContext.getUserId();
        NotificationReceiver r = receiverMapper.selectById(receiverId);
        if (r == null || !Objects.equals(r.getStudentId(), userId)) {
            throw new BusinessException(403, "无权操作该通知");
        }
        if (!Boolean.TRUE.equals(r.getRead())) {
            r.setRead(true);
            r.setReadAt(LocalDateTime.now());
            receiverMapper.updateById(r);
        }
    }

    public void readAll() {
        receiverMapper.update(null, new LambdaUpdateWrapper<NotificationReceiver>()
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId())
                .eq(NotificationReceiver::getRead, false)
                .set(NotificationReceiver::getRead, true)
                .set(NotificationReceiver::getReadAt, LocalDateTime.now()));
    }

    // ===== 老师/管理员发件箱 =====

    public Page<NotificationVO> sent(int page, int size) {
        Long userId = UserContext.getUserId();
        Page<Notification> p = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getSenderId, userId)
                        .orderByDesc(Notification::getId));
        Page<NotificationVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(p.getRecords().stream().map(n -> {
            NotificationVO v = new NotificationVO();
            v.setId(n.getId());
            v.setType(n.getType());
            v.setTitle(n.getTitle());
            v.setContent(n.getContent());
            v.setSenderName(n.getSenderName());
            v.setCreatedAt(n.getCreatedAt());
            return v;
        }).collect(Collectors.toList()));
        return vo;
    }

    private Page<NotificationVO> toVoPage(Page<NotificationReceiver> p) {
        Page<NotificationVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        if (p.getRecords().isEmpty()) {
            vo.setRecords(List.of());
            return vo;
        }
        List<Long> nids = p.getRecords().stream()
                .map(NotificationReceiver::getNotificationId).distinct().collect(Collectors.toList());
        Map<Long, Notification> nMap = notificationMapper.selectBatchIds(nids).stream()
                .collect(Collectors.toMap(Notification::getId, Function.identity()));
        vo.setRecords(p.getRecords().stream().map(r -> {
            NotificationVO v = new NotificationVO();
            Notification n = nMap.get(r.getNotificationId());
            if (n == null) return null;
            v.setId(n.getId());
            v.setReceiverId(r.getId());
            v.setType(n.getType());
            v.setTitle(n.getTitle());
            v.setContent(n.getContent());
            v.setSenderName(n.getSenderName());
            v.setRead(r.getRead());
            v.setCreatedAt(n.getCreatedAt());
            return v;
        }).filter(Objects::nonNull).collect(Collectors.toList()));
        return vo;
    }
}
```

- [ ] **Step 4: REST 控制器**

创建 `backend/src/main/java/com/example/sms/controller/NotificationController.java`：

```java
package com.example.sms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.sms.common.Result;
import com.example.sms.dto.SendNotificationDTO;
import com.example.sms.service.NotificationService;
import com.example.sms.vo.NotificationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 站内通知接口
 */
@Api(tags = "站内通知")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @ApiOperation("学生：收件箱分页")
    @GetMapping
    public Result<Page<NotificationVO>> inbox(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(notificationService.inbox(page, size));
    }

    @ApiOperation("学生：未读数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.unreadCount());
    }

    @ApiOperation("学生：标记已读")
    @PutMapping("/{receiverId}/read")
    public Result<Void> markRead(@PathVariable Long receiverId) {
        notificationService.markRead(receiverId);
        return Result.success();
    }

    @ApiOperation("学生：全部已读")
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.readAll();
        return Result.success();
    }

    @ApiOperation("老师/教秘：发件箱分页")
    @GetMapping("/sent")
    public Result<Page<NotificationVO>> sent(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(notificationService.sent(page, size));
    }

    @ApiOperation("老师/教秘：发送通知")
    @PostMapping("/send")
    public Result<Void> send(@Valid @RequestBody SendNotificationDTO dto) {
        notificationService.send(dto);
        return Result.success();
    }
}
```

- [ ] **Step 5: JwtInterceptor 追加角色规则**

修改 `backend/src/main/java/com/example/sms/config/JwtInterceptor.java`，在 ROLE_RULES 数组末尾（`{"/api/ai/**", "STUDENT,ADMIN"}` 之后）追加：

```java
            // 站内通知（精确规则在前，fail-closed）
            {"/api/notifications/unread-count", "STUDENT"},
            {"/api/notifications/read-all",     "STUDENT"},
            {"/api/notifications/sent",         "TEACHER,ADMIN"},
            {"/api/notifications/*/read",       "STUDENT"},
            {"/api/notifications/send",         "TEACHER,ADMIN"},
            {"/api/notifications/**",           "STUDENT"},
```

- [ ] **Step 6: 编译验证**

Run: `cd backend; mvn -q compile`
Expected: exit 0。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/example/sms/dto/SendNotificationDTO.java backend/src/main/java/com/example/sms/vo/NotificationVO.java backend/src/main/java/com/example/sms/service/NotificationService.java backend/src/main/java/com/example/sms/controller/NotificationController.java backend/src/main/java/com/example/sms/config/JwtInterceptor.java
git commit -m "feat: 通知服务与 REST 接口（发送/收件箱/未读/已读/发件箱）+ 角色规则"
```

---

### Task 4: 自动触发挂钩

**Files:**
- Modify: `backend/src/main/java/com/example/sms/service/GradeService.java`
- Modify: `backend/src/main/java/com/example/sms/service/CourseService.java`
- Modify: `backend/src/main/java/com/example/sms/service/EnrollService.java`

- [ ] **Step 1: GradeService 成绩发布通知**

在 `GradeService.java` 增加注入（在 `@Autowired private CourseGradeAuditMapper auditMapper;` 之后）：

```java
    @Autowired
    private NotificationService notificationService;
```

在 `publish(Long courseId)` 方法末尾（`recalcStudentCredits` 循环之后）追加：

```java
        // 成绩发布自动通知（接收人=该课程选课学生）
        notificationService.sendSystem("GRADE_PUBLISH",
                "成绩已发布",
                "「" + course.getCourseName() + "」成绩已发布，可登录系统查询。",
                scs.stream().map(StudentCourse::getStudentId).collect(Collectors.toList()));
```

注意 `publish` 已声明 `@Transactional`，`sendSystem` 的推送会在提交后执行。

- [ ] **Step 2: CourseService 调课放开 + 变更通知**

在 `CourseService.java` 增加注入（在 `@Autowired private CourseGradeAuditMapper auditMapper;` 之后）：

```java
    @Autowired
    private StudentCourseMapper studentCourseMapper;

    @Autowired
    private NotificationService notificationService;
```

将 `updateCourse(Long id, CourseFormDTO dto)` 整体替换为（已发布课程仅允许修改时间/地点并自动通知）：

```java
    /** 编辑课程：未发布可全量编辑；已发布仅允许调课（修改上课时间/地点），其余字段强制保留，变化时通知选课学生 */
    public void updateCourse(Long id, CourseFormDTO dto) {
        Course course = getEditableCourse(id);
        if ("PUBLISHED".equals(course.getStatus())) {
            String oldSchedule = course.getSchedule();
            String oldLocation = course.getLocation();
            course.setSchedule(dto.getSchedule());
            course.setLocation(dto.getLocation());
            courseMapper.updateById(course);
            boolean changed = !Objects.equals(oldSchedule, dto.getSchedule())
                    || !Objects.equals(oldLocation, dto.getLocation());
            if (changed) {
                List<Long> studentIds = studentCourseMapper.selectList(
                                new LambdaQueryWrapper<StudentCourse>()
                                        .eq(StudentCourse::getCourseId, id))
                        .stream().map(StudentCourse::getStudentId).collect(Collectors.toList());
                if (!studentIds.isEmpty()) {
                    notificationService.sendSystem("COURSE_CHANGE", "调课通知",
                            "「" + course.getCourseName() + "」课程信息已调整：上课时间 "
                                    + (dto.getSchedule() == null ? "未指定" : dto.getSchedule())
                                    + "，上课地点 " + (dto.getLocation() == null ? "未指定" : dto.getLocation()),
                            studentIds);
                }
            }
            return;
        }
        course.setCourseCode(dto.getCourseCode());
        course.setCourseName(dto.getCourseName());
        course.setCredit(dto.getCredit());
        course.setHours(dto.getHours());
        course.setCoverImageUrl(dto.getCoverImageUrl());
        course.setSchedule(dto.getSchedule());
        course.setLocation(dto.getLocation());
        course.setCapacity(dto.getCapacity());
        if (isAdmin() && dto.getTeacherId() != null) {
            course.setTeacherId(requireValidTeacher(dto.getTeacherId()).getId());
        }
        courseMapper.updateById(course);
    }
```

将 `publishCourse(Long id)` 替换为（保留"已发布不可重复发布"语义）：

```java
    /** 发布课程（锁定） */
    public void publishCourse(Long id) {
        Course course = getEditableCourse(id);
        if ("PUBLISHED".equals(course.getStatus())) {
            throw new BusinessException("课程已发布");
        }
        course.setStatus("PUBLISHED");
        courseMapper.updateById(course);
    }
```

将 `deleteCourse(Long id)` 替换为（保留"已发布不可删除"语义）：

```java
    /** 删除课程（仅未发布；有选课记录也不可删除） */
    @Transactional
    public void deleteCourse(Long id) {
        Course course = getEditableCourse(id);
        if ("PUBLISHED".equals(course.getStatus())) {
            throw new BusinessException("已发布课程不可删除");
        }
        Long enrolled = studentCourseMapper.selectCount(new LambdaQueryWrapper<StudentCourse>()
                .eq(StudentCourse::getCourseId, id));
        if (enrolled > 0) {
            throw new BusinessException("该课程已有学生选课，不可删除");
        }
        courseMapper.deleteById(course.getId());
    }
```

将 `getEditableCourse(Long id)` 替换为（移除"已发布锁定"抛错，由各方法自行校验）：

```java
    /** 获取可编辑课程（权限校验：教师只能操作自己的课程） */
    private Course getEditableCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException("课程不存在");
        if (!isAdmin() && !course.getTeacherId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权限操作他人课程");
        }
        return course;
    }
```

确认 `CourseService.java` 现有 import 含 `java.util.Objects`（若缺失则补 `import java.util.Objects;`）以及 `StudentCourse`、`StudentCourseMapper`、`LambdaQueryWrapper`、`Collectors`、`List`（若缺失则补 import）。

- [ ] **Step 3: EnrollService 选课成功通知**

在 `EnrollService.java` 增加注入（在 `@Autowired private CourseService courseService;` 之后）：

```java
    @Autowired
    private NotificationService notificationService;
```

在 `enroll(Long studentId, Long courseId)` 方法末尾（`courseMapper.updateById(course);` 之后）追加：

```java
        // 选课成功自动通知
        notificationService.sendSystem("ENROLL", "选课成功",
                "「" + course.getCourseName() + "」选课成功，可在“我的课表”中查看。",
                List.of(studentId));
```

在 `adminEnroll(Long studentId, Long courseId)` 方法内，选课成功分支追加相同通知（先查课程名）：

```java
        Course course = courseMapper.selectById(courseId);
        notificationService.sendSystem("ENROLL", "选课成功",
                "「" + course.getCourseName() + "」选课成功（教学秘书代选），可在“我的课表”中查看。",
                List.of(studentId));
```

（实施时按 `adminEnroll` 现有代码结构插入到成功路径末尾，确认 `List` 已 import。）

- [ ] **Step 4: 编译验证**

Run: `cd backend; mvn -q compile`
Expected: exit 0。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/example/sms/service/GradeService.java backend/src/main/java/com/example/sms/service/CourseService.java backend/src/main/java/com/example/sms/service/EnrollService.java
git commit -m "feat: 自动触发通知（成绩发布/调课/选课成功），已发布课程放开调课字段"
```

---

### Task 5: 后端单元测试

**Files:**
- Create: `backend/src/test/java/com/example/sms/service/NotificationServiceTest.java`

- [ ] **Step 1: 编写测试**

创建 `backend/src/test/java/com/example/sms/service/NotificationServiceTest.java`：

```java
package com.example.sms.service;

import com.example.sms.common.BusinessException;
import com.example.sms.dto.SendNotificationDTO;
import com.example.sms.entity.Course;
import com.example.sms.entity.Student;
import com.example.sms.mapper.CourseMapper;
import com.example.sms.mapper.NotificationMapper;
import com.example.sms.mapper.NotificationReceiverMapper;
import com.example.sms.mapper.StudentCourseMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.UserContext;
import com.example.sms.websocket.WsSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationReceiverMapper receiverMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private StudentCourseMapper studentCourseMapper;
    @Mock
    private WsSessionRegistry wsSessionRegistry;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(1L);
        user.setUserNo("T001");
        user.setRealName("张老师");
        user.setRoleType("TEACHER");
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 老师发送非本人课程 → 403 拒绝 */
    @Test
    void teacherSendingOthersCourseRejected() {
        Course course = new Course();
        course.setId(10L);
        course.setTeacherId(99L);
        when(courseMapper.selectById(10L)).thenReturn(course);

        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setTitle("测试");
        dto.setContent("内容");
        SendNotificationDTO.Target target = new SendNotificationDTO.Target();
        target.setKind("COURSE");
        target.setCourseId(10L);
        dto.setTarget(target);

        assertThrows(BusinessException.class, () -> notificationService.send(dto));
    }

    /** 已读他人通知 → 403 拒绝 */
    @Test
    void markReadOthersRejected() {
        com.example.sms.entity.NotificationReceiver r = new com.example.sms.entity.NotificationReceiver();
        r.setId(5L);
        r.setStudentId(999L);
        r.setNotificationId(1L);
        r.setRead(false);
        when(receiverMapper.selectById(5L)).thenReturn(r);

        assertThrows(BusinessException.class, () -> notificationService.markRead(5L));
    }

    /** ALL 接收方式：解析全部启用学生并生成通知+接收明细 */
    @Test
    void sendToAllEnabledStudents() {
        Student s1 = new Student();
        s1.setId(101L);
        Student s2 = new Student();
        s2.setId(102L);
        when(studentMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(notificationMapper.insert(any())).thenReturn(1);
        when(receiverMapper.insert(any())).thenReturn(1);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setTitle("全体通知");
        dto.setContent("内容");
        SendNotificationDTO.Target target = new SendNotificationDTO.Target();
        target.setKind("ALL");
        dto.setTarget(target);

        notificationService.send(dto);
        verify(notificationMapper).insert(any());
        verify(receiverMapper).insert(any());
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `cd backend; mvn -q -Dtest=NotificationServiceTest test`
Expected: `BUILD SUCCESS`，3 个测试全部通过。

- [ ] **Step 3: 提交**

```bash
git add backend/src/test/java/com/example/sms/service/NotificationServiceTest.java
git commit -m "test: 通知服务单测（老师越权/已读属主/ALL 发送）"
```

---

### Task 6: 前端 API 与类型、WebSocket store

**Files:**
- Modify: `frontend/src/types/index.ts`
- Create: `frontend/src/api/notification.ts`
- Create: `frontend/src/stores/notification.ts`
- Modify: `frontend/vite.config.ts`

- [ ] **Step 1: 类型追加**

在 `frontend/src/types/index.ts` 末尾追加：

```ts
/** 站内通知 */
export type NotificationType = 'MANUAL' | 'GRADE_PUBLISH' | 'COURSE_CHANGE' | 'ENROLL'

export interface NotificationItem {
  id: number
  receiverId?: number
  type: NotificationType
  title: string
  content: string
  senderName?: string
  read?: boolean
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
```

- [ ] **Step 2: API 模块**

创建 `frontend/src/api/notification.ts`：

```ts
import http from './http'
import type { NotificationItem, PageResult } from '@/types'

export type TargetKind = 'STUDENT_IDS' | 'CLASS' | 'MAJOR' | 'DEPARTMENT' | 'COURSE' | 'ALL'

export interface NotificationTarget {
  kind: TargetKind
  studentIds?: number[]
  className?: string
  major?: string
  department?: string
  courseId?: number
}

export interface SendNotificationBody {
  title: string
  content: string
  target: NotificationTarget
}

export function inbox(page = 1, size = 10) {
  return http.get('/notifications', { params: { page, size } }) as Promise<PageResult<NotificationItem>>
}

export function unreadCount() {
  return http.get('/notifications/unread-count') as Promise<number>
}

export function markRead(receiverId: number) {
  return http.put(`/notifications/${receiverId}/read`) as Promise<null>
}

export function readAll() {
  return http.put('/notifications/read-all') as Promise<null>
}

export function sent(page = 1, size = 10) {
  return http.get('/notifications/sent', { params: { page, size } }) as Promise<PageResult<NotificationItem>>
}

export function sendNotification(body: SendNotificationBody) {
  return http.post('/notifications/send', body) as Promise<null>
}
```

- [ ] **Step 3: WebSocket store**

创建 `frontend/src/stores/notification.ts`：

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken } from '@/api/http'
import { unreadCount as fetchUnread } from '@/api/notification'
import { useUserStore } from '@/stores/user'

/** 站内通知 store：WebSocket 连接管理 + 未读数 + 心跳 + 断线重连 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  const connected = ref(false)
  const latest = ref<{ title: string; content: string; type: string } | null>(null)

  let ws: WebSocket | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let retry = 0

  const WS_BASE = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/notifications`

  function refreshUnread() {
    fetchUnread()
      .then((n) => {
        unread.value = n
      })
      .catch(() => {})
  }

  function connect() {
    const userStore = useUserStore()
    const token = getToken()
    if (!token || !userStore.isLogin()) return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
    ws = new WebSocket(`${WS_BASE}?token=${encodeURIComponent(token)}`)
    ws.onopen = () => {
      connected.value = true
      retry = 0
      heartbeatTimer = setInterval(() => {
        if (ws?.readyState === WebSocket.OPEN) ws.send('{"type":"PING"}')
      }, 25000)
    }
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data)
        if (msg.type === 'PONG') return
        if (msg.type === 'NOTIFICATION') {
          unread.value += 1
          latest.value = msg.data
        }
      } catch {
        // 忽略非法帧
      }
    }
    ws.onclose = () => {
      connected.value = false
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
        heartbeatTimer = null
      }
      scheduleReconnect()
    }
    ws.onerror = () => {
      try {
        ws?.close()
      } catch {
        // no-op
      }
    }
  }

  function scheduleReconnect() {
    if (document.hidden) return
    const userStore = useUserStore()
    if (!userStore.isLogin()) return
    const delay = Math.min(1000 * 2 ** retry, 30000)
    retry += 1
    reconnectTimer = setTimeout(() => connect(), delay)
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    if (ws) {
      ws.onclose = null
      ws.close()
      ws = null
    }
    connected.value = false
  }

  return { unread, connected, latest, connect, disconnect, refreshUnread }
})
```

- [ ] **Step 4: vite 代理 /ws**

修改 `frontend/vite.config.ts` 的 proxy，追加 `/ws`：

```ts
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true
      }
    }
```

- [ ] **Step 5: 编译验证**

Run: `cd frontend; npm run build`
Expected: 构建成功（vue-tsc 类型检查通过 + vite build 产出 dist）。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/types/index.ts frontend/src/api/notification.ts frontend/src/stores/notification.ts frontend/vite.config.ts
git commit -m "feat: 前端通知 API/类型/WebSocket store 与 /ws 代理"
```

---

### Task 7: 顶栏铃铛组件 + 布局接入

**Files:**
- Create: `frontend/src/components/NotificationBell.vue`
- Modify: `frontend/src/layouts/MainLayout.vue`

- [ ] **Step 1: 铃铛组件**

创建 `frontend/src/components/NotificationBell.vue`：

```vue
<template>
  <el-popover placement="bottom-end" :width="340" trigger="click" @show="load">
    <template #reference>
      <el-badge :value="store.unread" :hidden="store.unread === 0" :max="99" class="bell">
        <el-button link circle>
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </template>
    <div class="notif-panel">
      <div class="notif-head">
        <span>通知</span>
        <el-button link type="primary" size="small" @click="goAll">查看全部</el-button>
      </div>
      <div v-if="items.length === 0" class="notif-empty">暂无通知</div>
      <div
        v-for="n in items"
        :key="n.receiverId"
        class="notif-item"
        :class="{ unread: !n.read }"
        @click="open(n)"
      >
        <div class="notif-title">{{ n.title }}</div>
        <div class="notif-time">{{ (n.createdAt || '').slice(5, 16) }}</div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/stores/notification'
import { inbox, markRead } from '@/api/notification'
import type { NotificationItem } from '@/types'

const router = useRouter()
const store = useNotificationStore()
const items = ref<NotificationItem[]>([])

async function load() {
  try {
    const page = await inbox(1, 5)
    items.value = page.records
  } catch {
    // 已由拦截器提示
  }
}

function goAll() {
  router.push('/notifications')
}

async function open(n: NotificationItem) {
  if (n.receiverId && !n.read) {
    try {
      await markRead(n.receiverId)
      store.unread = Math.max(0, store.unread - 1)
      n.read = true
    } catch {
      // no-op
    }
  }
  router.push('/notifications')
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.bell {
  display: inline-flex;
  vertical-align: middle;
}
.notif-panel {
  max-height: 360px;
  overflow-y: auto;
}
.notif-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.notif-empty {
  text-align: center;
  color: #909399;
  padding: 24px 0;
}
.notif-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f2f3f5;
  cursor: pointer;
}
.notif-item:hover {
  background: #f5f7fa;
}
.notif-item.unread .notif-title {
  font-weight: 600;
}
.notif-title {
  font-size: 14px;
  color: #303133;
}
.notif-time {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
```

- [ ] **Step 2: MainLayout 接入**

修改 `frontend/src/layouts/MainLayout.vue`：

1) template 中 `user-area` 内部、`el-tag` 之前插入：

```html
          <NotificationBell />
```

2) `script setup` 中 import：

```ts
import NotificationBell from '@/components/NotificationBell.vue'
import { useNotificationStore } from '@/stores/notification'
import { onMounted, onUnmounted } from 'vue'
```

3) 在 `handleLogout` 之前新增生命周期逻辑：

```ts
const notificationStore = useNotificationStore()

onMounted(() => {
  notificationStore.refreshUnread()
  notificationStore.connect()
})

onUnmounted(() => {
  notificationStore.disconnect()
})
```

4) 侧栏菜单为三个角色各追加通知中心入口（`menus` computed 中，追加到末尾）：
- ADMIN：`{ path: '/notifications', title: '通知中心', icon: 'Bell' }`
- TEACHER：`{ path: '/notifications', title: '通知中心', icon: 'Bell' }`
- STUDENT：`{ path: '/notifications', title: '通知中心', icon: 'Bell' }`

- [ ] **Step 3: 编译验证**

Run: `cd frontend; npm run build`
Expected: 构建成功。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/NotificationBell.vue frontend/src/layouts/MainLayout.vue
git commit -m "feat: 顶栏通知铃铛（未读角标+下拉预览）与布局接入"
```

---

### Task 8: 通知中心页 + 发送通知对话框 + 路由

**Files:**
- Create: `frontend/src/views/NotificationsView.vue`
- Create: `frontend/src/components/SendNotificationDialog.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 通知中心页**

创建 `frontend/src/views/NotificationsView.vue`：

```vue
<template>
  <div class="notifications-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-head">
          <span>{{ isStaff ? '发送记录' : '我的通知' }}</span>
          <div>
            <el-button v-if="!isStaff" type="primary" link @click="handleReadAll">全部已读</el-button>
            <el-button v-if="isStaff" type="primary" @click="sendVisible = true">发送通知</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="list.length === 0" description="暂无通知" />
      <el-table v-else :data="list" stripe>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column v-if="isStaff" prop="senderName" label="发送人" width="110" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createdAt?.replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" :title="detail?.title || '通知详情'" width="520px">
      <div class="detail-meta">
        <el-tag :type="detail ? typeTag(detail.type) : 'info'" size="small">
          {{ detail ? typeLabel(detail.type) : '' }}
        </el-tag>
        <span class="detail-time">{{ detail?.createdAt?.replace('T', ' ').slice(0, 16) }}</span>
      </div>
      <p class="detail-content">{{ detail?.content }}</p>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <SendNotificationDialog v-model="sendVisible" @sent="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { inbox, readAll, sent } from '@/api/notification'
import type { NotificationItem } from '@/types'
import SendNotificationDialog from '@/components/SendNotificationDialog.vue'

const userStore = useUserStore()
const isStaff = computed(() => userStore.role() === 'TEACHER' || userStore.role() === 'ADMIN')

const list = ref<NotificationItem[]>([])
const page = ref(1)
const size = 10
const total = ref(0)
const detailVisible = ref(false)
const detail = ref<NotificationItem | null>(null)
const sendVisible = ref(false)

async function load() {
  try {
    const p = isStaff.value ? await sent(page.value, size) : await inbox(page.value, size)
    list.value = p.records
    total.value = p.total
  } catch {
    // 已由拦截器提示
  }
}

function openDetail(row: NotificationItem) {
  detail.value = row
  detailVisible.value = true
}

async function handleReadAll() {
  try {
    await readAll()
    ElMessage.success('已全部标为已读')
    load()
  } catch {
    // no-op
  }
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    MANUAL: '通知',
    GRADE_PUBLISH: '成绩',
    COURSE_CHANGE: '调课',
    ENROLL: '选课'
  }
  return map[type] || '通知'
}

function typeTag(type: string) {
  const map: Record<string, string> = {
    MANUAL: 'info',
    GRADE_PUBLISH: 'success',
    COURSE_CHANGE: 'warning',
    ENROLL: 'primary'
  }
  return map[type] || 'info'
}

onMounted(load)
</script>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.detail-time {
  font-size: 12px;
  color: #909399;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
}
</style>
```

- [ ] **Step 2: 发送通知对话框**

创建 `frontend/src/components/SendNotificationDialog.vue`：

```vue
<template>
  <el-dialog :model-value="modelValue" title="发送通知" width="600px" @update:model-value="$emit('update:modelValue', $event)" @closed="reset">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：调课通知" />
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit />
      </el-form-item>
      <el-form-item label="接收对象" prop="kind">
        <el-radio-group v-model="form.kind">
          <el-radio-button label="COURSE">按课程</el-radio-button>
          <el-radio-button v-if="isAdmin" label="CLASS">按班级</el-radio-button>
          <el-radio-button v-if="isAdmin" label="MAJOR">按专业</el-radio-button>
          <el-radio-button v-if="isAdmin" label="DEPARTMENT">按院系</el-radio-button>
          <el-radio-button v-if="isAdmin" label="ALL">全部学生</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.kind === 'COURSE'" label="选择课程" prop="courseId">
        <el-select v-model="form.courseId" filterable placeholder="请选择课程" style="width: 100%">
          <el-option v-for="c in courses" :key="c.id" :label="`${c.courseCode} ${c.courseName}`" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-else-if="form.kind === 'CLASS'" label="班级" prop="className">
        <el-input v-model="form.className" placeholder="如：软件2101" />
      </el-form-item>
      <el-form-item v-else-if="form.kind === 'MAJOR'" label="专业" prop="major">
        <el-input v-model="form.major" placeholder="如：软件工程" />
      </el-form-item>
      <el-form-item v-else-if="form.kind === 'DEPARTMENT'" label="院系" prop="department">
        <el-input v-model="form.department" placeholder="如：计算机学院" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">发送</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { sendNotification, type SendNotificationBody } from '@/api/notification'
import { listMyCourses } from '@/api/course'
import { useUserStore } from '@/stores/user'
import type { Course } from '@/types'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'sent'): void }>()

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role() === 'ADMIN')

const formRef = ref<FormInstance>()
const loading = ref(false)
const courses = ref<Course[]>([])

const form = reactive<{
  title: string
  content: string
  kind: string
  courseId?: number
  className: string
  major: string
  department: string
}>({
  title: '',
  content: '',
  kind: 'COURSE',
  courseId: undefined,
  className: '',
  major: '',
  department: ''
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

async function submit() {
  await formRef.value?.validate()
  const body: SendNotificationBody = {
    title: form.title,
    content: form.content,
    target: {
      kind: form.kind as SendNotificationBody['target']['kind'],
      courseId: form.kind === 'COURSE' ? form.courseId : undefined,
      className: form.kind === 'CLASS' ? form.className : undefined,
      major: form.kind === 'MAJOR' ? form.major : undefined,
      department: form.kind === 'DEPARTMENT' ? form.department : undefined
    }
  }
  loading.value = true
  try {
    await sendNotification(body)
    ElMessage.success('发送成功')
    emit('update:modelValue', false)
    emit('sent')
  } catch {
    // 已由拦截器提示
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(form, {
    title: '',
    content: '',
    kind: 'COURSE',
    courseId: undefined,
    className: '',
    major: '',
    department: ''
  })
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open && courses.value.length === 0) {
      // 打开时加载课程列表（老师=自己的课程；管理员=全部）
      listMyCourses()
        .then((cs) => {
          courses.value = cs
        })
        .catch(() => {})
    }
  }
)
</script>
```

- [ ] **Step 3: 路由追加**

在 `frontend/src/router/index.ts` 的 MainLayout children 末尾（`admin/ai-assistant` 之后）追加：

```ts
      // 站内通知（三角色）
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/NotificationsView.vue'), meta: { roles: ['STUDENT', 'TEACHER', 'ADMIN'], title: '通知中心' } }
```

- [ ] **Step 4: 编译验证**

Run: `cd frontend; npm run build`
Expected: 构建成功。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/NotificationsView.vue frontend/src/components/SendNotificationDialog.vue frontend/src/router/index.ts
git commit -m "feat: 通知中心页、发送通知对话框与路由"
```

---

### Task 9: 课程管理页调课放开

**Files:**
- Modify: `frontend/src/views/admin/CourseManage.vue`

- [ ] **Step 1: 编辑按钮放开**

将操作列"编辑"按钮从：

```html
<el-button link type="primary" :disabled="row.status === 'PUBLISHED'" @click="openEdit(row)">
```

改为：

```html
<el-button link type="primary" @click="openEdit(row)">
```

- [ ] **Step 2: 已发布课程仅编辑时间/地点**

在 `script setup` 中新增状态（紧跟 `const dialogVisible = ref(false)` 之后）：

```ts
const editingStatus = ref<string>('')
const isPublishedEdit = computed(() => editingStatus.value === 'PUBLISHED')
```

（确认 `computed` 已从 vue 引入。）

在 `openEdit(row)` 中记录状态（在 `dialogVisible.value = true` 之前）：

```ts
  editingStatus.value = row.status
```

在 `openAdd()` 中重置：

```ts
  editingStatus.value = ''
```

对话框内"课程编号/课程名称/授课教师/学分/学时/容量"六个 `el-form-item` 的输入控件加 `:disabled="isPublishedEdit"`，并给"编辑课程"标题改为动态：

```html
<el-dialog v-model="dialogVisible" :title="form.id ? (isPublishedEdit ? '调课' : '编辑课程') : '新增课程'" width="560px" @closed="resetForm">
```

具体地，六个控件分别改为：

```html
<el-input v-model="form.courseCode" :disabled="isPublishedEdit" placeholder="如 CS101" />
<el-input v-model="form.courseName" :disabled="isPublishedEdit" placeholder="如 Java 程序设计" />
<el-select v-model="form.teacherId" :disabled="isPublishedEdit" filterable placeholder="请选择授课教师" style="width: 100%">
<el-input-number v-model="form.credit" :disabled="isPublishedEdit" :min="0.5" :max="20" :step="0.5" :precision="1" />
<el-input-number v-model="form.hours" :disabled="isPublishedEdit" :min="1" :max="200" />
<el-input-number v-model="form.capacity" :disabled="isPublishedEdit" :min="1" :max="1000" />
```

（上课时间/地点两个表单项保持可编辑。）

- [ ] **Step 3: 编译验证**

Run: `cd frontend; npm run build`
Expected: 构建成功。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/views/admin/CourseManage.vue
git commit -m "feat: 已发布课程支持调课（仅编辑时间/地点）"
```

---

### Task 10: 集成验证

**前置：** MySQL、Redis 已启动；后端环境变量与 .env 一致（JWT_SECRET 等）。

- [ ] **Step 1: 执行建表 SQL**

Run: `mysql -u root -p student_management < sql/notifications.sql`（按实际 MySQL 凭据）
Expected: 无报错；`SHOW TABLES` 出现 `notification`、`notification_receiver`。

- [ ] **Step 2: 启动后端**

Run: `cd backend; mvn -q spring-boot:run`（或复用既有启动方式，注入完整环境变量）
Expected: 日志无异常，端口 8080 监听。

- [ ] **Step 3: WebSocket 端点验证**

Run（PowerShell）：
```powershell
curl.exe -s -o NUL -w "%{http_code}" "http://localhost:8080/ws/notifications?token=INVALID"
```
Expected: 401（握手拒绝）。

- [ ] **Step 4: 手动发送全链路**

1. 学生 A（如 S2021001）登录拿到 token
2. 管理员登录，调 `POST /api/notifications/send`（target.kind=ALL 或 CLASS）
3. 学生 A 在已打开的通知中心页面实时看到新通知、铃铛角标 +1（浏览器手测）
4. `GET /api/notifications/unread-count` 返回新未读数
5. `PUT /api/notifications/{receiverId}/read` 后角标 -1

- [ ] **Step 5: 自动触发验证**

1. 选课成功：学生选一门已发布课程 → 收到"选课成功"通知（`GET /api/notifications` 可见，type=ENROLL）
2. 调课：管理员在课程管理页编辑已发布课程的"上课时间/地点" → 选课学生收到"调课通知"（type=COURSE_CHANGE）
3. 成绩发布：管理员对某已审核课程 `POST /api/grades/{courseId}/publish` → 选课学生收到"成绩已发布"（type=GRADE_PUBLISH）

- [ ] **Step 6: 离线补齐验证**

学生 B 不登录期间管理员发送通知 → 学生 B 登录后打开通知中心/拉取 `unread-count` → 显示离线期间的通知。

- [ ] **Step 7: 提交（如有遗漏修正）**

如有集成期修正，`git add -u` 后按 conventional commit 提交。

---

## Self-Review 记录

**Spec 覆盖：**
- 手动发送（Task 3 + Task 8）、四种接收方式（Task 3 resolveTargets）、老师权限边界（Task 3 + Task 5 测试）
- 自动触发：成绩发布（Task 4 Step 1）、调课（Task 4 Step 2 + Task 9 前端）、选课成功（Task 4 Step 3）
- 实时推送（Task 2 + Task 6 store）、离线补齐（收件箱分页 + unread-count，Task 3/6）、已读/全部已读（Task 3/6/7/8）
- 铃铛 + 角标 + 通知中心 + 发送界面（Task 7/8）、角色规则（Task 3 Step 5）
- 心跳与重连（Task 6 Step 3）、错误处理（GlobalExceptionHandler 复用，Task 3 未新增处理器）
- 测试（Task 5 单测 + Task 10 集成）

**无占位符：** 全部步骤含具体代码与命令。

**类型一致性：** `SendNotificationDTO.Target.kind` 与前端 `TargetKind` 枚举值一致；`NotificationVO` 字段与前端 `NotificationItem` 一一对应；`PageResult` 与 MyBatis-Plus `Page` 序列化字段（records/total/size/current/pages）一致。
