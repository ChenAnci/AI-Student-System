package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入数据初始化所需的实体、Mapper、Spring 与 MyBatis-Plus 相关类 ----
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 条件构造器：以类型安全方式构造查询条件
import com.example.sms.entity.Staff; // 教职工实体类：对应 staff 表
import com.example.sms.entity.Student; // 学生实体类：对应 student 表
import com.example.sms.mapper.StaffMapper; // 教职工 Mapper 接口：提供 staff 表的增删改查
import com.example.sms.mapper.StudentMapper; // 学生 Mapper 接口：提供 student 表的增删改查
import lombok.extern.slf4j.Slf4j; // Lombok 的 @Slf4j 注解：自动生成 log 日志对象
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 注解：按类型自动注入依赖
import org.springframework.boot.CommandLineRunner; // Spring Boot 启动后回调接口：容器就绪后执行一次
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt 密码加密器：用于加密初始密码
import org.springframework.stereotype.Component; // @Component 注解：将该类注册为 Spring 容器管理的组件

import java.math.BigDecimal; // 高精度小数类：用于学分、GPA 等需要精确计算的数值字段

/**
 * 启动时数据初始化：
 * 1. 确保初始账号存在（admin / T1001 / T1002 / S20230001~3）
 * 2. 初始账号仅在首次创建时设置初始密码 123456；已存在账号不覆盖用户修改后的密码
 */
@Slf4j // 自动生成 log 日志对象，供本类打印初始化过程日志
@Component // 注册为 Spring Bean：应用启动时自动执行初始化逻辑
public class DataInitializer implements CommandLineRunner { // 实现 CommandLineRunner：应用启动完成后由 Spring Boot 回调 run()

    @Autowired // 注入教职工 Mapper
    private StaffMapper staffMapper; // 教职工表数据访问对象

    @Autowired // 注入学生 Mapper
    private StudentMapper studentMapper; // 学生表数据访问对象

