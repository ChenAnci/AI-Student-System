package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.StudentCourse;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生选课表 Mapper：对应数据库表 student_course，负责选课记录（含成绩、考试标记）的增删改查
 */
@Mapper
public interface StudentCourseMapper extends BaseMapper<StudentCourse> {
}
