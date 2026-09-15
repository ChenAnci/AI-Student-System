package com.example.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.sms.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /** 行级锁定查询（SELECT ... FOR UPDATE），用于选课容量 check-then-act 防并发超选 */
    @Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE")
    Course selectByIdForUpdate(@Param("id") Long id);

    /**
     * 学生选课中心：已发布课程列表（关键词 + 学分范围筛选下推数据库）。
     * 关键词匹配课程名 / 课程号 / 授课教师姓名；minCredit / maxCredit 可为 null 表示不过滤。
     */
    @Select("""
            <script>
            SELECT c.* FROM course c
            LEFT JOIN staff s ON c.teacher_id = s.id
            WHERE c.status = 'PUBLISHED'
            <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.course_code LIKE CONCAT('%', #{keyword}, '%')
                     OR s.real_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="minCredit != null">
                AND c.credit &gt;= #{minCredit}
            </if>
            <if test="maxCredit != null">
                AND c.credit &lt;= #{maxCredit}
            </if>
            ORDER BY c.updated_at DESC
            </script>
            """)
    List<Course> selectCenterPublished(@Param("keyword") String keyword,
                                       @Param("minCredit") BigDecimal minCredit,
                                       @Param("maxCredit") BigDecimal maxCredit);
}
