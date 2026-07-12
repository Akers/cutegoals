#!/usr/bin/env bash

set -euo pipefail

# CuteGoals 2.0 — 后端开发启动脚本（Linux / macOS）
# 功能：交互式输入中间件地址、设置环境变量、启动 Spring Boot 并实时输出日志

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${REPO_ROOT}/server"
ENV_FILE="${REPO_ROOT}/.env.dev"

# 颜色
COLOR_INFO='\033[0;34m'
COLOR_OK='\033[0;32m'
COLOR_WARN='\033[1;33m'
COLOR_ERR='\033[0;31m'
COLOR_RESET='\033[0m'

info() { echo -e "${COLOR_INFO}[INFO] $*${COLOR_RESET}"; }
ok() { echo -e "${COLOR_OK}[OK] $*${COLOR_RESET}"; }
warn() { echo -e "${COLOR_WARN}[WARN] $*${COLOR_RESET}"; }
err() { echo -e "${COLOR_ERR}[ERR] $*${COLOR_RESET}" >&2; }

print_header() {
    echo ""
    echo "╔══════════════════════════════════════════════════════╗"
    echo "║       CuteGoals 2.0 — 后端开发启动脚本（Linux）       ║"
    echo "╚══════════════════════════════════════════════════════╝"
    echo ""
}

# 检查命令是否存在
check_command() {
    local cmd="$1"
    local min_version="$2"
    if ! command -v "${cmd}" > /dev/null 2>&1; then
        err "${cmd} 未安装，请先安装 ${cmd} ${min_version}+"
        exit 1
    fi
    ok "${cmd} 已安装"
}

check_java_version() {
    local version
    version=$(java -version 2>&1 | grep -oE 'version "[0-9]+' | head -1 | sed 's/version "//')
    if [ "${version}" -lt 21 ]; then
        err "JDK 版本过低：${version}，需要 JDK 21+"
        exit 1
    fi
    ok "JDK 版本：${version}"
}

