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

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    private static final String DEFAULT_PWD_HASH =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

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
