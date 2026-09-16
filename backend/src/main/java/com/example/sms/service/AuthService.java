package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
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
        String lockKey = username.toLowerCase(Locale.ROOT);
        checkLoginLocked(lockKey);
        // 账号维度限流：单账号每分钟最多 LOGIN_RATE_LIMIT 次登录尝试（Redis 固定窗口）
        if (!allowRate(LOGIN_RATE_KEY + lockKey, LOGIN_RATE_LIMIT)) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
        try {
            LoginResponse resp = doLogin(username, password);
            try {
                redis.delete(LOGIN_FAIL_KEY + lockKey);
            } catch (DataAccessException e) {
                log.warn("Redis 不可用，登录成功清除失败计数失败：{}", e.getMessage());
            }
            return resp;
        } catch (BusinessException e) {
            recordLoginFailure(lockKey);
            throw e;
        }
    }

    private LoginResponse doLogin(String username, String password) {
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
            String count = redis.opsForValue().get(LOGIN_FAIL_KEY + username);
            if (count != null && Integer.parseInt(count) >= MAX_FAIL_ATTEMPTS) {
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
            Long count = redis.opsForValue().increment(key);
            // 首次失败时设置过期时间（INCR 原子自增，仅首次返回 1）
            if (count != null && count == 1) {
                redis.expire(key, LOCK_SECONDS, TimeUnit.SECONDS);
            }
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，登录失败计数降级忽略：{}", e.getMessage());
        }
    }

    /** Redis 固定窗口限流：INCR + 首次 EXPIRE，超过 limit 返回 false；Redis 不可用时降级放行 */
    private boolean allowRate(String key, int limit) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, LOGIN_RATE_SECONDS, TimeUnit.SECONDS);
            }
            return count == null || count <= limit;
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，限流降级放行：{}", e.getMessage());
            return true;
        }
    }

    private LoginResponse loginStaff(String staffNo, String password) {
        Staff staff = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, staffNo));
        if (staff == null || !encoder.matches(password, staff.getPasswordHash())) {
            throw new BusinessException("工号或密码错误");
        }
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
                staff.getRealName(), staff.getRoleType()));
        return resp;
    }

    private LoginResponse loginStudent(String studentNo, String password) {
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));
        if (student == null || !encoder.matches(password, student.getPasswordHash())) {
            throw new BusinessException("学号或密码错误");
        }
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
                student.getRealName(), "STUDENT"));
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
                staff.getRealName(), staff.getRoleType()));
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
                student.getRealName(), "STUDENT"));
        return resp;
    }
}