    // BCrypt 密码编码器（cost=10）：首次创建初始账号时动态加密默认密码
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10); // 密码编码器实例：cost=10 表示加密强度（迭代 2^10 次）

    // 初始密码 123456 的预计算 BCrypt 哈希（与 encoder 生成的哈希一致）
    private static final String DEFAULT_PWD_HASH = // 预计算的默认密码哈希常量（供比对/审计使用）
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2"; // 123456 的 BCrypt 哈希值（固定字符串）

    /**
     * 调用逻辑：实现 CommandLineRunner，Spring 容器启动完成后由 Spring Boot 自动回调一次（每个应用进程仅执行一次）。
     * 为什么：初始化采用幂等设计——每次启动都校验一遍，但只补缺失的初始账号，
     * 已存在账号（含用户改过的密码）不做任何覆盖，避免"重启即重置数据"。
     * 应用启动后执行：确保管理员/教师/学生的初始账号存在；
     * 仅首次创建时写入初始密码 123456，已存在账号不做任何改动（不覆盖用户修改后的数据）。
     */
    @Override // 重写 CommandLineRunner 接口的 run 方法
    public void run(String... args) { // 容器启动完成后由 Spring Boot 自动调用（args 为应用启动参数）
        ensureStaff("admin", "系统管理员", "ADMIN", "教务处", "13800000000"); // 确保管理员账号 admin 存在（初始密码 123456）
        ensureStaff("T1001", "王建国", "TEACHER", "计算机学院", "13800000001"); // 确保教师账号 T1001 存在
        ensureStaff("T1002", "李秀兰", "TEACHER", "计算机学院", "13800000002"); // 确保教师账号 T1002 存在

        ensureStudent("S20230001", "张三", "男", "计算机学院", "软件工程", "软工2301", 2023, new BigDecimal("3.00")); // 确保学生 S20230001 存在（已有学分，GPA 3.00）
        ensureStudent("S20230002", "李四", "女", "计算机学院", "软件工程", "软工2301", 2023, BigDecimal.ZERO); // 确保学生 S20230002 存在（无学分，GPA 0）
        ensureStudent("S20230003", "王五", "男", "计算机学院", "计算机科学", "计科2301", 2023, BigDecimal.ZERO); // 确保学生 S20230003 存在（无学分，GPA 0）

        log.info(">>> 初始账号数据校验完成，初始密码均为 123456"); // 打印初始化完成提示日志
    }

    /**
     * 调用逻辑：仅由 {@link #run(String...)} 在启动初始化阶段逐个工号调用（每个初始职工各调一次）。
     * 为什么：幂等创建——先按工号查询，只有不存在才插入；已存在的账号（含已修改的密码/状态）不做任何改动。
     * 按工号查询职工账号，不存在则创建为启用状态的初始账号（初始密码 123456）
     */
    private void ensureStaff(String no, String name, String role, String dept, String phone) { // 私有方法：幂等地确保教职工账号存在（参数：工号/姓名/角色/部门/手机号）
        Staff staff = staffMapper.selectOne( // 查询单个教职工
                new LambdaQueryWrapper<Staff>().eq(Staff::getStaffNo, no)); // 构造查询条件：staff_no 字段等于参数 no
        if (staff == null) { // 查询结果为空，说明该账号尚不存在
            // 仅首次初始化创建账号并设置初始密码；此后启动不再覆盖用户已修改的密码
            staff = new Staff(); // 创建新的教职工实体对象
            staff.setStaffNo(no); // 设置工号
            staff.setRealName(name); // 设置真实姓名
            staff.setRoleType(role); // 设置角色类型（ADMIN/TEACHER）
            staff.setDepartment(dept); // 设置所属部门/学院
            staff.setPhone(phone); // 设置手机号
            staff.setStatus("ENABLED"); // 设置账号状态为启用
            staff.setPasswordHash(encoder.encode("123456")); // 用 BCrypt 加密初始密码 123456 后存入（不存明文）
            staffMapper.insert(staff); // 将新账号插入数据库
        }
    }

    /**
     * 调用逻辑：仅由 {@link #run(String...)} 在启动初始化阶段逐个学号调用（每个初始学生各调一次）。
     * 为什么：幂等创建——先按学号查询，只有不存在才插入，避免每次启动重复建号或覆盖用户已修改的资料。
     * 按学号查询学生账号，不存在则创建为启用状态的初始账号（含入学年份、学分、GPA 等初始资料）
     */
    private void ensureStudent(String no, String name, String gender, String dept, // 私有方法：幂等地确保学生账号存在（参数：学号/姓名/性别/院系）
                               String major, String className, int year, BigDecimal credits) { // 方法参数续行：专业/班级/入学年份/已修学分
        Student student = studentMapper.selectOne( // 查询单个学生
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, no)); // 构造查询条件：student_no 字段等于参数 no
        if (student == null) { // 查询结果为空，说明该账号尚不存在
            // 仅首次初始化创建账号并设置初始密码；此后启动不再覆盖用户已修改的密码
            student = new Student(); // 创建新的学生实体对象
            student.setStudentNo(no); // 设置学号
            student.setRealName(name); // 设置真实姓名
            student.setGender(gender); // 设置性别
            student.setDepartment(dept); // 设置所属院系
            student.setMajor(major); // 设置专业
            student.setClassName(className); // 设置班级
            student.setEnrollmentYear(year); // 设置入学年份
            student.setStatus("ENABLED"); // 设置账号状态为启用
            student.setTotalEarnedCredits(credits); // 设置已修学分
            student.setRequiredCredits(new BigDecimal("160.00")); // 设置毕业要求学分 160
            student.setGpa(credits.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("3.00") : BigDecimal.ZERO); // 若已有学分则 GPA 设为 3.00，否则为 0
            student.setPasswordHash(encoder.encode("123456")); // 用 BCrypt 加密初始密码 123456 后存入（不存明文）
            studentMapper.insert(student); // 将新账号插入数据库
        }
    }
}
