# 站内通知系统（WebSocket）设计

日期：2026-09-16
分支：feature/websocket-notifications

## 1. 背景与目标

管理员与老师需要向学生发送站内通知（调课通知、成绩通知、选课确认等），学生登录后即可实时接收并查看。要求：

- 发送方：ADMIN、TEACHER
- 接收方：STUDENT（含离线消息，上线后补齐显示）
- 实时性：WebSocket 推送，学生在线即时收到
- 展示：顶部铃铛 + 未读角标 + 通知中心列表/详情，支持标记已读/全部已读
- 自动触发：成绩发布、课程信息变更、选课成功三个业务节点自动生成通知

"邮件"按站内信实现（WebSocket 推送 + 数据库持久化），非真实 SMTP 邮件。

## 2. 需求摘要

| 维度 | 内容 |
|---|---|
| 手动发送 | ADMIN/TEACHER 编辑标题 + 正文 + 选择接收人后发送 |
| 接收人选择 | 精确选人（学生ID列表）、按班级/专业/院系批量、按课程（该课程选课学生）、全部学生 |
| 自动触发 | 成绩发布后（GRADE_PUBLISH）、课程信息变更后（COURSE_CHANGE）、选课成功后（ENROLL） |
| 已读状态 | 未读/已读 + 未读角标 + 全部已读 |
| 持久化 | 通知与接收明细落库，离线消息永久保存，上线拉取补齐 |
| 实时推送 | 原生 WebSocket，方案 A |

## 3. 架构总览

组件图（简化）：

```
前端                                                    后端
┌──────────────────┐      WS /ws/notifications?token=    ┌──────────────────────────┐
│ NotificationBell │◄──────────── 推送 JSON ──────────── │ NotificationWsHandler     │
│ stores/notification │                                │ WsSessionRegistry (内存)   │
│ NotificationsView  │─── REST ────────────────────────►│ NotificationController     │
│ SendNotificationDialog │                             │ NotificationService        │
└──────────────────┘                                   └──────────┬───────────────┘
                                                                  │ 写库/读库
                                                     ┌────────────▼──────────────┐
                                                     │ notification               │
                                                     │ notification_receiver      │
                                                     └───────────────────────────┘
```

数据流：

1. 学生登录 → 前端建立 `ws://<host>/ws/notifications?token=<jwt>`，握手拦截器校验 JWT 并注册会话
2. 同时 REST 拉取 `unread-count` 与分页历史（离线消息在此补齐）
3. 发送方 `POST /api/notifications/send` → 服务端解析接收人 → 写库（notification + receiver）→ 对在线学生 `sendToUser` 推送
4. 前端收到推送 → 未读角标 +1、列表顶部插入新消息
5. 学生点击已读 → `PUT /api/notifications/{id}/read` → 角标更新

关键边界：

- 通知为单向推送（服务器 → 学生），无客户端上行业务消息
- 单实例部署，会话表存内存，不做跨实例广播（Redis pub/sub 属 YAGNI）
- `/ws/**` 不经过 JwtInterceptor，由握手拦截器自行认证；`/api/notifications/**` 走现有角色规则

## 4. 数据模型

新增两张表（SQL 追加到 sql/init.sql 风格保持一致）：

