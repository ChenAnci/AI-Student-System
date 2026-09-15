package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /** 行级锁定查询（SELECT ... FOR UPDATE），用于选课容量 check-then-act 防并发超选 */
    @Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE")
    Course selectByIdForUpdate(@Param("id") Long id);
}
