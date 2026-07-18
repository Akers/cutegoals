#!/usr/bin/env python3
"""
partial_reset.py — 模块级数据清理脚本

用途：在 Round-1 测试过程中，当某个用例污染了下一用例所需的数据状态时，
      清理相关模块的数据（不重置 token/账户/家庭结构）。

⚠️ 仅在 Round-1 期间使用；Round-2（最终回归）前必须再次执行完整重置。

用法:
    python3 partial_reset.py list
    python3 partial_reset.py exchange        # 清理 exchange + exchange_snapshot
    python3 partial_reset.py points          # 清理 points_ledger + 重置 balance
    python3 partial_reset.py task-review     # 清理 task_review + task_attempt
    python3 partial_reset.py task            # 清理 task_assignment + snapshot
    python3 partial_reset.py audit           # 仅清理 audit_log
"""
import os
import sys
import json
from datetime import datetime

try:
    import psycopg2
except ImportError:
    print("ERROR: psycopg2 not installed.", file=sys.stderr)
    sys.exit(2)

PG_HOST = os.environ.get("PG_HOST", "localhost")
PG_PORT = int(os.environ.get("PG_PORT", "35432"))
PG_DB = os.environ.get("PG_DATABASE", "cutegoals")
PG_USER = os.environ.get("PG_USER", "cutegoals")
PG_PASSWORD = os.environ.get("PG_PASSWORD", "cutegoals")
PG_SCHEMA = os.environ.get("PG_SCHEMA", "cutegoals")


def connect():
    return psycopg2.connect(
        host=PG_HOST, port=PG_PORT, dbname=PG_DB,
        user=PG_USER, password=PG_PASSWORD,
        options=f"-c search_path={PG_SCHEMA}",
        connect_timeout=5,
    )


def reset_exchange():
    """清理所有兑换记录（保留 prize/blind_box 配置）"""
    return (
        ["exchange", "exchange_snapshot"],
        "TRUNCATE TABLE exchange_snapshot, exchange CASCADE;",
        ["TRUNCATED exchange + exchange_snapshot"],
    )


def reset_points():
    """清空积分流水并重置 balance（保留 child account）"""
    return (
        ["points_ledger", "points_balance"],
        """
        TRUNCATE TABLE points_ledger CASCADE;
        UPDATE points_balance SET balance = 0, total_earned = 0, version = version + 1, updated_at = NOW();
        """,
        ["TRUNCATED points_ledger", "Reset all points_balance to 0"],
    )


def reset_task_review():
    """清理审核记录（保留 task_assignment 结构）"""
    return (
        ["task_review", "task_attempt"],
        """
        TRUNCATE TABLE task_review, task_attempt CASCADE;
        """,
        ["TRUNCATED task_review + task_attempt"],
    )


def reset_task():
    """清理任务分配（保留 template）"""
    return (
        ["task_assignment", "task_assignment_snapshot", "task_attempt", "task_review"],
        "TRUNCATE TABLE task_review, task_attempt, task_assignment_snapshot, task_assignment CASCADE;",
        ["TRUNCATED task_assignment chain (templates kept)"],
    )


def reset_audit():
    """清空审计日志（仅用于验证审计功能的基线）"""
    return (
        ["audit_log"],
        "TRUNCATE TABLE audit_log CASCADE;",
        ["TRUNCATED audit_log"],
    )


RESETS = {
    "exchange": reset_exchange,
    "points": reset_points,
    "task-review": reset_task_review,
    "task": reset_task,
    "audit": reset_audit,
}


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    arg = sys.argv[1]
    if arg == "list":
        for k, fn in RESETS.items():
            print(f"  {k:15s}  {fn.__doc__.strip().split(chr(10))[0]}")
        return
    if arg not in RESETS:
        print(f"ERROR: unknown reset '{arg}'", file=sys.stderr)
        sys.exit(2)
    affected_tables, sql, messages = RESETS[arg]()
    ts = datetime.now().isoformat(timespec="seconds")
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
        conn.commit()
        counts = {}
        with conn.cursor() as cur:
            for t in affected_tables:
                cur.execute(f"SELECT count(*) FROM {PG_SCHEMA}.{t}")
                (n,) = cur.fetchone()
                counts[t] = n
    print(json.dumps({
        "reset": arg,
        "timestamp": ts,
        "affected_tables": counts,
        "messages": messages,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
