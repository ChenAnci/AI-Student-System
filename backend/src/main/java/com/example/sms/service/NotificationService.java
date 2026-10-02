package com.example.sms.service; // 声明当前类所在的包：service 服务层

// ===== import 区域：引入 MyBatis-Plus 查询构造器/更新构造器/分页插件、业务异常、DTO、实体、Mapper、用户上下文、VO、WebSocket 会话注册表、Jackson、Spring 事务同步与 Java 工具 =====
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 查询条件构造器
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper; // MyBatis-Plus 的 Lambda 更新条件构造器：批量置已读时使用
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // MyBatis-Plus 分页对象：封装页码/每页条数/总数
import com.example.sms.common.BusinessException; // 自定义业务异常类
import com.example.sms.dto.SendNotificationDTO; // 发送通知请求 DTO
import com.example.sms.entity.Course; // 课程实体类
import com.example.sms.entity.Notification; // 通知主表实体类
import com.example.sms.entity.NotificationReceiver; // 通知接收明细实体类（一人一条）
import com.example.sms.entity.Student; // 学生实体类
import com.example.sms.entity.StudentCourse; // 学生选课关系实体类
import com.example.sms.mapper.CourseMapper; // 课程表 Mapper 接口
import com.example.sms.mapper.NotificationMapper; // 通知主表 Mapper 接口
import com.example.sms.mapper.NotificationReceiverMapper; // 通知接收明细表 Mapper 接口
import com.example.sms.mapper.StudentCourseMapper; // 学生选课表 Mapper 接口
import com.example.sms.mapper.StudentMapper; // 学生表 Mapper 接口
import com.example.sms.util.UserContext; // 用户上下文工具：读取当前登录用户信息
import com.example.sms.vo.NotificationVO; // 通知视图对象
import com.example.sms.websocket.WsSessionRegistry; // WebSocket 会话注册表：向在线用户推送消息
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson JSON 序列化工具
import lombok.extern.slf4j.Slf4j; // Lombok 注解：自动生成 log 日志对象
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解
import org.springframework.stereotype.Service; // Spring 服务层注解
import org.springframework.transaction.annotation.Transactional; // Spring 声明式事务注解
import org.springframework.transaction.support.TransactionSynchronization; // Spring 事务同步回调接口（事务提交后执行）
import org.springframework.transaction.support.TransactionSynchronizationManager; // Spring 事务同步管理器：注册事务回调
import java.time.LocalDateTime; // 本地日期时间：记录已读时间
import java.util.List; // 列表接口
import java.util.Map; // 键值映射接口
import java.util.Objects; // 对象工具类：null 安全的 equals 比较
import java.util.function.Function; // 函数式接口
import java.util.stream.Collectors; // Stream 收集器

/**
 * 站内通知服务：发送（手动/系统）、收件箱、未读数、已读、发件箱
 */
@Slf4j // Lombok 注解：为类生成 log 日志对象
@Service // 声明为 Spring 服务组件，交由容器管理
public class NotificationService { // 站内通知服务类：通知的发送、解析接收人、收件箱/发件箱、已读管理与 WebSocket 推送

    @Autowired // Spring 自动注入 NotificationMapper
    private NotificationMapper notificationMapper; // 通知主表 Mapper

    @Autowired // Spring 自动注入 NotificationReceiverMapper
    private NotificationReceiverMapper receiverMapper; // 通知接收明细表 Mapper（一人一条接收记录）

    @Autowired // Spring 自动注入 StudentMapper
    private StudentMapper studentMapper; // 学生表 Mapper：按班级/专业/院系解析接收人

    @Autowired // Spring 自动注入 CourseMapper
    private CourseMapper courseMapper; // 课程表 Mapper：按课程解析接收人时查询课程

    @Autowired // Spring 自动注入 StudentCourseMapper
    private StudentCourseMapper studentCourseMapper; // 学生选课表 Mapper：查询选课学生作为接收人

    @Autowired // Spring 自动注入 WsSessionRegistry
    private WsSessionRegistry wsSessionRegistry; // WebSocket 会话注册表：向在线学生实时推送通知

    @Autowired // Spring 自动注入 ObjectMapper
    private ObjectMapper objectMapper; // Jackson JSON 序列化器：把推送消息转成 JSON 字符串

    // ===== 发送 =====

