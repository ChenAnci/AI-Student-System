"""MySQL 只读访问层：所有查询强制绑定学号，学生只能查自己的数据。"""
from contextlib import contextmanager
from datetime import datetime
from decimal import Decimal
from typing import Any, Iterator

import pymysql

from config import settings


@contextmanager
def _conn() -> Iterator[pymysql.connections.Connection]:
    # 每次操作新建连接、用完即关（上下文管理器）：本服务是低频只读查询，
    # 相比维护连接池，简单起见直接短连接，避免池内连接失效导致的脏读/复用问题。
    conn = pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_db,
        charset="utf8mb4",
        # 使用 DictCursor：查询结果以 dict 返回，字段名可直接引用，便于后续 JSON 序列化。
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        yield conn
    finally:
        conn.close()


def _clean(row: dict[str, Any] | None) -> dict[str, Any] | None:
    """把 Decimal / datetime 转成 JSON 可序列化类型。"""
    if row is None:
        return None
    out: dict[str, Any] = {}
    for k, v in row.items():
        if isinstance(v, Decimal):
            out[k] = float(v)
        elif isinstance(v, datetime):
            out[k] = v.strftime("%Y-%m-%d %H:%M:%S")
        else:
            out[k] = v
    return out


def get_student(student_no: str) -> dict[str, Any] | None:
    """学生基本信息（不含密码）。"""
    # 参数化 SQL：学号等用户可控值一律走 %s 占位符由驱动转义，
    # 从根上杜绝 SQL 注入（禁止字符串拼接 SQL）。
    sql = (
        "SELECT student_no, real_name, gender, phone, department, major, class_name, "
        "enrollment_year, total_earned_credits, required_credits, gpa, status "
        "FROM student WHERE student_no = %s"
    )
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(sql, (student_no,))
        return _clean(cur.fetchone())


def get_grades(student_no: str) -> list[dict[str, Any]]:
    """成绩单：课程名/学分/分数/mark/成绩审核状态。"""
    # 越权防线：WHERE 强制以 student_no 过滤，且该学号由上层（tools.py）取自已验签的
    # JWT claims，与请求参数解耦——即使客户端传了别人的学号也查不到他人成绩。
    # LEFT JOIN 成绩审核表：无审核记录时用 COALESCE 兜底为 'DRAFT'，保证行不丢。
    sql = (
        "SELECT c.course_name, c.credit, sc.score, sc.mark, "
        "COALESCE(ga.status, 'DRAFT') AS audit_status "
        "FROM student_course sc "
        "JOIN course c ON c.id = sc.course_id "
        "LEFT JOIN course_grade_audit ga ON ga.course_id = c.id "
        "JOIN student s ON s.id = sc.student_id "
        "WHERE s.student_no = %s "
        "ORDER BY sc.created_at"
    )
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(sql, (student_no,))
        return [_clean(r) for r in cur.fetchall()]


def get_schedule(student_no: str) -> list[dict[str, Any]]:
    """课表：已选课程的时间地点与教师。"""
    # 同样强制按学号过滤：学生只能看到自己已选课程，杜绝越权查他人课表。
    sql = (
        "SELECT c.course_name, c.credit, c.schedule, c.location, st.real_name AS teacher_name "
        "FROM student_course sc "
        "JOIN course c ON c.id = sc.course_id "
        "JOIN staff st ON st.id = c.teacher_id "
        "JOIN student s ON s.id = sc.student_id "
        "WHERE s.student_no = %s"
    )
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(sql, (student_no,))
        return [_clean(r) for r in cur.fetchall()]


def get_available_courses(student_no: str) -> list[dict[str, Any]]:
    """可选课程：已发布、未选、未满员。"""
    # 子查询按学号反查该生已选课程，主查询排除之：返回的是"对他个人而言"可选（未选）的课，
    # 因此也必须绑定学号——用别人的学号查会得到不准确的选课建议（但不会泄露他人隐私数据）。
    sql = (
        "SELECT c.id, c.course_code, c.course_name, c.credit, c.hours, "
        "c.schedule, c.location, c.capacity, c.current_enrolled, st.real_name AS teacher_name "
        "FROM course c "
        "JOIN staff st ON st.id = c.teacher_id "
        "WHERE c.status = 'PUBLISHED' "
        "AND c.current_enrolled < c.capacity "
        "AND c.id NOT IN ("
        "  SELECT course_id FROM student_course "
        "  WHERE student_id = (SELECT id FROM student WHERE student_no = %s)"
        ")"
    )
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(sql, (student_no,))
        return [_clean(r) for r in cur.fetchall()]


def _escape_like(keyword: str) -> str:
    """转义 LIKE 模式通配符，避免用户/LLM 输入中的 % _ \\ 扩大匹配范围（非 SQL 注入，仅限定返回集）。"""
    return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def get_course_detail(keyword: str) -> list[dict[str, Any]]:
    """按课程名/编号模糊查询课程详情。"""
    # LIKE 模糊查询属于公开课程目录数据，不涉及个人隐私，无需绑定学号。
    # 关键词仍走参数化传参（防注入），且通配符已转义（_escape_like），
    # 防止用户/LLM 输入里的 % _ 把匹配面扩到全表。
    sql = (
        "SELECT c.id, c.course_code, c.course_name, c.credit, c.hours, "
        "c.schedule, c.location, c.capacity, c.current_enrolled, c.status, "
        "st.real_name AS teacher_name, st.department AS teacher_department "
        "FROM course c "
        "JOIN staff st ON st.id = c.teacher_id "
        "WHERE c.course_name LIKE %s OR c.course_code LIKE %s"
    )
    like = f"%{_escape_like(keyword or '')}%"
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(sql, (like, like))
        return [_clean(r) for r in cur.fetchall()]


def get_all_published_courses() -> list[dict[str, Any]]:
    """所有已发布课程（用于向量库同步）。"""
    # 全量拉取仅用于向量库/BM25 索引构建（公开课程目录），与具体学生无关，故不绑定学号。
    sql = (
        "SELECT c.id, c.course_code, c.course_name, c.credit, c.hours, "
        "c.schedule, c.location, c.capacity, c.current_enrolled, c.status, "
        "st.real_name AS teacher_name "
        "FROM course c "
        "JOIN staff st ON st.id = c.teacher_id "
        "WHERE c.status = 'PUBLISHED'"
    )
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(sql)
        return [_clean(r) for r in cur.fetchall()]
