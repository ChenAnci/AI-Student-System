package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.config.RedisConfig;
import com.example.sms.dto.LoginDTO;
import com.example.sms.dto.LoginResponse;
import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 登录认证服务
 */
@Slf4j
@Service
public class AuthService {

    @Autowired
    private StaffMapper staffMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate redis;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    /** Redis Key 前缀：登录失败计数（锁定键统一小写，配合 MySQL 大小写不敏感排序规则） */
    private static final String LOGIN_FAIL_KEY = "sms:login:fail:";

    /** Redis Key 前缀：登录尝试频率（单账号每分钟上限，Redis 固定窗口） */
    private static final String LOGIN_RATE_KEY = "sms:rate:login:user:";
    private static final int LOGIN_RATE_LIMIT = 10;
    private static final long LOGIN_RATE_SECONDS = 60;

    /**
     * 登录：学号（S 开头）查学生表，工号查教职工表；带失败锁定保护。
     * 锁定键统一小写规范化，防止利用 MySQL 大小写不敏感排序规则以大小写变体绕过锁定。
     */
    public LoginResponse login(LoginDTO dto) {
        return guardedLogin(dto.getUsername().trim(), dto.getPassword());
    }

    /**
     * 统一登录守卫（login 与 OAuth 绑定共用）：
     * 锁定检查 → 账号限流 → 登录（校验密码与 ENABLED 状态）→ 成功清除失败计数 / 失败累计。
     * 防止 OAuth 绑定成为暴力破解入口（Q-1）与冻结账号绕过（Q-2）。
     */
    private LoginResponse guardedLogin(String username, String password) {
        // 锁定/限流 key 统一小写：MySQL 的排序规则对账号名大小写不敏感（如 "Admin" 与 "admin" 是同一账号），
        // 若锁定 key 保留原始大小写，攻击者可用大小写变体不断生成新 key，绕过同一账号的锁定与限流。
        String lockKey = username.toLowerCase(Locale.ROOT);
        // 第一道防线：连续失败锁定检查（命中则直接抛异常，不再消耗限流配额）
        checkLoginLocked(lockKey);
        // 第二道防线：账号维度限流——单账号每分钟最多 LOGIN_RATE_LIMIT 次登录尝试（Redis 固定窗口），
        // 与拦截器中的 IP 维度限流叠加，分别限制"同一账号被爆破"与"同一来源 IP 刷接口"。
        if (!allowRate(LOGIN_RATE_KEY + lockKey, LOGIN_RATE_LIMIT)) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
        try {
            LoginResponse resp = doLogin(username, password);
            try {
                // 登录成功：清除失败计数，让连续失败记录"归零"重新计数；
                // 否则历史失败会一直累加，导致偶尔输错几次密码的正常用户被误锁。
                redis.delete(LOGIN_FAIL_KEY + lockKey);
            } catch (DataAccessException e) {
                log.warn("Redis 不可用，登录成功清除失败计数失败：{}", e.getMessage());
            }
            return resp;
        } catch (BusinessException e) {
            // 登录失败（密码错/账号停用等）：累计一次失败计数，为后续锁定做准备。
            // 注意密码错误与账号不存在返回同一提示（"工号或密码错误"），不区分账号是否存在，防账号枚举。
            recordLoginFailure(lockKey);
            throw e;
        }
    }

    private LoginResponse doLogin(String username, String password) {
        // 账号前缀路由：S 开头（且非 admin）走学生表，其余走教职工表；
        // admin 是教职工账号，虽然以 S 开头也不可误入学生表。
        if (username.toUpperCase().startsWith("S") && !username.equalsIgnoreCase("admin")) {
            return loginStudent(username, password);
        }
        return loginStaff(username, password);
    }

    // ===== 登录防爆破（Redis 共享：连续 5 次失败锁定 5 分钟，多实例部署计数一致；key 自动过期解锁）=====

    private static final int MAX_FAIL_ATTEMPTS = 5;
    private static final long LOCK_SECONDS = 5 * 60L;

    private void checkLoginLocked(String username) {
        try {
            // 读取当前失败计数：达到 MAX_FAIL_ATTEMPTS（5 次）即锁定，拒绝继续尝试。
            // 锁定由 Redis key 的 TTL 自动解除（5 分钟），无需手动清理，天然防止"永久锁死"误伤正常用户。
            String count = redis.opsForValue().get(LOGIN_FAIL_KEY + username);
            if (count != null && Integer.parseInt(count) >= MAX_FAIL_ATTEMPTS) {
                // 按剩余 TTL 计算剩余分钟数并提示用户，向上取整避免显示"0 分钟后重试"的歧义
                Long ttl = redis.getExpire(LOGIN_FAIL_KEY + username, TimeUnit.SECONDS);
                long remainMin = (ttl == null || ttl <= 0) ? 1 : (ttl / 60) + 1;
                throw new BusinessException("登录失败次数过多，请 " + remainMin + " 分钟后重试");
            }
        } catch (DataAccessException e) {
            // Redis 不可用时降级放行（可用性优先，防护暂时失效），记录告警
            log.warn("Redis 不可用，登录锁定检查降级放行：{}", e.getMessage());
        }
    }

