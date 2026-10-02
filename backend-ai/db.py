"""MySQL 只读访问层：所有查询强制绑定学号，学生只能查自己的数据。"""
# contextmanager：把生成器函数包装成上下文管理器，支持 with 语句
from contextlib import contextmanager
# datetime：用于判断数据库返回的日期时间字段并格式化
from datetime import datetime
# Decimal：用于判断数据库 DECIMAL 字段（需转成 float 才能被 JSON 序列化）
from decimal import Decimal
# typing：提供类型标注（Any 表示任意类型，Iterator 表示迭代器类型）
from typing import Any, Iterator

# pymysql：纯 Python 实现的 MySQL 驱动，用于连接与执行 SQL
import pymysql

# 导入全局配置（数据库连接参数）
from config import settings


# 数据库连接上下文管理器：with _conn() as conn 使用，自动建立/关闭连接
@contextmanager
def _conn() -> Iterator[pymysql.connections.Connection]:
    """数据库连接上下文管理器：创建短连接、用毕自动关闭。

    返回：pymysql 连接对象（DictCursor，查询结果以 dict 返回）。
    """
    # 每次操作新建连接、用完即关（上下文管理器）：本服务是低频只读查询，
    # 相比维护连接池，简单起见直接短连接，避免池内连接失效导致的脏读/复用问题。
    # 建立数据库连接：主机/端口/账号/密码/库名全部来自配置，charset 用 utf8mb4 以支持中文与 emoji
    conn = pymysql.connect(
        host=settings.mysql_host,  # 数据库主机
        port=settings.mysql_port,  # 数据库端口
        user=settings.mysql_user,  # 数据库用户名（低权账号）
        password=settings.mysql_password,  # 数据库密码
        database=settings.mysql_db,  # 数据库名
        charset="utf8mb4",  # 字符集：兼容中文、表情符号等四字节字符
        # 使用 DictCursor：查询结果以 dict 返回，字段名可直接引用，便于后续 JSON 序列化。
        # 游标类型设为 DictCursor：每行查询结果以字典返回（键为列名）
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        # 把连接交给 with 块内的业务代码使用
        yield conn
    finally:
        # 无论正常还是异常退出，都关闭连接，防止连接泄漏
        conn.close()


# 结果清洗函数：把数据库特有类型（Decimal/datetime）转成 JSON 可序列化的 Python 原生类型
def _clean(row: dict[str, Any] | None) -> dict[str, Any] | None:
    """把 Decimal / datetime 转成 JSON 可序列化类型。"""
    # 无记录（None）时直接原样返回
    if row is None:
        return None
    # 新建输出字典，用于逐字段转换
    out: dict[str, Any] = {}
    # 遍历查询结果行的每一列（键为列名，值为原始值）
    for k, v in row.items():
        # Decimal 类型（如 GPA、学分等 DECIMAL 列）：转成 float，否则 JSON 序列化会报错
        if isinstance(v, Decimal):
            out[k] = float(v)
        # datetime 类型（如时间戳列）：格式化为 "年-月-日 时:分:秒" 字符串，可读且可序列化
        elif isinstance(v, datetime):
            out[k] = v.strftime("%Y-%m-%d %H:%M:%S")
        # 其它类型（str/int/None/bytes 等）原样保留
        else:
            out[k] = v
    # 返回清洗后的字典
    return out


# 学生基本信息查询函数
def get_student(student_no: str) -> dict[str, Any] | None:
    """学生基本信息（不含密码）。"""
    # 参数化 SQL：学号等用户可控值一律走 %s 占位符由驱动转义，
    # 从根上杜绝 SQL 注入（禁止字符串拼接 SQL）。
    # 只查询必要字段（不包含密码等敏感列），WHERE 条件用 %s 占位符绑定学号
    sql = (
        "SELECT student_no, real_name, gender, phone, department, major, class_name, "
        "enrollment_year, total_earned_credits, required_credits, gpa, status "
        "FROM student WHERE student_no = %s"
    )
    # 双重上下文：外层开连接、内层开游标，结束时自动关闭
    with _conn() as conn, conn.cursor() as cur:
        # 执行参数化查询：学号作为元组参数传入（由驱动安全转义，防 SQL 注入）
        cur.execute(sql, (student_no,))
        # 取第一行（学号唯一，最多一行），清洗后返回；查无此人时返回 None
        return _clean(cur.fetchone())


# 成绩单查询函数
def get_grades(student_no: str) -> list[dict[str, Any]]:
    """成绩单：课程名/学分/分数/mark/成绩审核状态。"""
    # 越权防线：WHERE 强制以 student_no 过滤，且该学号由上层（tools.py）取自已验签的
    # JWT claims，与请求参数解耦——即使客户端传了别人的学号也查不到他人成绩。
    # LEFT JOIN 成绩审核表：无审核记录时用 COALESCE 兜底为 'DRAFT'，保证行不丢。
    # 多表联查：选课表 × 课程表 × 成绩审核表 × 学生表，按学号过滤并按选课时间排序
    sql = (
        "SELECT c.course_name, c.credit, sc.score, sc.mark, "
        "COALESCE(ga.status, 'DRAFT') AS audit_status "  # 审核状态为空时兜底为 DRAFT（草稿）
        "FROM student_course sc "
        "JOIN course c ON c.id = sc.course_id "
        "LEFT JOIN course_grade_audit ga ON ga.course_id = c.id "
        "JOIN student s ON s.id = sc.student_id "
        "WHERE s.student_no = %s "
        "ORDER BY sc.created_at"  # 按选课时间升序，成绩单按时间线展示
    )
    with _conn() as conn, conn.cursor() as cur:
        # 执行参数化查询（学号绑定）
        cur.execute(sql, (student_no,))
        # 取全部行并逐行清洗，返回列表
        return [_clean(r) for r in cur.fetchall()]


# 课表查询函数
def get_schedule(student_no: str) -> list[dict[str, Any]]:
    """课表：已选课程的时间地点与教师。"""
    # 同样强制按学号过滤：学生只能看到自己已选课程，杜绝越权查他人课表。
    # 联查选课表、课程表、教师表，只返回该生已选课程的上课信息
    sql = (
        "SELECT c.course_name, c.credit, c.schedule, c.location, st.real_name AS teacher_name "
        "FROM student_course sc "
        "JOIN course c ON c.id = sc.course_id "
        "JOIN staff st ON st.id = c.teacher_id "
        "JOIN student s ON s.id = sc.student_id "
        "WHERE s.student_no = %s"  # 强制绑定学号
    )
    with _conn() as conn, conn.cursor() as cur:
        # 执行参数化查询
        cur.execute(sql, (student_no,))
        # 取全部行并逐行清洗后返回
        return [_clean(r) for r in cur.fetchall()]


# 可选课程查询函数（用于选课建议）
def get_available_courses(student_no: str) -> list[dict[str, Any]]:
    """可选课程：已发布、未选、未满员。"""
    # 子查询按学号反查该生已选课程，主查询排除之：返回的是"对他个人而言"可选（未选）的课，
    # 因此也必须绑定学号——用别人的学号查会得到不准确的选课建议（但不会泄露他人隐私数据）。
    # 主查询过滤条件：课程已发布、未满员、且不在该生已选课程的子查询结果中
    sql = (
        "SELECT c.id, c.course_code, c.course_name, c.credit, c.hours, "
        "c.schedule, c.location, c.capacity, c.current_enrolled, st.real_name AS teacher_name "
        "FROM course c "
        "JOIN staff st ON st.id = c.teacher_id "
        "WHERE c.status = 'PUBLISHED' "  # 只推荐已发布课程
        "AND c.current_enrolled < c.capacity "  # 未满员（已选人数 < 容量）
        "AND c.id NOT IN ("  # 排除该生已选的课程
        "  SELECT course_id FROM student_course "
        "  WHERE student_id = (SELECT id FROM student WHERE student_no = %s)"
        ")"
    )
    with _conn() as conn, conn.cursor() as cur:
        # 执行参数化查询（子查询中绑定学号）
        cur.execute(sql, (student_no,))
        # 取全部行并逐行清洗后返回
        return [_clean(r) for r in cur.fetchall()]


# LIKE 通配符转义函数
def _escape_like(keyword: str) -> str:
    """转义 LIKE 模式通配符，避免用户/LLM 输入中的 % _ \\ 扩大匹配范围（非 SQL 注入，仅限定返回集）。"""
    # 依次转义三个通配符：\ 先转成 \\（必须最先转，避免二次转义）、% 转成 \% 、_ 转成 \_，
    # 使其成为字面字符而不是 SQL 通配符，从而把模糊匹配面限定在用户真实意图内
    return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


# 课程详情模糊查询函数
def get_course_detail(keyword: str) -> list[dict[str, Any]]:
    """按课程名/编号模糊查询课程详情。"""
    # LIKE 模糊查询属于公开课程目录数据，不涉及个人隐私，无需绑定学号。
    # 关键词仍走参数化传参（防注入），且通配符已转义（_escape_like），
    # 防止用户/LLM 输入里的 % _ 把匹配面扩到全表。
    # 联查课程表与教师表，按课程名或课程编号做 LIKE 模糊匹配
    sql = (
        "SELECT c.id, c.course_code, c.course_name, c.credit, c.hours, "
        "c.schedule, c.location, c.capacity, c.current_enrolled, c.status, "
        "st.real_name AS teacher_name, st.department AS teacher_department "
        "FROM course c "
        "JOIN staff st ON st.id = c.teacher_id "
        "WHERE c.course_name LIKE %s OR c.course_code LIKE %s"  # 名称或编号任一命中即可
    )
    # 构造 LIKE 模式：关键词前后加 % 表示"包含即匹配"，同时把关键词内的通配符转义成字面量
    like = f"%{_escape_like(keyword or '')}%"
    with _conn() as conn, conn.cursor() as cur:
        # 同一个 LIKE 模式同时用于课程名与课程编号两列
        cur.execute(sql, (like, like))
        # 取全部行并逐行清洗后返回
        return [_clean(r) for r in cur.fetchall()]


# 全量已发布课程查询函数（供向量库/BM25 索引同步使用）
def get_all_published_courses() -> list[dict[str, Any]]:
    """所有已发布课程（用于向量库同步）。"""
    # 全量拉取仅用于向量库/BM25 索引构建（公开课程目录），与具体学生无关，故不绑定学号。
    # 只查询状态为 PUBLISHED（已发布）的课程，未发布课程不进检索语料
    sql = (
        "SELECT c.id, c.course_code, c.course_name, c.credit, c.hours, "
        "c.schedule, c.location, c.capacity, c.current_enrolled, c.status, "
        "st.real_name AS teacher_name "
        "FROM course c "
        "JOIN staff st ON st.id = c.teacher_id "
        "WHERE c.status = 'PUBLISHED'"
    )
    with _conn() as conn, conn.cursor() as cur:
        # 无参数，直接执行 SQL
        cur.execute(sql)
        # 取全部行并逐行清洗后返回
        return [_clean(r) for r in cur.fetchall()]
