package com.example.sms.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 启动时数据初始化：
 * 1. 确保初始账号存在（admin / T1001 / T1002 / S20230001~3）
 * 2. 初始账号仅在首次创建时设置初始密码 123456；已存在账号不覆盖用户修改后的密码
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private StaffMapper staffMapper;

    @Autowired
    private StudentMapper studentMapper;

    // BCrypt 密码编码器（cost=10）：首次创建初始账号时动态加密默认密码
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    // 初始密码 123456 的预计算 BCrypt 哈希（与 encoder 生成的哈希一致）
    private static final String DEFAULT_PWD_HASH =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    /**
     * 调用逻辑：实现 CommandLineRunner，Spring 容器启动完成后由 Spring Boot 自动回调一次（每个应用进程仅执行一次）。
     * 为什么：初始化采用幂等设计——每次启动都校验一遍，但只补缺失的初始账号，
     * 已存在账号（含用户改过的密码）不做任何覆盖，避免"重启即重置数据"。
     * 应用启动后执行：确保管理员/教师/学生的初始账号存在；
     * 仅首次创建时写入初始密码 123456，已存在账号不做任何改动（不覆盖用户修改后的数据）。
     */
    @Override
    public void run(String... args) {
        ensureStaff("admin", "系统管理员", "ADMIN", "教务处", "13800000000");
        ensureStaff("T1001", "王建国", "TEACHER", "计算机学院", "13800000001");
        ensureStaff("T1002", "李秀兰", "TEACHER", "计算机学院", "13800000002");

        ensureStudent("S20230001", "张三", "男", "计算机学院", "软件工程", "软工2301", 2023, new BigDecimal("3.00"));
        ensureStudent("S20230002", "李四", "女", "计算机学院", "软件工程", "软工2301", 2023, BigDecimal.ZERO);
        ensureStudent("S20230003", "王五", "男", "计算机学院", "计算机科学", "计科2301", 2023, BigDecimal.ZERO);

        log.info(">>> 初始账号数据校验完成，初始密码均为 123456");
    }

    /**
     * 调用逻辑：仅由 {@link #run(String...)} 在启动初始化阶段逐个工号调用（每个初始职工各调一次）。
     * 为什么：幂等创建——先按工号查询，只有不存在才插入；已存在的账号（含已修改的密码/状态）不做任何改动。
     * 按工号查询职工账号，不存在则创建为启用状态的初始账号（初始密码 123456）
     */
    private void ensureStaff(String no, String name, String role, String dept, String phone) {
        Staff staff = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, no));
        if (staff == null) {
            // 仅首次初始化创建账号并设置初始密码；此后启动不再覆盖用户已修改的密码
            staff = new Staff();
            staff.setStaffNo(no);
            staff.setRealName(name);
            staff.setRoleType(role);
            staff.setDepartment(dept);
            staff.setPhone(phone);
            staff.setStatus("ENABLED");
            staff.setPasswordHash(encoder.encode("123456"));
            staffMapper.insert(staff);
        }
    }

    /**
     * 调用逻辑：仅由 {@link #run(String...)} 在启动初始化阶段逐个学号调用（每个初始学生各调一次）。
     * 为什么：幂等创建——先按学号查询，只有不存在才插入，避免每次启动重复建号或覆盖用户已修改的资料。
     * 按学号查询学生账号，不存在则创建为启用状态的初始账号（含入学年份、学分、GPA 等初始资料）
     */
    private void ensureStudent(String no, String name, String gender, String dept,
                               String major, String className, int year, BigDecimal credits) {
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, no));
        if (student == null) {
            // 仅首次初始化创建账号并设置初始密码；此后启动不再覆盖用户已修改的密码
            student = new Student();
            student.setStudentNo(no);
            student.setRealName(name);
            student.setGender(gender);
            student.setDepartment(dept);
            student.setMajor(major);
            student.setClassName(className);
            student.setEnrollmentYear(year);
            student.setStatus("ENABLED");
            student.setTotalEarnedCredits(credits);
            student.setRequiredCredits(new BigDecimal("160.00"));
            student.setGpa(credits.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("3.00") : BigDecimal.ZERO);
            student.setPasswordHash(encoder.encode("123456"));
            studentMapper.insert(student);
        }
    }
}
