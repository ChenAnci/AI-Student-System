package com.example.sms.service; // 声明当前类所在的包：service 服务层，存放核心业务逻辑

// ===== import 区域：引入 MyBatis-Plus 查询构造器、业务异常、Redis 配置、DTO/VO、实体、Mapper、JWT 工具、Lombok 日志及 Spring 相关依赖 =====
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 条件构造器：编译期类型安全地拼装 SQL 查询条件
import com.example.sms.common.BusinessException; // 自定义业务异常类：抛出后由全局异常处理器统一转成标准响应格式
import com.example.sms.config.RedisConfig; // Redis 配置类：其中声明了 INCR+EXPIRE 原子执行脚本常量（INCR_EXPIRE_SCRIPT）
import com.example.sms.dto.LoginDTO; // 登录请求 DTO：携带用户名与密码
import com.example.sms.dto.LoginResponse; // 登录成功后的响应 DTO：用户基础信息 + JWT 令牌
import com.example.sms.entity.Staff; // 教职工实体类，对应 staff 表
import com.example.sms.entity.Student; // 学生实体类，对应 student 表
import com.example.sms.mapper.StaffMapper; // 教职工表 MyBatis-Plus Mapper 接口：提供按条件查询等单表操作
import com.example.sms.mapper.StudentMapper; // 学生表 Mapper 接口
import com.example.sms.util.JwtUtil; // JWT 工具类：负责生成携带 tokenVersion 参与签名的令牌
import lombok.extern.slf4j.Slf4j; // Lombok 注解：编译期自动生成 slf4j 的 log 日志对象
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入注解：按类型自动装配 Bean
import org.springframework.dao.DataAccessException; // Spring 数据访问异常：用于捕获 Redis 不可用等底层存储故障
import org.springframework.data.redis.core.StringRedisTemplate; // Spring Data Redis 的字符串模板：操作字符串键值、执行 Lua 脚本
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt 密码加密器：加盐散列存储，抗彩虹表与暴力破解
import org.springframework.stereotype.Service; // Spring 服务层注解：将本类注册为容器管理的 Bean
import java.util.Collections; // 集合工具类：用于构造单元素不可变 List
import java.util.Locale; // 区域对象：统一用 Locale.ROOT 做小写转换，避免土耳其语等地区性大小写规则
import java.util.concurrent.TimeUnit; // 时间单位枚举：配合 Redis 过期时间与 TTL 读取使用

/**
 * 登录认证服务
 */
@Slf4j // Lombok 注解：为类生成 log 日志字段，方法内可直接 log.warn 等记录日志
@Service // 声明为 Spring 服务组件，参与组件扫描并支持依赖注入
public class AuthService { // 登录认证服务类：负责账号密码登录、失败锁定、登录限流，以及 OAuth 登录的复用逻辑

    @Autowired // Spring 自动注入 StaffMapper
    private StaffMapper staffMapper; // 教职工表 Mapper：登录时按工号查询教职工账号

    @Autowired // Spring 自动注入 StudentMapper
    private StudentMapper studentMapper; // 学生表 Mapper：登录时按学号查询学生账号

    @Autowired // Spring 自动注入 JwtUtil
    private JwtUtil jwtUtil; // JWT 工具：签发登录令牌（tokenVersion 参与签名，便于吊销旧令牌）

