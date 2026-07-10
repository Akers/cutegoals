#!/usr/bin/env bash
# =============================================================================
# CuteGoals 2.0 — 构建与部署管理脚本
# 用法：./deploy/build.sh <子命令> [选项]
# 子命令：help | build-server | build-web | build-docker | up | down | logs | backup
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

# ── 颜色定义 ────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ── 辅助函数 ────────────────────────────────────────────────────────────────

# 输出错误信息并退出
die() {
    echo -e "${RED}[错误]${NC} $*" >&2
    exit 1
}

# 输出信息
info() {
    echo -e "${GREEN}[信息]${NC} $*"
}

# 输出警告
warn() {
    echo -e "${YELLOW}[警告]${NC} $*"
}

# 检查是否安装了必需的命令
check_dependency() {
    local cmd="$1"
    local hint="${2:-}"
    if ! command -v "${cmd}" &>/dev/null; then
        die "缺少必需命令：${cmd}${hint:+ （${hint}）}"
    fi
}

# 检查 .env 文件是否存在并加载关键变量
load_env() {
    if [[ ! -f "${ENV_FILE}" ]]; then
        warn ".env 文件不存在（${ENV_FILE}）"
        warn "请从模板复制：cp deploy/.env.template .env"
        warn "部分操作需要 .env 文件才能正常运行"
        return 1
    fi
    # 仅加载不影响已有环境变量
    set -a
    source "${ENV_FILE}"
    set +a
    return 0
}

# ── 帮助信息 ────────────────────────────────────────────────────────────────

show_help() {
    cat <<'EOF'
CuteGoals 2.0 — 构建与部署管理脚本

用法：
  ./deploy/build.sh <子命令> [选项]

子命令：
  help                       显示此帮助信息
  build-server [--skip-tests] 构建后端 JAR 包
  build-web                  构建前端产物
  build-docker [--no-cache]  构建后端和前端 Docker 镜像
  up [--detach]              启动所有 Docker Compose 服务
  down                       停止并移除所有 Docker Compose 服务
  logs [服务名]              查看服务日志（默认查看所有）
  backup                     手动触发数据库备份

常用示例：
  ./deploy/build.sh build-server            # 构建后端
  ./deploy/build.sh build-web               # 构建前端
  ./deploy/build.sh build-docker            # 构建 Docker 镜像
  ./deploy/build.sh up --detach             # 后台启动所有服务
  ./deploy/build.sh logs server             # 查看后端日志
  ./deploy/build.sh backup                  # 手动备份

脚本使用中文输出，所有错误信息明确提示。缺少必填参数时脚本将报错并提示正确用法。
EOF
}

# ── 子命令实现 ──────────────────────────────────────────────────────────────

# 构建后端 JAR 包
cmd_build_server() {
    check_dependency "java" "请安装 JDK 21+"
    check_dependency "mvn" "请安装 Maven 3.9+"

    local mvn_args=("package" "-pl" "web" "-am" "-B" "-q")
    if [[ "${1:-}" == "--skip-tests" ]]; then
        mvn_args+=("-DskipTests")
        info "跳过测试（--skip-tests）"
    fi

    info "开始构建后端 JAR 包..."
    (cd "${PROJECT_ROOT}/server" && mvn "${mvn_args[@]}")
    info "后端构建完成！JAR 包位于 server/web/target/"
}

# 构建前端产物
cmd_build_web() {
    check_dependency "node" "请安装 Node.js 20+"
    check_dependency "npm" "请安装 npm 10+"

    info "开始安装前端依赖..."
    (cd "${PROJECT_ROOT}/web" && npm ci)

    info "开始构建前端产物..."
    (cd "${PROJECT_ROOT}/web" && npm run build)
    info "前端构建完成！产物位于 web/dist/"
}

