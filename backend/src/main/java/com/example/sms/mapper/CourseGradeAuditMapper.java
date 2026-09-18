package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.CourseGradeAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程成绩审核表 Mapper：对应数据库表 course_grade_audit，负责成绩审核流程（提交/审核/发布）相关查询
 */
@Mapper
public interface CourseGradeAuditMapper extends BaseMapper<CourseGradeAudit> {
}