    /**
     * 手动发送（发送者取自 UserContext，老师仅可发给自己的课程学生）。
     * 调用逻辑：NotificationController.send → notificationService.send：教师/教秘在发通知页选择接收范围提交，内部 resolveTargets 解析接收人 → doSend 先落库 notification 主表 + receivers 接收明细（批量插入）→ 事务提交后（afterCommit）经 WebSocket 推送给在线学生。
     * 为什么：先落库后推送——消息可靠性以落库为准，WebSocket 推送失败只记日志不影响发送结果，离线学生刷新收件箱即可补拉；教师仅限按自己的课程发送，防止绕过前端越权群发。
     */
    @Transactional // 声明式事务：通知主表与接收明细在同一事务中落库
    public void send(SendNotificationDTO dto) { // 手动发送通知（教师/教秘）
        UserContext.CurrentUser user = UserContext.get(); // 获取当前登录用户信息（发送者）
        // 权限边界：教师只能按课程发送且必须为自己的课程（防止绕过前端隐藏选项，直接调用接口用 STUDENT_IDS/ALL 等发给任意学生）
        if ("TEACHER".equals(user.getRoleType()) // 发送者是教师
                && !"COURSE".equals(dto.getTarget().getKind())) { // 且接收方式不是"按课程"
            throw new BusinessException(403, "教师只能向自己授课课程的学生发送通知"); // 拒绝越权发送
        }
        List<Long> studentIds = resolveTargets(dto.getTarget()); // 根据接收方式解析出接收学生 ID 列表
        if (studentIds.isEmpty()) { // 未匹配到任何学生
            throw new BusinessException("未匹配到任何学生"); // 提示无接收人
        }
        doSend("MANUAL", dto.getTitle().trim(), dto.getContent().trim(), // 执行发送：类型 MANUAL，标题/内容去空白
                user.getRoleType(), user.getUserId(), user.getRealName(), studentIds); // 附带发送者角色/ID/姓名与接收人列表
    }

    /** 系统自动触发（成绩发布/调课/选课成功）；接收人为空时静默跳过 */
    @Transactional // 声明式事务：通知落库原子提交
    public void sendSystem(String type, String title, String content, List<Long> studentIds) { // 系统自动通知（成绩发布/调课/选课成功等）
        if (studentIds == null || studentIds.isEmpty()) return; // 接收人为空则静默跳过（不产生无效通知）
        doSend(type, title, content, "SYSTEM", null, "系统", studentIds); // 执行发送：发送者为系统（SYSTEM），ID 为空，显示名"系统"
    }

