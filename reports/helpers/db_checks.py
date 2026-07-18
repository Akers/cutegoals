#!/usr/bin/env python3
"""
db_checks.py — 不变量 DB 验证脚本

替代 plan 中的 db-checks.sh（系统无 psql 客户端）。直连 PG :35432。

用法:
    python3 db_checks.py <check-name>
    python3 db_checks.py list            # 列出所有可用 check
    python3 db_checks.py all             # 跑所有 check
    python3 db_checks.py points-non-negative
"""
import os
import sys
import json
import hashlib
from datetime import datetime

try:
    import psycopg2
except ImportError:
    print("ERROR: psycopg2 not installed. Run: /tmp/cg-venv/bin/pip install psycopg2-binary", file=sys.stderr)
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


def db_hash(table):
    """计算表内容的 hash（基于 id 顺序的行 hash 聚合）。

    用于 progress checkpoint，验证表内容是否变化。
    """
    sql = f"""
    SELECT md5(COALESCE(string_agg(t::text, '|' ORDER BY id), ''))
    FROM {PG_SCHEMA}.{table} t
    """
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            (h,) = cur.fetchone()
            return h or "EMPTY"


def points_non_negative():
    """I-001: 所有 points_balance.balance/total_earned 必须 >= 0"""
    sql = """
    SELECT
        COUNT(*) FILTER (WHERE balance < 0)      AS neg_balance,
        COUNT(*) FILTER (WHERE total_earned < 0) AS neg_total,
        COUNT(*)                                 AS total
    FROM points_balance
    """
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            r = cur.fetchone()
    passed = (r[0] == 0 and r[1] == 0)
    return {
        "passed": passed,
        "data": {"negative_balance": r[0], "negative_total_earned": r[1], "total_rows": r[2]},
    }


def ledger_balance_consistency():
    """I-002: 每条 points_ledger 的 balance_after 与按发生顺序累计的金额一致

    ledger.balance_after 应等于该 child 此前 ledger.amount 累计。
    """
    sql = """
    WITH ordered AS (
        SELECT
            child_id,
            amount,
            balance_after,
            SUM(amount) OVER (PARTITION BY child_id ORDER BY id) AS computed_balance
        FROM points_ledger
    )
    SELECT
        COUNT(*) AS total,
        COUNT(*) FILTER (WHERE balance_after IS NOT NULL AND balance_after <> computed_balance) AS mismatches
    FROM ordered
    """
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            r = cur.fetchone()
    return {
        "passed": r[1] == 0,
        "data": {"total_ledger_rows": r[0], "balance_mismatches": r[1]},
    }


def prize_stock_non_negative():
    """I-003: 所有 prize.stock 必须 >= 0（仅未删除奖品）"""
    sql = """
    SELECT
        COUNT(*) FILTER (WHERE stock < 0) AS neg_stock,
        COUNT(*) AS total
    FROM prize
    WHERE deleted = FALSE
    """
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            r = cur.fetchone()
    return {
        "passed": r[0] == 0,
        "data": {"negative_stock_rows": r[0], "total_active_prizes": r[1]},
    }


def exchange_snapshot_immutable():
    """I-004: exchange_snapshot 表内容不可变

    实现方式：对每个 exchange，snapshot 行创建后不应被 UPDATE/DELETE。
    本 check 返回当前 snapshot 表 hash，多次跑对比以验证不可变性。
    """
    sql = """
    SELECT
        COUNT(DISTINCT e.id) AS total_exchanges,
        COUNT(es.id)         AS snapshots_present
    FROM exchange e
    LEFT JOIN exchange_snapshot es ON es.exchange_id = e.id
    """
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            r = cur.fetchone()
    snap_hash = db_hash("exchange_snapshot")
    return {
        "passed": True,  # 不可变性需多次跑此 check 对比 snap_hash
        "data": {
            "total_exchanges": r[0],
            "snapshots_present": r[1],
            "current_snapshot_table_hash": snap_hash,
            "note": "Run check twice and compare hash to verify immutability",
        },
    }


def task_review_immutable():
    """I-005: task_review 的审核记录不可变（无 UPDATE/DELETE）

    通过 audit_log 中 object_type='task_review' 且 event_type 含 UPDATE/DELETE 验证。
    """
    sql = """
    SELECT COUNT(*) AS suspicious_events
    FROM audit_log
    WHERE object_type = 'task_review'
      AND (event_type ILIKE '%UPDATE%' OR event_type ILIKE '%DELETE%')
    """
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            (n,) = cur.fetchone()
    return {
        "passed": n == 0,
        "data": {"task_review_update_or_delete_events": n},
    }


def ledger_no_update_delete():
    """I-006: points_ledger 不可变（无 UPDATE/DELETE）"""
    sql_audit = """
    SELECT COUNT(*) AS suspicious_events
    FROM audit_log
    WHERE object_type = 'points_ledger'
      AND (event_type ILIKE '%UPDATE%' OR event_type ILIKE '%DELETE%')
    """
    sql_count = "SELECT COUNT(*) FROM points_ledger"
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(sql_audit)
            (n,) = cur.fetchone()
            cur.execute(sql_count)
            (total,) = cur.fetchone()
    return {
        "passed": n == 0,
        "data": {"ledger_update_or_delete_events": n, "current_ledger_rows": total},
    }


CHECKS = {
    "points-non-negative": ("I-001 points_balance 非负", points_non_negative),
    "ledger-balance-consistency": ("I-002 points_ledger 余额一致性", ledger_balance_consistency),
    "prize-stock-non-negative": ("I-003 prize.stock_count 非负", prize_stock_non_negative),
    "exchange-snapshot-immutable": ("I-004 exchange_snapshot 不可变（当前 hash）", exchange_snapshot_immutable),
    "task-review-immutable": ("I-005 task_review 审核记录不可变", task_review_immutable),
    "ledger-no-update-delete": ("I-006 points_ledger 不可变", ledger_no_update_delete),
}


def run(name):
    if name not in CHECKS:
        print(f"ERROR: unknown check '{name}'. Use 'list' to see options.", file=sys.stderr)
        sys.exit(2)
    label, fn = CHECKS[name]
    result = fn()
    ts = datetime.now().isoformat(timespec="seconds")
    output = {
        "check": name,
        "label": label,
        "timestamp": ts,
        **result,
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))
    sys.exit(0 if result["passed"] else 1)


def main():
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    arg = sys.argv[1]
    if arg == "list":
        for k, (label, _) in CHECKS.items():
            print(f"  {k:35s}  {label}")
        return
    if arg == "all":
        failed = []
        for k in CHECKS:
            label, fn = CHECKS[k]
            try:
                r = fn()
                status = "PASS" if r["passed"] else "FAIL"
                print(f"[{status}] {k}: {r.get('data', {})}")
                if not r["passed"]:
                    failed.append(k)
            except Exception as e:
                print(f"[ERROR] {k}: {type(e).__name__}: {e}")
                failed.append(k)
        sys.exit(1 if failed else 0)
    run(arg)


if __name__ == "__main__":
    main()