    private void recordLoginFailure(String username) {
        try {
            String key = LOGIN_FAIL_KEY + username;
            String current = redis.opsForValue().get(key);
            if (current != null && Integer.parseInt(current) >= MAX_FAIL_ATTEMPTS) {
                // 已锁定：不再递增（避免锁定期间失败计数无限膨胀，R-1 缓解）
                return;
            }
            // INCR + 首次 EXPIRE 原子脚本（避免进程崩溃导致 key 无 TTL 永久残留）
            redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT,
                    Collections.singletonList(key), String.valueOf(LOCK_SECONDS));
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，登录失败计数降级忽略：{}", e.getMessage());
        }
    }

    /** Redis 固定窗口限流：INCR+EXPIRE 原子脚本，超过 limit 返回 false；Redis 不可用时降级放行 */
    private boolean allowRate(String key, int limit) {
        try {
            // 计数存 Redis、多实例共享：任何一台实例的计数都会同步到其它实例，
            // 避免水平扩容后每台各自计数导致限流上限被放大 limit×实例数。
            Long count = redis.execute(RedisConfig.INCR_EXPIRE_SCRIPT,
                    Collections.singletonList(key), String.valueOf(LOGIN_RATE_SECONDS));
            // count == null（脚本异常无返回值）时按放行处理，fail-open 保证可用性
            return count == null || count <= limit;
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，限流降级放行：{}", e.getMessage());
            return true;
        }
    }

    private LoginResponse loginStaff(String staffNo, String password) {
        Staff staff = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, staffNo));
        // 先校验密码再校验状态：账号不存在与密码错误返回同一提示，避免攻击者通过报错差异枚举有效工号；
        // BCrypt 每次比对耗时稳定，也能平摊时序差异，增加爆破成本。
        // 文案与学号登录统一为中性提示（L-2）：不因账号类型（工号/学号）暴露差异，彻底消除用户枚举面。
        if (staff == null || !encoder.matches(password, staff.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        // 账号状态校验：被停用/冻结的账号一律拒绝登录（含 OAuth 绑定签发路径，见 issueByUserNo/verifyAndLogin）
        if (!"ENABLED".equals(staff.getStatus())) {
            throw new BusinessException("账号已被停用或冻结，请联系教学秘书");
        }
        LoginResponse resp = new LoginResponse();
        resp.setUserId(staff.getId());
        resp.setUserNo(staff.getStaffNo());
        resp.setRealName(staff.getRealName());
        resp.setRoleType(staff.getRoleType());
        resp.setDepartment(staff.getDepartment());
        resp.setToken(jwtUtil.generateToken(staff.getId(), staff.getStaffNo(),
                staff.getRealName(), staff.getRoleType(), staff.getTokenVersion()));
        return resp;
    }

    private LoginResponse loginStudent(String studentNo, String password) {
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));
        // 与教职工登录同一策略：不存在与密码错误同提示（防学号枚举），密码比对用 BCrypt
        if (student == null || !encoder.matches(password, student.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        // 停用/冻结账号拒绝登录
        if (!"ENABLED".equals(student.getStatus())) {
            throw new BusinessException("账号已被停用或冻结，请联系教学秘书");
        }
        LoginResponse resp = new LoginResponse();
        resp.setUserId(student.getId());
        resp.setUserNo(student.getStudentNo());
        resp.setRealName(student.getRealName());
        resp.setRoleType("STUDENT");
        resp.setDepartment(student.getDepartment());
        resp.setMajor(student.getMajor());
        resp.setClassName(student.getClassName());
        resp.setToken(jwtUtil.generateToken(student.getId(), student.getStudentNo(),
                student.getRealName(), "STUDENT", student.getTokenVersion()));
        return resp;
    }

    // ===== OAuth 登录复用：校验账号密码 / 按账号直接签发 =====

    /**
     * 校验账号密码并签发（供 OAuth 绑定场景复用）。
     * 与 login 走同一登录守卫：锁定 + 限流 + ENABLED 状态校验，防止绑定接口被暴力破解 / 冻结账号绕过。
     */
    public LoginResponse verifyAndLogin(String username, String password) {
        return guardedLogin(username.trim(), password);
    }

    /**
     * 按工号/学号直接签发（OAuth 已绑定账号，绑定关系建立时已校验存在与启用）。
     */
    public LoginResponse issueByUserNo(String userNo) {
        if (userNo.toUpperCase().startsWith("S") && !userNo.equalsIgnoreCase("admin")) {
            Student s = studentMapper.selectOne(
                    new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, userNo));
            if (s == null || !"ENABLED".equals(s.getStatus())) {
                throw new BusinessException("账号不存在或已停用");
            }
            return buildStudentResponse(s);
        }
        Staff st = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, userNo));
        if (st == null || !"ENABLED".equals(st.getStatus())) {
            throw new BusinessException("账号不存在或已停用");
        }
        return buildStaffResponse(st);
    }

    private LoginResponse buildStaffResponse(Staff staff) {
        LoginResponse resp = new LoginResponse();
        resp.setUserId(staff.getId());
        resp.setUserNo(staff.getStaffNo());
        resp.setRealName(staff.getRealName());
        resp.setRoleType(staff.getRoleType());
        resp.setDepartment(staff.getDepartment());
        resp.setToken(jwtUtil.generateToken(staff.getId(), staff.getStaffNo(),
                staff.getRealName(), staff.getRoleType(), staff.getTokenVersion()));
        return resp;
    }

    private LoginResponse buildStudentResponse(Student student) {
        LoginResponse resp = new LoginResponse();
        resp.setUserId(student.getId());
        resp.setUserNo(student.getStudentNo());
        resp.setRealName(student.getRealName());
        resp.setRoleType("STUDENT");
        resp.setDepartment(student.getDepartment());
        resp.setMajor(student.getMajor());
        resp.setClassName(student.getClassName());
        resp.setToken(jwtUtil.generateToken(student.getId(), student.getStudentNo(),
                student.getRealName(), "STUDENT", student.getTokenVersion()));
        return resp;
    }
}
