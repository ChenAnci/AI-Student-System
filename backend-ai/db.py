"""MySQL 只读访问层：所有查询强制绑定学号，学生只能查自己的数据。"""
from contextlib import contextmanager
from datetime import datetime
from decimal import Decimal
from typing import Any, Iterator

import pymysql

from config import settings


@contextmanager
def _conn() -> Iterator[pymysql.connections.Connection]:
    conn = pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_db,
        charset="utf8mb4",
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
