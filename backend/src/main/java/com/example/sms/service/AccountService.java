package com.example.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.sms.common.BusinessException;
import com.example.sms.dto.AccountCreateDTO;
import com.example.sms.dto.AccountUpdateDTO;
import com.example.sms.entity.Staff;
import com.example.sms.entity.Student;
import com.example.sms.excel.StaffExcelRow;
import com.example.sms.excel.StudentExcelRow;
import com.example.sms.mapper.StaffMapper;
import com.example.sms.mapper.StudentMapper;
import com.example.sms.util.ExcelUtil;
import com.example.sms.util.UserContext;
import com.example.sms.vo.AccountImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 账号管理服务（教学秘书）
 */
@Service
public class AccountService {

    @Autowired
    private StaffMapper staffMapper;

    @Autowired
    private StudentMapper studentMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    private static final BigDecimal DEFAULT_REQUIRED_CREDITS = new BigDecimal("160.00");

    // ===== 导入初始密码：随机生成 8 位（去除易混淆字符），避免统一弱口令 =====
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private String randomInitPassword() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /** 校验当前用户必须是教学秘书 */
    public void checkAdmin() {
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "无权限，仅教学秘书可操作");
        }
    }

    /** 教职工列表 */
    public List<Staff> listStaffs(String keyword) {
        LambdaQueryWrapper<Staff> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(keyword != null && !keyword.isBlank(), Staff::getRealName, keyword)
                .or(keyword != null && !keyword.isBlank(), w -> w.like(Staff::getStaffNo, keyword))
                .orderByAsc(Staff::getStaffNo);
        return staffMapper.selectList(wrapper);
    }

    /** 学生列表 */
    public List<Student> listStudents(String keyword) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Student::getRealName, keyword)
                        .or().like(Student::getStudentNo, keyword)
                        .or().like(Student::getMajor, keyword)
                        .or().like(Student::getClassName, keyword))
                .orderByAsc(Student::getStudentNo);
        return studentMapper.selectList(wrapper);
    }

    /**
     * 添加账号（教师生成 T 工号，学生生成 S 学号），初始密码 123456
     */
    @Transactional
    public void createAccount(AccountCreateDTO dto) {
        checkAdmin();
        if ("TEACHER".equals(dto.getRoleType())) {
            Staff staff = new Staff();
            staff.setStaffNo(nextNo("T", false));
            staff.setRealName(dto.getRealName());
            staff.setRoleType("TEACHER");
            staff.setStatus("ENABLED");
            staff.setDepartment(dto.getDepartment());
            staff.setPhone(dto.getPhone());
            staff.setPasswordHash(encoder.encode("123456"));
            staffMapper.insert(staff);
        } else if ("STUDENT".equals(dto.getRoleType())) {
            Student student = new Student();
            student.setStudentNo(nextNo("S", true));
            student.setRealName(dto.getRealName());
            student.setStatus("ENABLED");
            student.setGender(dto.getGender());
            student.setDepartment(dto.getDepartment());
            student.setMajor(dto.getMajor());
            student.setClassName(dto.getClassName());
            student.setEnrollmentYear(dto.getEnrollmentYear());
            student.setPhone(dto.getPhone());
            student.setRequiredCredits(DEFAULT_REQUIRED_CREDITS);
            student.setTotalEarnedCredits(BigDecimal.ZERO);
            student.setGpa(BigDecimal.ZERO);
            student.setPasswordHash(encoder.encode("123456"));
            studentMapper.insert(student);
        } else {
            throw new BusinessException("角色仅支持 TEACHER 或 STUDENT");
        }
    }

    /** 重置密码为 123456 */
    public void resetPassword(String userType, Long id) {
        checkAdmin();
        if ("STAFF".equals(userType)) {
            Staff staff = staffMapper.selectById(id);
            if (staff == null) throw new BusinessException("账号不存在");
            staff.setPasswordHash(encoder.encode("123456"));
            staffMapper.updateById(staff);
        } else if ("STUDENT".equals(userType)) {
            Student student = studentMapper.selectById(id);
            if (student == null) throw new BusinessException("账号不存在");
            student.setPasswordHash(encoder.encode("123456"));
            studentMapper.updateById(student);
        } else {
            throw new BusinessException("非法账号类型");
        }
    }

    /** 编辑账号信息（学号/工号与角色不可修改） */
    public void updateAccount(String userType, Long id, AccountUpdateDTO dto) {
        checkAdmin();
        if ("STAFF".equals(userType)) {
            Staff staff = staffMapper.selectById(id);
            if (staff == null) throw new BusinessException("账号不存在");
            staff.setRealName(dto.getRealName());
            staff.setDepartment(dto.getDepartment());
            staff.setPhone(dto.getPhone());
            staffMapper.updateById(staff);
        } else if ("STUDENT".equals(userType)) {
            Student student = studentMapper.selectById(id);
            if (student == null) throw new BusinessException("账号不存在");
            student.setRealName(dto.getRealName());
            student.setGender(dto.getGender());
            student.setPhone(dto.getPhone());
            student.setDepartment(dto.getDepartment());
            student.setMajor(dto.getMajor());
            student.setClassName(dto.getClassName());
            student.setEnrollmentYear(dto.getEnrollmentYear());
            studentMapper.updateById(student);
        } else {
            throw new BusinessException("非法账号类型");
        }
    }

    /** 冻结/启用账号 */
    public void toggleStatus(String userType, Long id, String status) {
        checkAdmin();
        if (!"ENABLED".equals(status) && !"FROZEN".equals(status) && !"SUSPENDED".equals(status)) {
            throw new BusinessException("非法状态");
        }
        if ("STAFF".equals(userType)) {
            Staff staff = staffMapper.selectById(id);
            if (staff == null) throw new BusinessException("账号不存在");
            staff.setStatus(status);
            staffMapper.updateById(staff);
        } else if ("STUDENT".equals(userType)) {
            Student student = studentMapper.selectById(id);
            if (student == null) throw new BusinessException("账号不存在");
            student.setStatus(status);
            studentMapper.updateById(student);
        } else {
            throw new BusinessException("非法账号类型");
        }
    }

    /** 当前教师/学生查看本人信息 */
    public Object myInfo() {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        if ("STUDENT".equals(role)) {
            return studentMapper.selectById(userId);
        }
        return staffMapper.selectById(userId);
    }

    // ==================== Excel 导入导出 ====================

    /** 导出学生列表 */
    public void exportStudents(HttpServletResponse response) {
        checkAdmin();
        List<Student> students = listStudents(null);
        List<StudentExcelRow> rows = students.stream().map(s -> {
            StudentExcelRow r = new StudentExcelRow();
            r.setRealName(s.getRealName());
            r.setStudentNo(s.getStudentNo());
            r.setGender(s.getGender());
            r.setDepartment(s.getDepartment());
            r.setMajor(s.getMajor());
            r.setClassName(s.getClassName());
            r.setEnrollmentYear(s.getEnrollmentYear());
            r.setPhone(s.getPhone());
            return r;
        }).collect(Collectors.toList());
        ExcelUtil.write(response, "学生列表", StudentExcelRow.class, rows);
    }

    /** 学生导入模板 */
    public void downloadStudentTemplate(HttpServletResponse response) {
        checkAdmin();
        ExcelUtil.write(response, "学生导入模板", StudentExcelRow.class, Collections.emptyList());
    }

    /**
     * 批量导入学生：学号留空自动生成，初始密码为随机 8 位（随导入结果返回，供线下分发）
     * 先整体校验，存在错误则整批不导入并返回错误明细
     */
    @Transactional
    public List<AccountImportResultVO> importStudents(MultipartFile file) {
        checkAdmin();
        List<ExcelUtil.RowItem<StudentExcelRow>> rows = ExcelUtil.readWithRowNumbers(file, StudentExcelRow.class);
        if (rows.isEmpty()) {
            throw new BusinessException("未读取到任何有效数据，请使用模板填写后导入");
        }
        List<String> errors = new ArrayList<>();
        Set<String> noSet = new HashSet<>();
        List<Student> toInsert = new ArrayList<>();
        List<AccountImportResultVO> result = new ArrayList<>();
        for (ExcelUtil.RowItem<StudentExcelRow> item : rows) {
            StudentExcelRow row = item.getData();
            int rowNum = item.getRowNum();
            String name = trimToNull(row.getRealName());
            if (name == null) {
                errors.add("第" + rowNum + "行：姓名不能为空");
                continue;
            }
            String no = trimToNull(row.getStudentNo());
            if (no != null) {
                if (!noSet.add(no)) {
                    errors.add("第" + rowNum + "行：学号 " + no + " 在文件中重复");
                    continue;
                }
                Long count = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                        .eq(Student::getStudentNo, no));
                if (count > 0) {
                    errors.add("第" + rowNum + "行：学号 " + no + " 已存在");
                    continue;
                }
            }
            Student student = new Student();
            student.setStudentNo(no != null ? no : nextNo("S", true));
            student.setRealName(name);
            student.setStatus("ENABLED");
            student.setGender(trimToNull(row.getGender()));
            student.setDepartment(trimToNull(row.getDepartment()));
            student.setMajor(trimToNull(row.getMajor()));
            student.setClassName(trimToNull(row.getClassName()));
            student.setEnrollmentYear(row.getEnrollmentYear() != null ? row.getEnrollmentYear()
                    : java.time.Year.now().getValue());
            student.setPhone(trimToNull(row.getPhone()));
            student.setRequiredCredits(DEFAULT_REQUIRED_CREDITS);
            student.setTotalEarnedCredits(BigDecimal.ZERO);
            student.setGpa(BigDecimal.ZERO);
            String initPwd = randomInitPassword();
            student.setPasswordHash(encoder.encode(initPwd));
            result.add(new AccountImportResultVO(name, student.getStudentNo(), initPwd));
            toInsert.add(student);
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("导入失败（共 " + errors.size() + " 处错误），请修正后重新导入：\n"
                    + String.join("\n", errors.subList(0, Math.min(errors.size(), 10))));
        }
        for (Student student : toInsert) {
            studentMapper.insert(student);
        }
        return result;
    }

    /** 导出教职工列表 */
    public void exportStaffs(HttpServletResponse response) {
        checkAdmin();
        List<Staff> staffs = listStaffs(null);
        List<StaffExcelRow> rows = staffs.stream().map(s -> {
            StaffExcelRow r = new StaffExcelRow();
            r.setRealName(s.getRealName());
            r.setStaffNo(s.getStaffNo());
            r.setRoleType(s.getRoleType());
            r.setDepartment(s.getDepartment());
            r.setPhone(s.getPhone());
            return r;
        }).collect(Collectors.toList());
        ExcelUtil.write(response, "教职工列表", StaffExcelRow.class, rows);
    }

    /** 教职工导入模板 */
    public void downloadStaffTemplate(HttpServletResponse response) {
        checkAdmin();
        ExcelUtil.write(response, "教职工导入模板", StaffExcelRow.class, Collections.emptyList());
    }

    /**
     * 批量导入教职工：工号留空自动生成，初始密码为随机 8 位（随导入结果返回，供线下分发）。
     * 安全限制：导入仅支持 TEACHER，不允许通过导入创建 ADMIN 账号（管理员必须在系统内人工创建）。
     */
    @Transactional
    public List<AccountImportResultVO> importStaffs(MultipartFile file) {
        checkAdmin();
        List<ExcelUtil.RowItem<StaffExcelRow>> rows = ExcelUtil.readWithRowNumbers(file, StaffExcelRow.class);
        if (rows.isEmpty()) {
            throw new BusinessException("未读取到任何有效数据，请使用模板填写后导入");
        }
        List<String> errors = new ArrayList<>();
        Set<String> noSet = new HashSet<>();
        List<Staff> toInsert = new ArrayList<>();
        List<AccountImportResultVO> result = new ArrayList<>();
        for (ExcelUtil.RowItem<StaffExcelRow> item : rows) {
            StaffExcelRow row = item.getData();
            int rowNum = item.getRowNum();
            String name = trimToNull(row.getRealName());
            if (name == null) {
                errors.add("第" + rowNum + "行：姓名不能为空");
                continue;
            }
            String role = trimToNull(row.getRoleType());
            if (role != null && !"TEACHER".equals(role)) {
                errors.add("第" + rowNum + "行：角色仅支持 TEACHER（管理员账号不允许通过导入创建，请在系统中人工创建）");
                continue;
            }
            String no = trimToNull(row.getStaffNo());
            if (no != null) {
                if (!noSet.add(no)) {
                    errors.add("第" + rowNum + "行：工号 " + no + " 在文件中重复");
                    continue;
                }
                Long count = staffMapper.selectCount(new LambdaQueryWrapper<Staff>()
                        .eq(Staff::getStaffNo, no));
                if (count > 0) {
                    errors.add("第" + rowNum + "行：工号 " + no + " 已存在");
                    continue;
                }
            }
            Staff staff = new Staff();
            staff.setStaffNo(no != null ? no : nextNo("T", false));
            staff.setRealName(name);
            staff.setRoleType(role != null ? role : "TEACHER");
            staff.setStatus("ENABLED");
            staff.setDepartment(trimToNull(row.getDepartment()));
            staff.setPhone(trimToNull(row.getPhone()));
            String initPwd = randomInitPassword();
            staff.setPasswordHash(encoder.encode(initPwd));
            result.add(new AccountImportResultVO(name, staff.getStaffNo(), initPwd));
            toInsert.add(staff);
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("导入失败（共 " + errors.size() + " 处错误），请修正后重新导入：\n"
                    + String.join("\n", errors.subList(0, Math.min(errors.size(), 10))));
        }
        for (Staff staff : toInsert) {
            staffMapper.insert(staff);
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 生成下一个工号/学号（T1002 -> T1003，S20230003 -> S20230004）。
     * 带进程内缓存 + 同步：解决同批次（事务内多次查询最大号不变）与并发下生成重复号的问题，
     * 缓存以 DB 最大号与本次已生成号的较大者为基准递增。
     */
    private final Map<String, Long> nextNoCache = new ConcurrentHashMap<>();

    private String nextNo(String prefix, boolean student) {
        synchronized (nextNoCache) {
            String maxNo;
            // 按数字后缀的数值大小取最大号（字符串字典序会把 S2023009 排在 S20230010 之后，导致跨位撞号）
            if (student) {
                maxNo = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                                .last("ORDER BY CAST(SUBSTRING(student_no, 2) AS UNSIGNED) DESC LIMIT 1"))
                        .stream().map(Student::getStudentNo).findFirst().orElse(null);
            } else {
                maxNo = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                                .last("ORDER BY CAST(SUBSTRING(staff_no, 2) AS UNSIGNED) DESC LIMIT 1"))
                        .stream().map(Staff::getStaffNo).findFirst().orElse(null);
            }
            long base = 0;
            if (maxNo != null) {
                String numPart = maxNo.replaceAll("[^0-9]", "");
                base = numPart.isEmpty() ? 0 : Long.parseLong(numPart);
            }
            long cached = nextNoCache.getOrDefault(prefix, 0L);
            long next = Math.max(base, cached) + 1;
            nextNoCache.put(prefix, next);
            return prefix + next;
        }
    }
}
