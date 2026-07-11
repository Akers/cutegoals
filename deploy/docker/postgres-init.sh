#!/bin/bash
# =============================================================================
# CuteGoals 2.0 — PostgreSQL 初始化脚本
# 在 Docker 首次启动时执行：创建 schema 并设置权限
# =============================================================================

set -e

# 创建应用 schema
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE SCHEMA IF NOT EXISTS "${PG_SCHEMA:-cutegoals}";
    ALTER SCHEMA "${PG_SCHEMA:-cutegoals}" OWNER TO "$POSTGRES_USER";
    ALTER ROLE "$POSTGRES_USER" SET search_path TO "${PG_SCHEMA:-cutegoals}", public;
EOSQL

echo "PostgreSQL initialization complete: schema '${PG_SCHEMA:-cutegoals}' created."
