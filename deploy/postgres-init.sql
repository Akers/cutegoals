-- =============================================================================
-- CuteGoals 2.0 — PostgreSQL 开发环境初始化脚本
-- =============================================================================
-- 用法：
--   以 PostgreSQL superuser 执行（如 postgres 或数据库所有者）：
--
--     psql -U postgres -h localhost -p 35432 -f deploy/postgres-init.sql
--
-- 或先连接 postgres 数据库后再执行：
--
--     \i deploy/postgres-init.sql
--
-- 说明：
--   1. 创建应用角色 cutegoals（如不存在）
--   2. 创建数据库 cutegoals，所有者为 cutegoals
--   3. 创建 schema 并授权给应用角色
--   4. 设置默认搜索路径
--
-- 注意：
--   如果数据库/用户已存在但权限不对，请连接数据库所有者或 superuser 后
--   执行：
--     ALTER DATABASE cutegoals OWNER TO cutegoals;
--     \c cutegoals
--     ALTER SCHEMA cutegoals OWNER TO cutegoals;
-- =============================================================================

-- 创建应用角色（如果尚不存在）
DO
$do$
BEGIN
    IF NOT EXISTS (
        SELECT FROM pg_catalog.pg_roles
        WHERE rolname = 'cutegoals'
    ) THEN
        CREATE ROLE cutegoals LOGIN PASSWORD 'cutegoals';
    END IF;
END
$do$;

-- 创建数据库（如已存在请删除或跳过，此语句需要 superuser 或 CREATEDB 权限）
CREATE DATABASE cutegoals
    WITH OWNER = cutegoals
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TEMPLATE = template0;

-- 切换到 cutegoals 数据库执行后续授权
\c cutegoals;

-- 创建应用 schema 并设置权限
CREATE SCHEMA IF NOT EXISTS cutegoals;
ALTER SCHEMA cutegoals OWNER TO cutegoals;
ALTER ROLE cutegoals SET search_path TO cutegoals, public;

GRANT ALL PRIVILEGES ON SCHEMA cutegoals TO cutegoals;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA cutegoals TO cutegoals;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA cutegoals TO cutegoals;

ALTER DEFAULT PRIVILEGES IN SCHEMA cutegoals GRANT ALL ON TABLES TO cutegoals;
ALTER DEFAULT PRIVILEGES IN SCHEMA cutegoals GRANT ALL ON SEQUENCES TO cutegoals;

-- 完成提示
\echo 'CuteGoals PostgreSQL 初始化完成。数据库: cutegoals，用户: cutegoals，schema: cutegoals'