check_maven_version() {
    local version
    version=$(mvn -version 2>&1 | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
    ok "Maven 版本：${version}"
}

check_postgres() {
    local host="${1:-localhost}"
    local port="${2:-35432}"
    if command -v pg_isready > /dev/null 2>&1; then
        if ! pg_isready -h "${host}" -p "${port}" > /dev/null 2>&1; then
            warn "PostgreSQL ${host}:${port} 未响应，请先启动数据库"
            info "手动初始化：psql -U postgres -h ${host} -p ${port} -f deploy/postgres-init.sql"
            info "Docker 启动：docker compose -f deploy/docker-compose.yml up -d mit-modelide-core-postgres mit-modelide-core-redis"
        else
            ok "PostgreSQL ${host}:${port} 可连接"
        fi
    fi
}

# 加载已有配置
load_existing_config() {
    if [ -f "${ENV_FILE}" ]; then
        info "检测到历史开发配置 ${ENV_FILE}，将用作默认值"
        # shellcheck source=/dev/null
        source "${ENV_FILE}"
    fi
}

# 读取输入，带默认值
prompt() {
    local var_name="$1"
    local message="$2"
    local default_value="$3"
    local input

    if [ -n "${default_value}" ]; then
        echo -e "${COLOR_INFO}${message} [默认: ${default_value}]:${COLOR_RESET}"
    else
        echo -e "${COLOR_INFO}${message}:${COLOR_RESET}"
    fi
    read -r input

    if [ -z "${input}" ] && [ -n "${default_value}" ]; then
        input="${default_value}"
    fi

    eval "${var_name}='${input}'"
}

prompt_secret() {
    local var_name="$1"
    local message="$2"
    local default_value="$3"
    local input

    if [ -n "${default_value}" ]; then
        echo -e "${COLOR_INFO}${message} [默认: ${default_value}]:${COLOR_RESET}"
    else
        echo -e "${COLOR_INFO}${message}:${COLOR_RESET}"
    fi
    read -rs input
    echo ""

    if [ -z "${input}" ] && [ -n "${default_value}" ]; then
        input="${default_value}"
    fi

    eval "${var_name}='${input}'"
}

# 生成随机 JWT secret
generate_jwt_secret() {
    openssl rand -base64 32
}

# 保存配置到 .env.dev
save_config() {
    cat > "${ENV_FILE}" <<EOF
# CuteGoals 2.0 — 开发环境配置（由 start-dev.sh 生成）
# 此文件仅用于本地开发，请勿提交到 Git

PG_HOST=${PG_HOST}
PG_PORT=${PG_PORT}
PG_DATABASE=${PG_DATABASE}
PG_USER=${PG_USER}
PG_PASSWORD=${PG_PASSWORD}
PG_SCHEMA=${PG_SCHEMA}

REDIS_HOST=${REDIS_HOST}
REDIS_PORT=${REDIS_PORT}
REDIS_PASSWORD=${REDIS_PASSWORD}

CUTEGOALS_JWT_SECRET=${CUTEGOALS_JWT_SECRET}
APP_PRODUCTION=false
PORT=${PORT}
EOF
    ok "配置已保存到 ${ENV_FILE}"
}

# 主流程
main() {
    print_header

    check_command java "21"
    check_java_version
    check_command mvn "3.9"
    check_maven_version

    load_existing_config

    echo ""
    info "请输入 PostgreSQL 连接信息（回车使用默认值）"
    prompt PG_HOST "PostgreSQL 主机" "${PG_HOST:-localhost}"
    prompt PG_PORT "PostgreSQL 端口" "${PG_PORT:-35432}"
    prompt PG_DATABASE "PostgreSQL 数据库名" "${PG_DATABASE:-cutegoals}"
    prompt PG_USER "PostgreSQL 用户名" "${PG_USER:-cutegoals}"
    prompt_secret PG_PASSWORD "PostgreSQL 密码" "${PG_PASSWORD:-cutegoals}"
    prompt PG_SCHEMA "PostgreSQL Schema" "${PG_SCHEMA:-cutegoals}"
    check_postgres "${PG_HOST:-localhost}" "${PG_PORT:-35432}"

    echo ""
    info "请输入 Redis 连接信息（回车使用默认值）"
    prompt REDIS_HOST "Redis 主机" "${REDIS_HOST:-localhost}"
    prompt REDIS_PORT "Redis 端口" "${REDIS_PORT:-6379}"
    prompt_secret REDIS_PASSWORD "Redis 密码" "${REDIS_PASSWORD:-}"

    echo ""
    info "请输入应用配置"
    if [ -z "${CUTEGOALS_JWT_SECRET:-}" ]; then
        CUTEGOALS_JWT_SECRET=$(generate_jwt_secret)
        warn "已自动生成 JWT_SECRET，如要固定可手动修改 .env.dev"
    fi
    prompt_secret CUTEGOALS_JWT_SECRET "JWT Secret" "${CUTEGOALS_JWT_SECRET}"
    prompt PORT "后端服务端口" "${PORT:-8080}"

    echo ""
    read -rp "是否将本次配置保存到 .env.dev 供下次使用？ [Y/n]: " save_choice
    if [ -z "${save_choice}" ] || [[ "${save_choice}" =~ ^[Yy]$ ]]; then
        save_config
    fi

    echo ""
    info "正在启动 CuteGoals 2.0 后端服务..."
    info "服务启动后，下方将实时输出日志。按 Ctrl+C 停止服务。"
    echo ""

    export PG_HOST PG_PORT PG_DATABASE PG_USER PG_PASSWORD PG_SCHEMA
    export REDIS_HOST REDIS_PORT REDIS_PASSWORD
    export CUTEGOALS_JWT_SECRET APP_PRODUCTION=false PORT

    cd "${SERVER_DIR}"
    exec mvn -pl web -am spring-boot:run -DskipTests \
        -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8"
}

# 支持 --logs 参数查看日志
if [ "${1:-}" == "--logs" ] || [ "${1:-}" == "-l" ]; then
    if [ -f "${REPO_ROOT}/logs/cutegoals-dev.log" ]; then
        tail -f "${REPO_ROOT}/logs/cutegoals-dev.log"
    else
        err "未找到日志文件：${REPO_ROOT}/logs/cutegoals-dev.log"
        exit 1
    fi
else
    main
fi