    @Autowired // Spring 自动注入 StringRedisTemplate
    private StringRedisTemplate redis; // Redis 客户端：保存登录失败计数、限流计数等分布式状态

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10); // BCrypt 编码器实例（强度 10，迭代代价适中），用于密码散列的生成与比对

    /** Redis Key 前缀：登录失败计数（锁定键统一小写，配合 MySQL 大小写不敏感排序规则） */
    private static final String LOGIN_FAIL_KEY = "sms:login:fail:"; // 失败计数 key 前缀，完整 key 形如 sms:login:fail:admin

    /** Redis Key 前缀：登录尝试频率（单账号每分钟上限，Redis 固定窗口） */
    private static final String LOGIN_RATE_KEY = "sms:rate:login:user:"; // 登录限流 key 前缀，按账号维度统计尝试次数
    private static final int LOGIN_RATE_LIMIT = 10; // 限流阈值：单账号每分钟最多允许的登录尝试次数
    private static final long LOGIN_RATE_SECONDS = 60; // 限流窗口时长（秒）：固定窗口为 60 秒一个周期

    /**
     * 登录：学号（S 开头）查学生表，工号查教职工表；带失败锁定保护。
     * 锁定键统一小写规范化，防止利用 MySQL 大小写不敏感排序规则以大小写变体绕过锁定。
     * 调用逻辑：AuthController.login → authService.login → guardedLogin（失败锁定检查 → 账号限流 → 密码比对 + ENABLED 校验）→ 签发含 tokenVersion 的 JWT，前端保存后随请求头访问受保护接口。
     * 为什么：账号不存在与密码错误统一提示"账号或密码错误"防账号枚举；Redis 计数锁定 + 限流防暴力破解；JWT 携带 tokenVersion 参与签名，改密/冻结时可整体吊销旧令牌。
     */
    public LoginResponse login(LoginDTO dto) { // 登录入口方法：接收登录 DTO，返回登录响应（含用户信息与 JWT）
        return guardedLogin(dto.getUsername().trim(), dto.getPassword()); // 用户名去除首尾空白后，交给统一登录守卫执行完整校验流程
    }

    /**
     * 统一登录守卫（login 与 OAuth 绑定共用）：
     * 锁定检查 → 账号限流 → 登录（校验密码与 ENABLED 状态）→ 成功清除失败计数 / 失败累计。
     * 防止 OAuth 绑定成为暴力破解入口（Q-1）与冻结账号绕过（Q-2）。
     */
    private LoginResponse guardedLogin(String username, String password) { // 统一登录守卫：封装锁定检查、限流、登录及失败计数的完整流程
        // 锁定/限流 key 统一小写：MySQL 的排序规则对账号名大小写不敏感（如 "Admin" 与 "admin" 是同一账号），
        // 若锁定 key 保留原始大小写，攻击者可用大小写变体不断生成新 key，绕过同一账号的锁定与限流。
        String lockKey = username.toLowerCase(Locale.ROOT); // 把用户名转小写作为锁定/限流 key，保证同一账号无论大小写变体都命中同一把锁
        // 第一道防线：连续失败锁定检查（命中则直接抛异常，不再消耗限流配额）
        checkLoginLocked(lockKey); // 检查该账号是否已因连续失败被锁定，若已锁定则直接抛出业务异常
        // 第二道防线：账号维度限流——单账号每分钟最多 LOGIN_RATE_LIMIT 次登录尝试（Redis 固定窗口），
        // 与拦截器中的 IP 维度限流叠加，分别限制"同一账号被爆破"与"同一来源 IP 刷接口"。
        if (!allowRate(LOGIN_RATE_KEY + lockKey, LOGIN_RATE_LIMIT)) { // 若账号维度限流被触发（超过每分钟上限）
            throw new BusinessException("登录尝试过于频繁，请稍后再试"); // 抛出频率限制提示，拒绝本次登录
        }
        try { // 进入登录主流程，捕获业务异常用于累计失败计数
            LoginResponse resp = doLogin(username, password); // 按账号类型路由到学生/教职工登录并校验密码与状态
            try { // 登录成功后的清理流程单独捕获 Redis 异常，不影响主流程
                // 登录成功：清除失败计数，让连续失败记录"归零"重新计数；
                // 否则历史失败会一直累加，导致偶尔输错几次密码的正常用户被误锁。
                redis.delete(LOGIN_FAIL_KEY + lockKey); // 删除该账号的失败计数 key，成功登录后清零重新累计
            } catch (DataAccessException e) { // Redis 删除失败（如 Redis 不可用）
                log.warn("Redis 不可用，登录成功清除失败计数失败：{}", e.getMessage()); // 仅记录告警日志，不阻断登录成功结果
            }
            return resp; // 返回登录成功响应
        } catch (BusinessException e) { // 登录失败（密码错误/账号停用等）抛出业务异常时
            // 登录失败（密码错/账号停用等）：累计一次失败计数，为后续锁定做准备。
            // 注意密码错误与账号不存在返回同一提示（"工号或密码错误"），不区分账号是否存在，防账号枚举。
            recordLoginFailure(lockKey); // 累计一次失败计数（达到阈值后该账号将被锁定）
            throw e; // 继续向上抛出原始异常，让控制器转成标准错误响应
        }
    }

    private LoginResponse doLogin(String username, String password) { // 根据账号前缀路由到对应账号类型的登录校验
        // 账号前缀路由：S 开头（且非 admin）走学生表，其余走教职工表；
        // admin 是教职工账号，虽然以 S 开头也不可误入学生表。
        if (username.toUpperCase().startsWith("S") && !username.equalsIgnoreCase("admin")) { // 用户名转大写判断是否 S 开头且非 admin（admin 特殊归属教职工表）
            return loginStudent(username, password); // 走学生登录流程（学号查学生表）
        }
        return loginStaff(username, password); // 其余账号一律走教职工登录流程（工号查教职工表）
    }

    // ===== 登录防爆破（Redis 共享：连续 5 次失败锁定 5 分钟，多实例部署计数一致；key 自动过期解锁）=====

    private static final int MAX_FAIL_ATTEMPTS = 5; // 锁定阈值：连续失败达到 5 次即锁定账号
    private static final long LOCK_SECONDS = 5 * 60L; // 锁定时长：5 分钟（由 Redis key TTL 自动到期解锁）

    private void checkLoginLocked(String username) { // 检查账号是否处于锁定状态（达到失败阈值）
        try { // 捕获 Redis 异常做降级处理
            // 读取当前失败计数：达到 MAX_FAIL_ATTEMPTS（5 次）即锁定，拒绝继续尝试。
            // 锁定由 Redis key 的 TTL 自动解除（5 分钟），无需手动清理，天然防止"永久锁死"误伤正常用户。
            String count = redis.opsForValue().get(LOGIN_FAIL_KEY + username); // 读取该账号的失败计数字符串值
            if (count != null && Integer.parseInt(count) >= MAX_FAIL_ATTEMPTS) { // 计数存在且已达到锁定阈值
                // 按剩余 TTL 计算剩余分钟数并提示用户，向上取整避免显示"0 分钟后重试"的歧义
                Long ttl = redis.getExpire(LOGIN_FAIL_KEY + username, TimeUnit.SECONDS); // 查询锁定 key 的剩余存活秒数（即剩余锁定时间）
                long remainMin = (ttl == null || ttl <= 0) ? 1 : (ttl / 60) + 1; // 将剩余秒数换算为分钟并向上取整（异常值按 1 分钟兜底）
                throw new BusinessException("登录失败次数过多，请 " + remainMin + " 分钟后重试"); // 抛出带剩余时间的锁定提示
            }
        } catch (DataAccessException e) { // Redis 不可用导致的读取异常
            // Redis 不可用时降级放行（可用性优先，防护暂时失效），记录告警
            log.warn("Redis 不可用，登录锁定检查降级放行：{}", e.getMessage()); // 记录告警日志，本次不拦截登录（fail-open 策略）
        }
    }

    private void recordLoginFailure(String username) { // 累计一次登录失败计数，达到阈值后触发锁定
        try { // 捕获 Redis 异常做降级处理
            String key = LOGIN_FAIL_KEY + username; // 拼装该账号的失败计数 key
            String current = redis.opsForValue().get(key); // 读取当前失败计数
            if (current != null && Integer.parseInt(current) >= MAX_FAIL_ATTEMPTS) { // 已经达到锁定阈值
                // 已锁定：不再递增（避免锁定期间失败计数无限膨胀，R-1 缓解）
                return; // 直接返回，避免锁定期间计数持续增长
            }
            // INCR + 首次 EXPIRE 原子脚本（避免进程崩溃导致 key 无 TTL 永久残留）
            redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT, // 执行 Redis 原子脚本：自增计数并在首次创建时设置 TTL
                    Collections.singletonList(key), String.valueOf(LOCK_SECONDS)); // 脚本参数：计数的 key 与锁定时长秒数
        } catch (DataAccessException e) { // Redis 不可用导致的写入异常
            log.warn("Redis 不可用，登录失败计数降级忽略：{}", e.getMessage()); // 记录告警，失败计数暂时不累计（可用性优先）
        }
    }

    /** Redis 固定窗口限流：INCR+EXPIRE 原子脚本，超过 limit 返回 false；Redis 不可用时降级放行 */
    private boolean allowRate(String key, int limit) { // 账号维度固定窗口限流：返回是否允许继续尝试
        try { // 捕获 Redis 异常做降级处理
            // 计数存 Redis、多实例共享：任何一台实例的计数都会同步到其它实例，
            // 避免水平扩容后每台各自计数导致限流上限被放大 limit×实例数。
            Long count = redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT, // 原子执行自增并设置窗口 TTL，返回窗口内累计次数
                    Collections.singletonList(key), String.valueOf(LOGIN_RATE_SECONDS)); // 脚本参数：限流 key 与窗口秒数
            // count == null（脚本异常无返回值）时按放行处理，fail-open 保证可用性
            return count == null || count <= limit; // 无返回值视为放行；有值则判断是否超过窗口上限
        } catch (DataAccessException e) { // Redis 不可用导致的限流查询异常
            log.warn("Redis 不可用，限流降级放行：{}", e.getMessage()); // 记录告警
            return true; // 降级放行：Redis 故障期间不拦截登录，保证可用性
        }
    }

    private LoginResponse loginStaff(String staffNo, String password) { // 教职工登录：按工号查表并校验密码与状态
        Staff staff = staffMapper.selectOne( // 按工号查询教职工账号（唯一记录）
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, staffNo)); // 构建等值查询条件：staff_no = 工号
        // 先校验密码再校验状态：账号不存在与密码错误返回同一提示，避免攻击者通过报错差异枚举有效工号；
        // BCrypt 每次比对耗时稳定，也能平摊时序差异，增加爆破成本。
        // 文案与学号登录统一为中性提示（L-2）：不因账号类型（工号/学号）暴露差异，彻底消除用户枚举面。
        if (staff == null || !encoder.matches(password, staff.getPasswordHash())) { // 账号不存在或密码散列不匹配（两者同提示防枚举）
            throw new BusinessException("账号或密码错误"); // 抛出统一的中性错误提示，不区分"账号不存在"与"密码错误"
        }
        // 账号状态校验：被停用/冻结的账号一律拒绝登录（含 OAuth 绑定签发路径，见 issueByUserNo/verifyAndLogin）
        if (!"ENABLED".equals(staff.getStatus())) { // 账号状态不是正常可用（ENABLED）
            throw new BusinessException("账号已被停用或冻结，请联系教学秘书"); // 拒绝登录并提示联系教学秘书
        }
        // token 携带 tokenVersion 参与签名：改密/重置/冻结时版本号递增，旧 token 立即全部失效（吊销）
        LoginResponse resp = new LoginResponse(); // 创建登录响应对象
        resp.setUserId(staff.getId()); // 填充用户主键 ID
        resp.setUserNo(staff.getStaffNo()); // 填充工号
        resp.setRealName(staff.getRealName()); // 填充真实姓名
        resp.setRoleType(staff.getRoleType()); // 填充角色类型（TEACHER/ADMIN 等）
        resp.setDepartment(staff.getDepartment()); // 填充所属院系
        resp.setToken(jwtUtil.generateToken(staff.getId(), staff.getStaffNo(), // 生成 JWT：注入用户 ID 与工号
                staff.getRealName(), staff.getRoleType(), staff.getTokenVersion())); // JWT 附加姓名、角色与 tokenVersion（参与签名用于吊销）
        return resp; // 返回组装完成的登录响应
    }

    private LoginResponse loginStudent(String studentNo, String password) { // 学生登录：按学号查表并校验密码与状态
        Student student = studentMapper.selectOne( // 按学号查询学生账号（唯一记录）
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo)); // 构建等值查询条件：student_no = 学号
        // 与教职工登录同一策略：不存在与密码错误同提示（防学号枚举），密码比对用 BCrypt
        if (student == null || !encoder.matches(password, student.getPasswordHash())) { // 账号不存在或密码散列不匹配
            throw new BusinessException("账号或密码错误"); // 抛出统一中性提示，防止学号枚举
        }
        // 停用/冻结账号拒绝登录
        if (!"ENABLED".equals(student.getStatus())) { // 学生账号状态不是 ENABLED
            throw new BusinessException("账号已被停用或冻结，请联系教学秘书"); // 拒绝登录
        }
        // 与教职工一致：token 携带 tokenVersion 参与签名，改密/重置/冻结后旧 token 全部失效（吊销）
        LoginResponse resp = new LoginResponse(); // 创建登录响应对象
        resp.setUserId(student.getId()); // 填充用户主键 ID
        resp.setUserNo(student.getStudentNo()); // 填充学号
        resp.setRealName(student.getRealName()); // 填充真实姓名
        resp.setRoleType("STUDENT"); // 学生角色固定为 STUDENT
        resp.setDepartment(student.getDepartment()); // 填充所属院系
        resp.setMajor(student.getMajor()); // 填充专业
        resp.setClassName(student.getClassName()); // 填充班级
        resp.setToken(jwtUtil.generateToken(student.getId(), student.getStudentNo(), // 生成 JWT：注入用户 ID 与学号
                student.getRealName(), "STUDENT", student.getTokenVersion())); // JWT 附加姓名、学生角色与 tokenVersion
        return resp; // 返回组装完成的登录响应
    }

    // ===== OAuth 登录复用：校验账号密码 / 按账号直接签发 =====

    /**
     * 校验账号密码并签发（供 OAuth 绑定场景复用）。
     * 与 login 走同一登录守卫：锁定 + 限流 + ENABLED 状态校验，防止绑定接口被暴力破解 / 冻结账号绕过。
     * 调用逻辑：OAuthController.bind → githubOAuthService.bind → authService.verifyAndLogin：先校验账号密码再建立 OAuth 绑定关系，成功后返回的 JWT 即绑定登录态。
     * 为什么：复用 login 的同一登录守卫，避免 OAuth 绑定接口成为绕过锁定/限流的暴力破解入口；同时保证绑定操作确由账号本人发起。
     */
    public LoginResponse verifyAndLogin(String username, String password) { // OAuth 绑定场景的登录校验入口：校验账号密码并签发登录态
        return guardedLogin(username.trim(), password); // 与普通登录共用统一守卫（锁定/限流/状态校验），保证绑定接口不被绕过
    }

    /**
     * 按工号/学号直接签发（OAuth 已绑定账号，绑定关系建立时已校验存在与启用）。
     * 调用逻辑：GithubOAuthService.handleCallback 在 GitHub 用户已绑定系统账号时调用：按绑定关系中的 userNo 路由学生/教职工表，直接签发 JWT 登录态，再经一次性授权码回传前端换取。
     * 为什么：OAuth 登录免密直接放行，但内部仍校验账号存在且 ENABLED，防止账号被删/停用后通过已绑定关系继续放行；响应组装复用 buildStudentResponse/buildStaffResponse 与常规登录保持一致。
     */
    public LoginResponse issueByUserNo(String userNo) { // OAuth 已绑定账号的直接签发入口：按账号号直接发放登录态（免密）
        if (userNo.toUpperCase().startsWith("S") && !userNo.equalsIgnoreCase("admin")) { // S 开头且非 admin 判定为学生账号
            Student s = studentMapper.selectOne( // 按学号查询学生
                    new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, userNo)); // 等值条件：student_no = userNo
            if (s == null || !"ENABLED".equals(s.getStatus())) { // 学生不存在或已被停用
                throw new BusinessException("账号不存在或已停用"); // 拒绝签发，防止被删/停用账号继续放行
            }
            return buildStudentResponse(s); // 组装学生登录响应并签发 JWT
        }
        Staff st = staffMapper.selectOne( // 其余按工号查询教职工
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, userNo)); // 等值条件：staff_no = userNo
        if (st == null || !"ENABLED".equals(st.getStatus())) { // 教职工不存在或已被停用
            throw new BusinessException("账号不存在或已停用"); // 拒绝签发
        }
        return buildStaffResponse(st); // 组装教职工登录响应并签发 JWT
    }

    /** 组装教职工登录响应（OAuth 直接签发复用，与 loginStaff 的响应构建逻辑一致） */
    private LoginResponse buildStaffResponse(Staff staff) { // 将教职工实体组装为登录响应（含 JWT）
        LoginResponse resp = new LoginResponse(); // 创建响应对象
        resp.setUserId(staff.getId()); // 填充用户主键 ID
        resp.setUserNo(staff.getStaffNo()); // 填充工号
        resp.setRealName(staff.getRealName()); // 填充真实姓名
        resp.setRoleType(staff.getRoleType()); // 填充角色类型
        resp.setDepartment(staff.getDepartment()); // 填充所属院系
        resp.setToken(jwtUtil.generateToken(staff.getId(), staff.getStaffNo(), // 生成 JWT：注入用户 ID 与工号
                staff.getRealName(), staff.getRoleType(), staff.getTokenVersion())); // JWT 附加姓名、角色与 tokenVersion
        return resp; // 返回登录响应
    }

    /** 组装学生登录响应（OAuth 直接签发复用，与 loginStudent 的响应构建逻辑一致） */
    private LoginResponse buildStudentResponse(Student student) { // 将学生实体组装为登录响应（含 JWT）
        LoginResponse resp = new LoginResponse(); // 创建响应对象
        resp.setUserId(student.getId()); // 填充用户主键 ID
        resp.setUserNo(student.getStudentNo()); // 填充学号
        resp.setRealName(student.getRealName()); // 填充真实姓名
        resp.setRoleType("STUDENT"); // 学生角色固定为 STUDENT
        resp.setDepartment(student.getDepartment()); // 填充所属院系
        resp.setMajor(student.getMajor()); // 填充专业
        resp.setClassName(student.getClassName()); // 填充班级
        resp.setToken(jwtUtil.generateToken(student.getId(), student.getStudentNo(), // 生成 JWT：注入用户 ID 与学号
                student.getRealName(), "STUDENT", student.getTokenVersion())); // JWT 附加姓名、学生角色与 tokenVersion
        return resp; // 返回登录响应
    }
}