```sql
CREATE TABLE IF NOT EXISTS notification (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  type        VARCHAR(20)  NOT NULL COMMENT 'MANUAL|GRADE_PUBLISH|COURSE_CHANGE|ENROLL',
  title       VARCHAR(100) NOT NULL,
  content     VARCHAR(2000) NOT NULL,
  sender_type VARCHAR(10)  NOT NULL COMMENT 'ADMIN|TEACHER|SYSTEM',
  sender_id   BIGINT       NULL,
  sender_name VARCHAR(50)  NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_notification_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notification_receiver (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  notification_id BIGINT  NOT NULL,
  student_id      BIGINT  NOT NULL,
  is_read         TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_at         DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_receiver_student (student_id, is_read),
  INDEX idx_receiver_notification (notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

对应 MyBatis-Plus 实体：`Notification`、`NotificationReceiver`；Mapper：`NotificationMapper`、`NotificationReceiverMapper`。

## 5. 后端组件设计

| 组件 | 职责与要点 |
|---|---|
| `WebSocketConfig` | `@EnableWebSocket`；注册端点 `/ws/notifications`，绑定 `WsAuthHandshakeInterceptor` 与 `NotificationWebSocketHandler` |
| `WsAuthHandshakeInterceptor` | 从 query 参数 `token` 解析 JWT（复用 JwtUtil）；成功则写入 session attributes（userId）；失败返回 false 拒绝握手 |
| `NotificationWebSocketHandler` | `afterConnectionEstablished` 将 session 加入 `WsSessionRegistry`；`afterConnectionClosed` / `handleTransportError` 移除；`handleTextMessage` 响应 `{"type":"PING"}` → `{"type":"PONG"}` |
| `WsSessionRegistry` | `ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>`；`sendToUser(userId, json)` 遍历推送，session 失效（!isOpen）时移除 |
| `NotificationService` | 发送（解析接收人 → 写库 → 推在线）、分页收件箱、未读数、标记已读（含属主校验）、发件箱、自动触发（供 Grade/Course/Enroll 服务调用） |
| `NotificationController` | 暴露 REST 接口，见第 6 节 |

发送流程（`NotificationService.send`）：

1. 校验：标题、正文非空；接收方式与参数匹配
2. 解析接收人：`target.kind` 分派
   - `STUDENT_IDS`：直接使用 studentIds（去重）
   - `CLASS` / `MAJOR` / `DEPARTMENT`：StudentMapper 条件查询
   - `COURSE`：StudentCourseMapper 查该课程选课学生；校验课程存在且（老师）属于本人
   - `ALL`：查询全部 ENABLED 学生
3. 接收人为空 → 抛 BusinessException("未匹配到任何学生")
4. 插入 notification（sender 信息取自 UserContext / 系统标记）
5. 批量插入 notification_receiver（循环 insert，项目规模小可接受）
6. 事务提交后，对在线学生逐人 `sendToUser` 推送

自动触发（三个挂钩，均在服务层事务内调用 `NotificationService.sendSystem(...)`）：

| 场景 | 触发点 | 接收人 | 内容 |
|---|---|---|---|
| 成绩发布 | GradeService 发布方法 | 该课程选课学生 | 「{课程名}」成绩已发布，可登录查看 |
| 课程信息变更 | CourseService 更新方法，对比 schedule/location/teacherId 新旧值，有变化才触发 | 该课程选课学生 | 变更明细（时间/地点/教师） |
| 选课成功 | EnrollService 选课方法 | 该学生 | 「{课程名}」选课成功 |

`sendSystem` 与 `send` 共用底层实现，仅 sender 标记为 SYSTEM。

## 6. REST 接口与角色规则

接口（均为 `/api/notifications` 前缀）：

```
GET  /api/notifications                收件箱分页（page/size，倒序）
GET  /api/notifications/unread-count   未读数
PUT  /api/notifications/{id}/read      标记已读
PUT  /api/notifications/read-all       全部已读
GET  /api/notifications/sent           发件箱分页（发送历史）
POST /api/notifications/send           发送通知
```

JwtInterceptor ROLE_RULES 追加（精确规则在前，fail-closed）：

```
{"/api/notifications/unread-count", "STUDENT"},
{"/api/notifications/read-all",     "STUDENT"},
{"/api/notifications/sent",         "TEACHER,ADMIN"},
{"/api/notifications/*/read",       "STUDENT"},
{"/api/notifications/send",         "TEACHER,ADMIN"},
{"/api/notifications/**",           "STUDENT"},
```

权限边界：

- 发送：老师仅可「按课程」发送且 `course.teacherId == 当前用户`；管理员四种方式均可
- 已读属主：`{id}/read` 必须 `receiver.student_id == 当前 userId`，否则 403
- 收件箱仅学生可查；发件箱仅老师/管理员可查

发送请求 DTO（`SendNotificationDTO`）：

```
type: 固定 MANUAL（手动发送）
title: string
content: string
target: {
  kind: 'STUDENT_IDS' | 'CLASS' | 'MAJOR' | 'DEPARTMENT' | 'COURSE' | 'ALL',
  studentIds?: number[],
  className?: string,
  major?: string,
  department?: string,
  courseId?: number
}
```

## 7. WebSocket 协议

- 握手：`GET /ws/notifications?token=<jwt>`（浏览器原生 WebSocket，无 SockJS）
- 服务端 → 客户端：`{"type":"NOTIFICATION","data":{"receiverId":..,"notificationId":..,"title":..,"content":..,"type":..,"createdAt":".."}}`
- 客户端 → 服务端：`{"type":"PING"}`；服务端回 `{"type":"PONG"}`（心跳保活，防止 Tomcat 空闲超时）

## 8. 前端组件

| 组件 | 要点 |
|---|---|
| `stores/notification.ts` | Pinia：`unreadCount`、`connected`、最新消息缓存；`connect()`（登录后调用）/`disconnect()`；收到 `NOTIFICATION` → unreadCount+1、消息入队、通知订阅方刷新 |
| `components/NotificationBell.vue` | 顶栏铃铛：el-badge 未读角标 + 下拉预览最新 5 条 + 「查看全部」跳转通知中心；点击单条 → 标记已读并跳转 |
| `views/NotificationsView.vue` | 通知中心：学生 = 收件箱分页列表（类型标签：调课/成绩/选课/通知 + 时间 + 已读态）+ 详情弹窗 + 「全部已读」；老师/管理员 = 发件箱 + 「发送通知」入口 |
| `components/SendNotificationDialog.vue` | 发送表单：标题、正文、接收对象选择器（管理员 4 种方式 + 课程/全部；老师仅「按课程-自己的课程」下拉）；提交 POST send |

路由：`/notifications`（三角色可访问，挂载在 MainLayout 下）；MainLayout 顶栏加入 `NotificationBell`。

## 9. 连接可靠性

- 心跳：前端每 25s 发 PING，服务端回 PONG
- 重连：断线指数退避 1s→2s→4s→…→上限 30s；页面隐藏暂停重连、可见立即重连；收到 401/握手被拒停止并跳登录
- 服务端 session 失效静默清理，不阻塞业务

## 10. 错误处理

| 场景 | 处理 |
|---|---|
| 标题/正文为空、接收方式与参数不匹配、接收人为空 | BusinessException → 400（GlobalExceptionHandler） |
| 老师发送非本人课程 | BusinessException → 400/403 |
| 已读他人通知 | 403 |
| WebSocket 握手 token 无效 | 拒绝连接（close code 4401） |
| 推送时 session 已失效 | 静默移除（消息已落库） |
| 数据库异常 | 事务回滚 + GlobalExceptionHandler 统一响应 |

## 11. 测试计划

后端单测（JUnit，沿用 AccountServiceTest 风格）：

- 接收人解析：CLASS / COURSE / ALL 正确解析且去重；空结果抛异常
- 已读属主校验：他人 receiver 返回 403/拒绝
- 老师越权：发送非本人课程被拒
- 成绩发布/课程变更自动触发生成通知（mock 依赖）

集成验证（真实环境）：

- 发送通知 → 在线学生 WebSocket 实时收到（浏览器/脚本验证）
- 离线学生登录后拉取补齐，未读角标正确
- 已读 → 角标减一；全部已读归零
- 自动触发：发布成绩/改课程信息/选课后自动收到通知

前端：vite 编译通过；手测「发 → 收 → 已读 → 角标」关键链路。

## 12. 不做的事（YAGNI）

- 真实 SMTP 邮件发送
- STOMP/SockJS、消息代理、Redis pub/sub 跨实例广播
- 通知回收/过期清理任务（数据量小）
- 浏览器系统级通知（Notification API）
