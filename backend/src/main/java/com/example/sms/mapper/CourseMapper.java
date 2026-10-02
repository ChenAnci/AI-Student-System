// 声明包名：本接口位于 com.example.sms.mapper 包，属于数据访问（Mapper）层
package com.example.sms.mapper;

// 导入区：引入本接口所需的类与注解
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis-Plus 通用 Mapper 基类接口，提供单表通用 CRUD 能力
import com.example.sms.entity.Course;                    // 课程实体类，对应数据库 course 表
import org.apache.ibatis.annotations.Mapper;            // MyBatis 的 @Mapper 注解，标记本接口为 Mapper
import org.apache.ibatis.annotations.Param;             // @Param 注解：为 SQL 中 #{} 占位符显式绑定参数名
import org.apache.ibatis.annotations.Select;            // @Select 注解：标注自定义查询 SQL

import java.math.BigDecimal; // 高精度小数类型，学分字段使用，避免浮点精度误差
import java.util.List;       // 列表集合类型，用于返回多条查询结果

/**
 * 课程表 Mapper：对应数据库表 course，提供课程基础 CRUD 及选课中心相关查询
 */
// @Mapper：让 MyBatis 扫描本接口并自动生成代理实现类，供 Service 层注入使用
@Mapper
// 接口声明：继承 BaseMapper<Course> 获得课程表通用增删改查；下方为自定义 SQL 方法
public interface CourseMapper extends BaseMapper<Course> {

    /** 行级锁定查询（SELECT ... FOR UPDATE），用于选课容量 check-then-act 防并发超选 */
    // @Select：标注下方字符串为执行的自定义 SQL
    // SQL 逐行解释：
    //   SELECT * FROM course   —— 查询课程表全部字段
    //   WHERE id = #{id}       —— 按主键 id 精确定位一条课程（#{} 为预编译占位符，可防 SQL 注入）
    //   FOR UPDATE             —— 对命中的行加"行级排它锁"，直到事务提交/回滚才释放
    // 用途：选课时先查剩余容量再扣减（check-then-act），加锁可避免并发下多个请求同时读到同一剩余容量导致超选
    @Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE")
    // 方法定义：入参 id 为课程主键（@Param 指定占位符名），返回该课程对象；查询不到时返回 null
    Course selectByIdForUpdate(@Param("id") Long id);

    /**
     * 学生选课中心：已发布课程列表（关键词 + 学分范围筛选下推数据库）。
     * 关键词匹配课程名 / 课程号 / 授课教师姓名；minCredit / maxCredit 可为 null 表示不过滤。
     */
    // ===== 下方 @Select 文本块 SQL 逐行解释（文本块内部是字符串字面量，不能插入注释，故在此逐行说明）=====
    // <script>...</script>：MyBatis 动态 SQL 包裹标记，只有在其内部才能使用 <if> 等动态标签
    // SELECT c.* FROM course c：查询课程表全部字段，c 为课程表别名
    // LEFT JOIN staff s ON c.teacher_id = s.id：左连接教职工表 s，目的是取授课教师姓名（s.real_name）参与搜索；
    //                                            LEFT JOIN 保证即使教师信息缺失，课程也仍会被查出
    // WHERE c.status = 'PUBLISHED'：只展示"已发布"状态的课程（未发布课程对学生不可见）
    // <if test="keyword != null and keyword != ''">：当关键词非空时才拼入以下模糊筛选
    //   AND (c.course_name LIKE ... OR c.course_code LIKE ... OR s.real_name LIKE ...)：
    //   对课程名 / 课程号 / 教师姓名三个字段做模糊匹配（%关键词%），任一命中即算匹配；
    //   外层括号保证三个 OR 条件作为整体，避免与后续 AND 条件的优先级出错
    // <if test="minCredit != null">：当最低学分不为空时才拼入以下条件
    //   AND c.credit &gt;= #{minCredit}：学分 >= 最低学分（&gt; 是 XML 中对 > 的转义写法）
    // <if test="maxCredit != null">：当最高学分不为空时才拼入以下条件
    //   AND c.credit &lt;= #{maxCredit}：学分 <= 最高学分（&lt; 是 XML 中对 < 的转义写法）
    // ORDER BY c.updated_at DESC：按课程更新时间倒序，最新发布/更新的课程排在最前
    // </script>：动态 SQL 结束标记
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
    // 方法定义：keyword 为搜索关键词（可为 null/空串），minCredit / maxCredit 为学分上下限（可为 null 表示不过滤）；
    // 返回符合条件的已发布课程列表；筛选全部下推到数据库完成，避免全表查回内存再过滤
    List<Course> selectCenterPublished(@Param("keyword") String keyword,
                                       @Param("minCredit") BigDecimal minCredit,
                                       @Param("maxCredit") BigDecimal maxCredit);
}
