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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 登录认证服务
 */
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
        String username = dto.getUsername().trim();
        String lockKey = username.toLowerCase(Locale.ROOT);
        checkLoginLocked(lockKey);
        // 账号维度限流：单账号每分钟最多 LOGIN_RATE_LIMIT 次登录尝试（Redis 固定窗口）
        if (!allowRate(LOGIN_RATE_KEY + lockKey, LOGIN_RATE_LIMIT)) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
        try {
            LoginResponse resp = doLogin(username, dto.getPassword());
            redis.delete(LOGIN_FAIL_KEY + lockKey);
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
        String count = redis.opsForValue().get(LOGIN_FAIL_KEY + username);
        if (count != null && Integer.parseInt(count) >= MAX_FAIL_ATTEMPTS) {
            Long ttl = redis.getExpire(LOGIN_FAIL_KEY + username, TimeUnit.SECONDS);
            long remainMin = (ttl == null || ttl <= 0) ? 1 : (ttl / 60) + 1;
            throw new BusinessException("登录失败次数过多，请 " + remainMin + " 分钟后重试");
        }
    }

    private void recordLoginFailure(String username) {
        String key = LOGIN_FAIL_KEY + username;
        Long count = redis.opsForValue().increment(key);
        // 首次失败时设置过期时间（INCR 原子自增，仅首次返回 1）
        if (count != null && count == 1) {
            redis.expire(key, LOCK_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** Redis 固定窗口限流：INCR + 首次 EXPIRE，超过 limit 返回 false */
    private boolean allowRate(String key, int limit) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, LOGIN_RATE_SECONDS, TimeUnit.SECONDS);
        }
        return count == null || count <= limit;
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
     * 校验账号密码并签发（供 OAuth 绑定场景复用；不做失败锁定计数）。
     */
    public LoginResponse verifyAndLogin(String username, String password) {
        if (username.toUpperCase().startsWith("S") && !username.equalsIgnoreCase("admin")) {
            Student s = studentMapper.selectOne(
                    new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, username));
            if (s == null || !encoder.matches(password, s.getPasswordHash())) {
                throw new BusinessException("学号或密码错误");
            }
            return buildStudentResponse(s);
        }
        Staff st = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, username));
        if (st == null || !encoder.matches(password, st.getPasswordHash())) {
            throw new BusinessException("工号或密码错误");
        }
        return buildStaffResponse(st);
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
