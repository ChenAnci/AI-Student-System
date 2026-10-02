// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis-Plus 通用 Mapper 基类接口，提供单表通用 CRUD 能力
import com.example.sms.entity.CourseGradeAudit;          // 课程成绩审核实体类，对应数据库 course_grade_audit 表
import org.apache.ibatis.annotations.Mapper;             // MyBatis 的 @Mapper 注解，标记本接口为 Mapper

/**
 * 课程成绩审核表 Mapper：对应数据库表 course_grade_audit，负责成绩审核流程（提交/审核/发布）相关查询
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：继承 BaseMapper<CourseGradeAudit> 后，自动获得成绩审核表的通用增删改查、分页等方法，
// 覆盖成绩审核流程（提交 / 审核 / 发布）各阶段的数据访问
public interface CourseGradeAuditMapper extends BaseMapper<CourseGradeAudit> {
}