    private void doSend(String type, String title, String content, String senderType, // 发送核心方法：类型、标题、内容、发送者类型
                        Long senderId, String senderName, List<Long> studentIds) { // 发送者 ID、发送者姓名、接收学生 ID 列表
        // 1) 先写通知主表：插入后拿到自增 id，作为接收明细的外键
        Notification n = new Notification(); // 创建通知主表实体
        n.setType(type); // 设置通知类型（MANUAL/SYSTEM/ENROLL 等）
        n.setTitle(title); // 设置标题
        n.setContent(content); // 设置内容
        n.setSenderType(senderType); // 设置发送者类型（手动/系统）
        n.setSenderId(senderId); // 设置发送者 ID（系统通知为 null）
        n.setSenderName(senderName); // 设置发送者显示名
        notificationMapper.insert(n); // 插入通知主表，获得自增通知 id

        // 2) 写接收明细（一人一条）：先 distinct 去重，避免同一学生命中多种接收方式（如既在班级又在课程）产生重复通知；
        //    批量 INSERT（F-3）：一次拼接多行 VALUES 替代逐条 insert，群发数百人时避免 N+1 写放大
        List<Long> distinctIds = studentIds.stream().distinct().collect(Collectors.toList()); // 接收学生 ID 去重（防止一人多条重复通知）
        List<NotificationReceiver> receivers = distinctIds.stream().map(sid -> { // 为每个学生生成一条接收明细
            NotificationReceiver r = new NotificationReceiver(); // 创建接收明细实体
            r.setNotificationId(n.getId()); // 关联通知主表 id
            r.setStudentId(sid); // 设置接收学生 id
            r.setIsRead(false); // 初始未读
            return r; // 返回明细实体
        }).collect(Collectors.toList()); // 收集为明细列表
        receiverMapper.batchInsert(receivers); // 批量插入接收明细（一条 SQL 多行 VALUES）

        // 事务提交后推送（保证落库可见后再通知前端）
        // 为什么必须提交后再推：若在事务内推送，接收方此刻查询收件箱尚看不到记录，会产生"收到推送但列表为空"的窗口期；
        // 且 WebSocket 推送属于网络 IO，失败不应回滚已落库的通知——消息可靠性以落库为准，推送只是"实时加速"手段
        Runnable push = () -> pushToOnline(n, distinctIds); // 封装推送动作：向在线学生推送这条通知
        if (TransactionSynchronizationManager.isSynchronizationActive()) { // 当前存在活动的事务
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { // 注册事务同步回调
                @Override
                public void afterCommit() { // 事务提交成功后回调
                    push.run(); // 执行推送（此时数据已落库可见）
                }
            });
        } else { // 无事务环境（如测试或非事务调用）
            push.run(); // 直接执行推送
        }
    }

    private void pushToOnline(Notification n, List<Long> studentIds) { // 通过 WebSocket 向在线学生推送通知
        try { // 捕获推送过程的全部异常，保证推送失败不影响主流程
            // 推送前重新查接收明细，拿到每条接收记录的 id（receiverId）下发给前端——前端"标记已读"需要该 id 定位记录
            Map<Long, Long> sid2Rid = receiverMapper.selectList(new LambdaQueryWrapper<NotificationReceiver>() // 查询该通知的全部接收明细
                            .eq(NotificationReceiver::getNotificationId, n.getId())) // 等值条件：notification_id = 通知 id
                    .stream().collect(Collectors.toMap(NotificationReceiver::getStudentId, NotificationReceiver::getId)); // 建立"学生 id -> 接收明细 id"映射
            for (Long sid : studentIds) { // 逐个接收学生推送
                Long receiverId = sid2Rid.get(sid); // 取该学生的接收明细 id
                if (receiverId == null) continue; // 明细缺失则跳过（防御性）
                String json = objectMapper.writeValueAsString(Map.of( // 把推送内容序列化为 JSON
                        "type", "NOTIFICATION", // 消息类型：通知
                        "data", Map.of( // 通知数据
                                "receiverId", receiverId, // 接收明细 id（前端标记已读用）
                                "notificationId", n.getId(), // 通知主表 id
                                "title", n.getTitle(), // 标题
                                "content", n.getContent(), // 内容
                                "type", n.getType(), // 通知类型
                                "createdAt", n.getCreatedAt() == null ? LocalDateTime.now().toString() : n.getCreatedAt().toString()))); // 创建时间（null 时取当前时间）
                wsSessionRegistry.sendToUser(sid, json); // 通过 WebSocket 会话注册表向该学生推送
            }
        } catch (Exception e) { // 推送过程出现任何异常
            // 推送是"尽力而为"（best-effort）：任何异常只记日志不向上抛，避免把推送失败扩散成发送接口报错；
            // 消息已落库，学生下次刷新/进入收件箱即可拉到，实时性丢失可接受
            log.warn("通知推送失败（消息已落库）：{}", e.getMessage()); // 记录告警日志
        }
    }

    // ===== 接收人解析 =====

    private List<Long> resolveTargets(SendNotificationDTO.Target target) { // 根据接收方式解析接收学生 ID 列表
        String kind = target.getKind(); // 取接收方式类型
        switch (kind) { // 按类型分支处理
            case "STUDENT_IDS": // 按学生 id 白名单
                // 按学生 id 白名单直接发送（仅管理员可用，教师入口已被 send() 拦截）
                if (target.getStudentIds() == null || target.getStudentIds().isEmpty()) { // 未选择学生
                    throw new BusinessException("请选择接收学生"); // 提示必选
                }
                return target.getStudentIds(); // 直接返回白名单
            case "CLASS": // 按班级
                // 按班级名精确匹配：查出该班全部学生再取 id
                return studentMapper.selectList(new LambdaQueryWrapper<Student>() // 查询该班级学生
                                .eq(Student::getClassName, target.getClassName())) // 等值条件：class_name = 班级名
                        .stream().map(Student::getId).collect(Collectors.toList()); // 取出学生 ID 列表
            case "MAJOR": // 按专业
                // 按专业匹配：一个专业下可能跨多个班/多个年级
                return studentMapper.selectList(new LambdaQueryWrapper<Student>() // 查询该专业学生
                                .eq(Student::getMajor, target.getMajor())) // 等值条件：major = 专业名
                        .stream().map(Student::getId).collect(Collectors.toList()); // 取出学生 ID 列表
            case "DEPARTMENT": // 按院系
                // 按院系匹配：粒度最粗，适合院级通知
                return studentMapper.selectList(new LambdaQueryWrapper<Student>() // 查询该院系学生
                                .eq(Student::getDepartment, target.getDepartment())) // 等值条件：department = 院系名
                        .stream().map(Student::getId).collect(Collectors.toList()); // 取出学生 ID 列表
            case "COURSE": { // 按课程（块作用域：内部声明局部变量）
                // 按课程发送：通过选课关系表 student_course 找到选修该课的学生
                if (target.getCourseId() == null) throw new BusinessException("请选择课程"); // 未选择课程则提示
                Course course = courseMapper.selectById(target.getCourseId()); // 查询课程
                if (course == null) throw new BusinessException("课程不存在"); // 课程不存在则抛异常
                // 教师越权校验：即使前端/调用方伪造 courseId，也要确认课程归属自己（fail-closed，防止给他人课程学生群发）
                if ("TEACHER".equals(UserContext.getRole()) // 当前用户是教师
                        && !Objects.equals(course.getTeacherId(), UserContext.getUserId())) { // 且课程不属于当前教师
                    throw new BusinessException(403, "只能向自己授课课程的学生发送通知"); // 拒绝越权发送
                }
                return studentCourseMapper.selectList(new LambdaQueryWrapper<StudentCourse>() // 查询选修该课程的学生
                                .eq(StudentCourse::getCourseId, target.getCourseId())) // 等值条件：course_id = 课程 id
                        .stream().map(StudentCourse::getStudentId).collect(Collectors.toList()); // 取出学生 ID 列表
            }
            case "ALL": // 全校广播
                // 全校广播：只发给正常状态（ENABLED）学生，停用/毕业学生不接收，避免打扰无效账号
                return studentMapper.selectList(new LambdaQueryWrapper<Student>() // 查询全部启用状态学生
                                .eq(Student::getStatus, "ENABLED")) // 等值条件：status = ENABLED
                        .stream().map(Student::getId).collect(Collectors.toList()); // 取出学生 ID 列表
            default: // 未知类型
                // fail-closed：未列出的 kind 一律拒绝，防止未来新增类型时静默漏发
                throw new BusinessException("不支持的接收方式：" + kind); // 拒绝未知接收方式
        }
    }

    // ===== 学生收件箱 =====

    public Page<NotificationVO> inbox(int page, int size) { // 学生收件箱：分页查询当前登录学生的通知
        // 收件箱查的是"接收明细"表：当前登录学生（userId）作为 studentId 过滤，天然只有自己的通知，无需额外鉴权
        Long userId = UserContext.getUserId(); // 从上下文取当前登录学生 ID
        Page<NotificationReceiver> p = receiverMapper.selectPage(new Page<>(page, size), // 分页查询接收明细
                new LambdaQueryWrapper<NotificationReceiver>() // 构建查询条件
                        .eq(NotificationReceiver::getStudentId, userId) // 条件：接收学生 = 当前用户
                        .orderByDesc(NotificationReceiver::getId)); // 按明细 id 倒序（最新通知在前）
        return toVoPage(p); // 转换为通知视图分页
    }

    public long unreadCount() { // 查询当前学生未读通知数量（前端角标/红点）
        // 未读数只统计 is_read = false 的记录，供前端角标/红点展示
        return receiverMapper.selectCount(new LambdaQueryWrapper<NotificationReceiver>() // 统计未读数量
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId()) // 条件：接收学生 = 当前用户
                .eq(NotificationReceiver::getIsRead, false)); // 条件：未读
    }

    public void markRead(Long receiverId) { // 把单条通知标记为已读
        Long userId = UserContext.getUserId(); // 从上下文取当前登录学生 ID
        // 属主校验：接收记录必须属于当前登录学生，否则 403——防止越权把别人的通知标记为已读（接收记录 id 可被猜测/遍历）
        NotificationReceiver r = receiverMapper.selectById(receiverId); // 按主键查询接收明细
        if (r == null || !Objects.equals(r.getStudentId(), userId)) { // 明细不存在或不属于当前学生
            throw new BusinessException(403, "无权操作该通知"); // 抛出 403 越权提示
        }
        // 幂等：已读则直接跳过，不重复更新 readAt，避免无谓的写库
        if (!Boolean.TRUE.equals(r.getIsRead())) { // 当前未读
            r.setIsRead(true); // 置为已读
            r.setReadAt(LocalDateTime.now()); // 记录已读时间
            receiverMapper.updateById(r); // 落库更新
        }
    }

    public void readAll() { // 把当前学生全部未读通知标记为已读
        // 一条 UPDATE 批量置已读：只更新当前学生的未读记录，条件里带 studentId 即完成了属主隔离
        receiverMapper.update(null, new LambdaUpdateWrapper<NotificationReceiver>() // 批量更新（无需实体，直接按条件更新）
                .eq(NotificationReceiver::getStudentId, UserContext.getUserId()) // 条件一：接收学生 = 当前用户
                .eq(NotificationReceiver::getIsRead, false) // 条件二：仅未读记录
                .set(NotificationReceiver::getIsRead, true) // 置为已读
                .set(NotificationReceiver::getReadAt, LocalDateTime.now())); // 记录已读时间
    }

    // ===== 老师/管理员发件箱 =====

    public Page<NotificationVO> sent(int page, int size) { // 教师/教秘发件箱：分页查询自己发出的通知
        // 发件箱查的是"通知主表"：按 sender_id 过滤当前登录用户，只能看到自己发出的通知
        Long userId = UserContext.getUserId(); // 从上下文取当前登录用户 ID
        Page<Notification> p = notificationMapper.selectPage(new Page<>(page, size), // 分页查询通知主表
                new LambdaQueryWrapper<Notification>() // 构建查询条件
                        .eq(Notification::getSenderId, userId) // 条件：发送者 = 当前用户
                        .orderByDesc(Notification::getId)); // 按通知 id 倒序
        Page<NotificationVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal()); // 创建视图分页对象（保留原分页元数据）
        vo.setRecords(p.getRecords().stream().map(n -> { // 把通知实体转换为视图对象
            NotificationVO v = new NotificationVO(); // 创建通知视图
            v.setId(n.getId()); // 填充通知 id
            v.setType(n.getType()); // 填充类型
            v.setTitle(n.getTitle()); // 填充标题
            v.setContent(n.getContent()); // 填充内容
            v.setSenderName(n.getSenderName()); // 填充发送者姓名
            v.setCreatedAt(n.getCreatedAt()); // 填充创建时间
            return v; // 返回视图
        }).collect(Collectors.toList())); // 收集为视图列表
        return vo; // 返回发件箱分页
    }

    private Page<NotificationVO> toVoPage(Page<NotificationReceiver> p) { // 接收明细分页 -> 通知视图分页（收件箱专用）
        Page<NotificationVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal()); // 创建视图分页对象
        if (p.getRecords().isEmpty()) { // 本页无记录
            vo.setRecords(List.of()); // 直接返回空列表
            return vo; // 提前返回
        }
        // 批量查出本页涉及的通知主表记录，按 id 建 Map——避免每条明细都查一次主表（N+1 查询问题）
        List<Long> nids = p.getRecords().stream() // 提取本页所有通知 id
                .map(NotificationReceiver::getNotificationId).distinct().collect(Collectors.toList()); // 去重
        Map<Long, Notification> nMap = notificationMapper.selectBatchIds(nids).stream() // 批量查询通知主表
                .collect(Collectors.toMap(Notification::getId, Function.identity())); // 转成"通知 id -> 通知"映射
        vo.setRecords(p.getRecords().stream().map(r -> { // 逐条明细组装视图
            NotificationVO v = new NotificationVO(); // 创建通知视图
            Notification n = nMap.get(r.getNotificationId()); // 取对应主表通知
            if (n == null) return null; // 主表记录缺失则返回 null（稍后过滤）
            v.setId(n.getId()); // 填充通知 id
            v.setReceiverId(r.getId()); // 填充接收明细 id（标记已读用）
            v.setType(n.getType()); // 填充类型
            v.setTitle(n.getTitle()); // 填充标题
            v.setContent(n.getContent()); // 填充内容
            v.setSenderName(n.getSenderName()); // 填充发送者姓名
            // 是否已读取"接收明细"的字段，而不是主表（主表不存已读状态）
            v.setRead(r.getIsRead()); // 填充已读状态（来自接收明细）
            v.setCreatedAt(n.getCreatedAt()); // 填充创建时间
            return v; // 返回视图
        }).filter(Objects::nonNull).collect(Collectors.toList())); // 过滤掉 null（主表记录被删的极端情况）
        // 过滤 null：极端情况下主表记录被删除时，避免把空 VO 传给前端
        return vo; // 返回收件箱分页
    }
}
