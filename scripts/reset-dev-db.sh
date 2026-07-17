#!/usr/bin/env bash
# =============================================================================
# CuteGoals 2.0 — 本地开发数据库重置脚本（最小重置）
# -----------------------------------------------------------------------------
# 作用：
#   1) TRUNCATE 所有业务表（保留 schema 和 flyway_schema_history）
#   2) 重置自增 ID（RESTART IDENTITY）
#   3) 重新 seed 一条已知明文的 dev initialization_token
#
# 重置后：
#   - /api/instance/status 立即返回 UNINITIALIZED
#   - 可在 /admin/init 页面使用 DEV_INIT_TOKEN 完成初始化
#   - 无需重启后端服务
#
# 使用方式（任选其一）：
#   bash scripts/reset-dev-db.sh                 # 当前用户已在 docker 组
#   sudo bash scripts/reset-dev-db.sh            # 通过 sudo 提权
# =============================================================================
set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-sino-cms-postgres}"
PG_USER="${PG_USER:-cutegoals}"
PG_DB="${PG_DB:-cutegoals}"

# dev 模式的预定义明文 token（仅本地测试用）
#   明文: cutegoals-dev-init-2026
#   SHA-256(明文): 3569396aef8c974e60441f4815afe427ad222f3ac6e65fea06ac506483ca2c32
DEV_INIT_TOKEN="cutegoals-dev-init-2026"
DEV_INIT_TOKEN_HASH="3569396aef8c974e60441f4815afe427ad222f3ac6e65fea06ac506483ca2c32"

# 检测 docker 访问方式
if docker ps >/dev/null 2>&1; then
    DOCKER_PREFIX=(docker)
elif command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
    DOCKER_PREFIX=(sudo docker)
else
    echo "错误：无法访问 docker，请手动执行：" >&2
    echo "  sudo usermod -aG docker \$USER && newgrp docker" >&2
    echo "或直接用 sudo 运行本脚本：" >&2
    echo "  sudo bash scripts/reset-dev-db.sh" >&2
    exit 1
fi

echo "==> 使用 docker 前缀: ${DOCKER_PREFIX[*]}"
echo "==> 目标容器: ${PG_CONTAINER}"
echo

# 检查容器是否存在
if ! "${DOCKER_PREFIX[@]}" ps --filter "name=^${PG_CONTAINER}$" --format '{{.Names}}' | grep -q "^${PG_CONTAINER}$"; then
    echo "错误：容器 ${PG_CONTAINER} 不存在或未运行" >&2
    echo >&2
    echo "当前正在运行的 postgres 相关容器：" >&2
    "${DOCKER_PREFIX[@]}" ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}' | grep -iE 'postgres|NAMES' >&2 || true
    echo >&2
    echo "可通过环境变量覆盖：" >&2
    echo "  sudo PG_CONTAINER=<实际容器名> bash scripts/reset-dev-db.sh" >&2
    exit 1
fi

# 执行 TRUNCATE + seed
"${DOCKER_PREFIX[@]}" exec -i "${PG_CONTAINER}" \
    psql -U "${PG_USER}" -d "${PG_DB}" -v ON_ERROR_STOP=1 <<SQL
BEGIN;

-- 注意：不使用 RESTART IDENTITY，因为某些序列（如 account_id_seq1）的 owner
-- 可能不是当前用户，RESTART IDENTITY 会因权限不足而失败。
-- TRUNCATE 本身只需要表 owner 权限。
TRUNCATE TABLE
    account, role_binding, initialization_token, session, refresh_token,
    login_rate_limit, family, family_member, child_profile, parent_invitation,
    device_binding, task_template, task_difficulty, task_recurrence_rule,
    task_assignment, task_assignment_snapshot, task_attempt, task_review,
    points_ledger, points_balance, prize, blind_box_pool, blind_box_item,
    exchange, exchange_snapshot, instance_config, audit_log, backup_run,
    recovery_drill
CASCADE;

-- 尽力重置自增序列（对无权限的序列静默跳过，不影响整体事务）
DO \$\$
DECLARE
    s RECORD;
BEGIN
    FOR s IN
        SELECT c.relname AS seq_name
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relkind = 'S' AND n.nspname = current_schema()
    LOOP
        BEGIN
            EXECUTE format('ALTER SEQUENCE %I RESTART WITH 1', s.seq_name);
        EXCEPTION WHEN insufficient_privilege THEN
            NULL;
        END;
    END LOOP;
END \$\$;

-- 重新 seed dev initialization_token（24h 有效）
INSERT INTO initialization_token (token_hash, consumed, expires_at, created_at)
VALUES (
    '${DEV_INIT_TOKEN_HASH}',
    FALSE,
    NOW() + INTERVAL '24 hours',
    NOW()
);

SELECT 'instance_status after reset:' AS info;
SELECT
    CASE WHEN COUNT(*) = 0 THEN 'UNINITIALIZED (no consumed token)'
         ELSE 'INITIALIZED' END AS instance_status,
    COUNT(*) FILTER (WHERE consumed)            AS consumed_tokens,
    COUNT(*) FILTER (WHERE NOT consumed)        AS valid_unconsumed_tokens
FROM initialization_token;

COMMIT;
SQL

echo
echo "==> 重置完成"
echo "    DEV_INIT_TOKEN (明文，仅显示一次): ${DEV_INIT_TOKEN}"
echo "    打开 /admin/init 输入此 token 即可重新初始化"