# 构建 Docker 镜像
cmd_build_docker() {
    check_dependency "docker" "请安装 Docker 24+"

    local docker_args=()
    if [[ "${1:-}" == "--no-cache" ]]; then
        docker_args+=("--no-cache")
        info "禁用 Docker 构建缓存（--no-cache）"
    fi

    # 加载环境变量以读取 APP_VERSION
    load_env || true

    local version="${APP_VERSION:-latest}"

    info "构建后端 Docker 镜像：mit-modelide-core-server:${version} ..."
    docker build \
        -f "${SCRIPT_DIR}/docker/Dockerfile.server" \
        -t "mit-modelide-core-server:${version}" \
        "${docker_args[@]:+${docker_args[@]}}" \
        "${PROJECT_ROOT}"

    info "构建前端 Nginx Docker 镜像：mit-modelide-core-nginx:${version} ..."
    docker build \
        -f "${SCRIPT_DIR}/docker/Dockerfile.nginx" \
        -t "mit-modelide-core-nginx:${version}" \
        "${docker_args[@]:+${docker_args[@]}}" \
        "${PROJECT_ROOT}"

    info "Docker 镜像构建完成！"
    info "  - mit-modelide-core-server:${version}"
    info "  - mit-modelide-core-nginx:${version}"
}

# 启动所有服务
cmd_up() {
    check_dependency "docker" "请安装 Docker 24+"
    check_dependency "docker" "请安装 Docker Compose 2.x（已集成到 Docker CLI）"

    load_env || die ".env 文件缺失，请先创建并配置"

    local up_args=()
    if [[ "${1:-}" == "--detach" ]]; then
        up_args+=("-d")
    fi

    info "启动 Docker Compose 服务..."
    # 先构建镜像（如果不存在）
    if ! docker image inspect "mit-modelide-core-server:${APP_VERSION:-latest}" &>/dev/null; then
        warn "后端镜像不存在，自动构建..."
        cmd_build_docker
    fi
    if ! docker image inspect "mit-modelide-core-nginx:${APP_VERSION:-latest}" &>/dev/null; then
        warn "前端 Nginx 镜像不存在，自动构建..."
        cmd_build_docker
    fi

    docker compose \
        --env-file "${ENV_FILE}" \
        -f "${COMPOSE_FILE}" \
        up "${up_args[@]}"

    info "服务已启动！"
    info "  前端：http://localhost:80"
    info "  后端：http://localhost:8080"
    info "  健康检查：http://localhost:8080/api/health"
}

# 停止所有服务
cmd_down() {
    check_dependency "docker" "请安装 Docker 24+"

    load_env || true

    info "停止并移除 Docker Compose 服务..."
    docker compose \
        --env-file "${ENV_FILE}" \
        -f "${COMPOSE_FILE}" \
        down
    info "服务已停止。"
}

# 查看日志
cmd_logs() {
    check_dependency "docker" "请安装 Docker 24+"

    load_env || true

    local service="${1:-}"
    if [[ -n "${service}" ]]; then
        info "查看服务日志：${service}"
        docker compose \
            --env-file "${ENV_FILE}" \
            -f "${COMPOSE_FILE}" \
            logs -f "${service}"
    else
        info "查看所有服务日志..."
        docker compose \
            --env-file "${ENV_FILE}" \
            -f "${COMPOSE_FILE}" \
            logs -f
    fi
}

# 手动备份
cmd_backup() {
    check_dependency "docker" "请安装 Docker 24+"

    load_env || die ".env 文件缺失，请先创建并配置"

    info "手动触发数据库备份..."
    docker compose \
        --env-file "${ENV_FILE}" \
        -f "${COMPOSE_FILE}" \
        exec mit-modelide-core-mysql \
        mysqldump \
        --single-transaction \
        -u"${MYSQL_USER:-cutegoals}" \
        -p"${MYSQL_PASSWORD}" \
        "${MYSQL_DATABASE:-cutegoals}" \
        >"${PROJECT_ROOT}/backup_$(date +%Y%m%d_%H%M%S).sql"

    info "备份完成！文件位于项目根目录。"
}

# ── 主入口 ──────────────────────────────────────────────────────────────────

main() {
    # 无参数时显示帮助
    if [[ $# -eq 0 ]]; then
        warn "缺少子命令！请使用以下子命令："
        show_help
        exit 1
    fi

    local cmd="${1}"
    shift

    case "${cmd}" in
        help|-h|--help)
            show_help
            ;;
        build-server)
            cmd_build_server "${1:-}"
            ;;
        build-web)
            cmd_build_web
            ;;
        build-docker)
            cmd_build_docker "${1:-}"
            ;;
        up)
            cmd_up "${1:-}"
            ;;
        down)
            cmd_down
            ;;
        logs)
            cmd_logs "${1:-}"
            ;;
        backup)
            cmd_backup
            ;;
        *)
            die "未知子命令：${cmd}\n使用 ./deploy/build.sh help 查看可用子命令"
            ;;
    esac
}

main "$@"
