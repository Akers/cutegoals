#!/usr/bin/env bash
# =============================================================================
# CuteGoals 2.0 — 构建与部署管理脚本
# 用法：./deploy/build.sh <子命令> [选项]
# 子命令：help | build-server | build-web | build-docker | up | down | logs | backup | doctor
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

# 支持的平台
SUPPORTED_PLATFORMS=("linux/amd64" "linux/arm64")

# ── 颜色定义 ────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ── 辅助函数 ────────────────────────────────────────────────────────────────

die() {
    echo -e "${RED}[错误]${NC} $*" >&2
    exit 1
}

info() {
    echo -e "${GREEN}[信息]${NC} $*"
}

warn() {
    echo -e "${YELLOW}[警告]${NC} $*"
}

check_dependency() {
    local cmd="$1"
    local hint="${2:-}"
    if ! command -v "${cmd}" &>/dev/null; then
        die "缺少必需命令：${cmd}${hint:+ （${hint}）}"
    fi
}

load_env() {
    if [[ ! -f "${ENV_FILE}" ]]; then
        warn ".env 文件不存在（${ENV_FILE}）"
        warn "请从模板复制：cp deploy/.env.template .env"
        warn "部分操作需要 .env 文件才能正常运行"
        return 1
    fi
    set -a
    source "${ENV_FILE}"
    set +a
    return 0
}

# 校验平台是否受支持
validate_platform() {
    local platform="$1"
    for p in "${SUPPORTED_PLATFORMS[@]}"; do
        if [[ "$p" == "$platform" ]]; then
            return 0
        fi
    done
    echo -e "${RED}UNSUPPORTED_PLATFORM${NC}"
    echo "不受支持的平台：${platform}"
    echo "支持的平台：${SUPPORTED_PLATFORMS[*]}"
    exit 1
}

# 获取构建平台（参数指定或自动检测）
get_platform() {
    if [[ -n "${1:-}" ]]; then
        validate_platform "$1"
        echo "$1"
    else
        # 自动检测当前架构
        local arch
        arch="$(uname -m)"
        case "${arch}" in
            x86_64)  echo "linux/amd64" ;;
            aarch64) echo "linux/arm64" ;;
            arm64)   echo "linux/arm64" ;;
            *)
                die "不受支持的本地架构：${arch}，请使用 --platform 手动指定"
                ;;
        esac
    fi
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
  build-docker [--platform linux/amd64|linux/arm64] [--no-cache]
                             构建 Docker 镜像（支持多架构）
  up [--detach]              启动所有 Docker Compose 服务
  down                       停止并移除所有 Docker Compose 服务
  logs [服务名]              查看服务日志（默认查看所有）
  backup                     手动触发数据库备份
  doctor                     运行系统诊断（PostgreSQL 连接、磁盘、迁移状态）
  env-validate               校验 .env 配置是否有效

选项：
  --platform linux/amd64|linux/arm64  目标平台（默认自动检测）
  --no-cache                          禁用 Docker 构建缓存
  --skip-tests                        跳过测试
  --detach                            后台运行

常用示例：
  ./deploy/build.sh build-server            # 构建后端
  ./deploy/build.sh build-web               # 构建前端
  ./deploy/build.sh build-docker            # 构建 Docker 镜像（当前架构）
  ./deploy/build.sh build-docker --platform linux/arm64  # 构建 ARM64 镜像
  ./deploy/build.sh up --detach             # 后台启动所有服务
  ./deploy/build.sh logs server             # 查看后端日志
  ./deploy/build.sh backup                  # 手动备份
  ./deploy/build.sh doctor                  # 运行系统诊断

脚本使用中文输出，所有错误信息明确提示。
EOF
}

# ── 子命令实现 ──────────────────────────────────────────────────────────────

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

cmd_build_web() {
    check_dependency "node" "请安装 Node.js 20+"
    check_dependency "npm" "请安装 npm 10+"

    info "开始安装前端依赖..."
    (cd "${PROJECT_ROOT}/web" && npm ci)

    info "开始构建前端产物..."
    (cd "${PROJECT_ROOT}/web" && npm run build)
    info "前端构建完成！产物位于 web/dist/"
}

cmd_build_docker() {
    check_dependency "docker" "请安装 Docker 24+"

    local platform=""
    local docker_args=()

    # 解析参数
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --platform)
                if [[ -z "${2:-}" ]]; then
                    die "--platform 需要参数（如 linux/amd64）"
                fi
                platform="$2"
                validate_platform "$platform"
                shift 2
                ;;
            --no-cache)
                docker_args+=("--no-cache")
                info "禁用 Docker 构建缓存（--no-cache）"
                shift
                ;;
            *)
                die "未知选项：$1"
                ;;
        esac
    done

    # 如果未指定 platform，自动检测
    if [[ -z "${platform}" ]]; then
        platform="$(get_platform)"
        info "自动检测平台：${platform}"
    fi

    load_env || true
    local version="${APP_VERSION:-latest}"

    # 构建参数
    local build_args=("${docker_args[@]}")
    if [[ -n "${platform}" ]]; then
        build_args+=("--platform" "${platform}")
    fi

    # 构建后端镜像
    info "构建后端 Docker 镜像（${platform}）：mit-modelide-core-server:${version} ..."
    docker build \
        -f "${PROJECT_ROOT}/server/Dockerfile" \
        -t "mit-modelide-core-server:${version}" \
        "${build_args[@]}" \
        "${PROJECT_ROOT}"

    # 构建前端 web 镜像
    info "构建前端 Web Docker 镜像（${platform}）：mit-modelide-core-web:${version} ..."
    docker build \
        -f "${PROJECT_ROOT}/web/Dockerfile" \
        -t "mit-modelide-core-web:${version}" \
        "${build_args[@]}" \
        "${PROJECT_ROOT}"

    # 构建 nginx 镜像
    info "构建 Nginx Docker 镜像（${platform}）：mit-modelide-core-nginx:${version} ..."
    docker build \
        -f "${SCRIPT_DIR}/nginx/Dockerfile" \
        -t "mit-modelide-core-nginx:${version}" \
        "${build_args[@]}" \
        "${PROJECT_ROOT}"

    info "Docker 镜像构建完成！"
    info "  - mit-modelide-core-server:${version} (${platform})"
    info "  - mit-modelide-core-web:${version} (${platform})"
    info "  - mit-modelide-core-nginx:${version} (${platform})"
}

cmd_up() {
    check_dependency "docker" "请安装 Docker 24+"

    load_env || die ".env 文件缺失，请先创建并配置"

    local up_args=()
    if [[ "${1:-}" == "--detach" ]]; then
        up_args+=("-d")
    fi

    info "启动 Docker Compose 服务..."
    docker compose \
        --env-file "${ENV_FILE}" \
        -f "${COMPOSE_FILE}" \
        up "${up_args[@]}"

    info "服务已启动！"
    info "  前端：http://localhost:80"
    info "  健康检查：http://localhost:80/api/health"
}

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

cmd_backup() {
    check_dependency "docker" "请安装 Docker 24+"
    load_env || die ".env 文件缺失，请先创建并配置"

    info "手动触发数据库备份..."
    docker compose \
        --env-file "${ENV_FILE}" \
        -f "${COMPOSE_FILE}" \
        exec mit-modelide-core-backup \
        /usr/local/bin/backup.sh
}

cmd_doctor() {
    check_dependency "docker" "请安装 Docker 24+"
    load_env || true

    echo "========================================="
    echo "  CuteGoals 系统诊断 (doctor)"
    echo "========================================="
    echo ""

    local all_ok=true

    # 1. 检查 Docker 和 Compose
    echo "▶ 检查 Docker..."
    if docker info &>/dev/null; then
        echo "  ✓ Docker 运行中"
    else
        echo "  ✗ Docker 未运行或无法访问"
        all_ok=false
    fi

    # 2. 检查服务状态
    echo ""
    echo "▶ 检查服务状态..."
    local services=(
        "mit-modelide-core-postgres:PostgreSQL"
        "mit-modelide-core-redis:Redis"
        "mit-modelide-core-server:Server"
        "mit-modelide-core-nginx:Nginx"
    )

    for svc_entry in "${services[@]}"; do
        local svc_name="${svc_entry%%:*}"
        local svc_label="${svc_entry##*:}"
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "${svc_name}"; then
            local status
            status=$(docker inspect --format='{{.State.Health.Status}}' "${svc_name}" 2>/dev/null || echo "unknown")
            echo "  ${svc_label} (${svc_name}): ${status}"
            if [[ "${status}" != "healthy" ]]; then
                all_ok=false
            fi
        else
            echo "  ${svc_label} (${svc_name}): NOT_RUNNING"
            all_ok=false
        fi
    done

    # 3. PostgreSQL 连接检查
    echo ""
    echo "▶ PostgreSQL 连接检查..."
    if docker exec mit-modelide-core-postgres pg_isready -U "${PG_USER:-cutegoals}" -d "${PG_DATABASE:-cutegoals}" &>/dev/null 2>&1; then
        echo "  ✓ PostgreSQL 连接正常"
    else
        echo "  ✗ PostgreSQL 连接失败 (DEPENDENCY_UNHEALTHY)"
        all_ok=false
    fi

    # 4. 磁盘空间
    echo ""
    echo "▶ 磁盘空间检查..."
    local df_output
    df_output="$(df -h / | tail -1)"
    local avail
    avail="$(echo "${df_output}" | awk '{print $4}')"
    local used_pct
    used_pct="$(echo "${df_output}" | awk '{print $5}' | tr -d '%')"
    echo "  可用空间: ${avail}"
    if [[ "${used_pct}" -gt 90 ]]; then
        echo "  ⚠ 磁盘使用率超过 90% (${used_pct}%)"
        all_ok=false
    else
        echo "  ✓ 磁盘使用率正常 (${used_pct}%)"
    fi

    # 5. 健康检查端点
    echo ""
    echo "▶ 应用健康检查..."
    local health_status
    health_status="$(curl -sf http://localhost:8080/api/health 2>/dev/null || echo "UNREACHABLE")"
    if [[ "${health_status}" != "UNREACHABLE" ]]; then
        echo "  ✓ /api/health 响应正常"
    else
        # 尝试通过 Nginx
        health_status="$(curl -sf http://localhost/api/health 2>/dev/null || echo "UNREACHABLE")"
        if [[ "${health_status}" != "UNREACHABLE" ]]; then
            echo "  ✓ /api/health (via nginx) 响应正常"
        else
            echo "  ✗ 健康检查端点不可达"
            all_ok=false
        fi
    fi

    # 6. 迁移状态
    echo ""
    echo "▶ Flyway 迁移状态..."
    if docker exec mit-modelide-core-server wget -qO- http://localhost:8080/api/admin/health 2>/dev/null; then
        echo "  ✓ 管理员健康端点可访问"
    else
        echo "  - 管理员端点需认证（如需查看详细状态请登录）"
    fi

    echo ""
    if [[ "${all_ok}" == "true" ]]; then
        echo -e "${GREEN}✓ 所有检查通过${NC}"
    else
        echo -e "${YELLOW}⚠ 部分检查未通过，请查看上方警告${NC}"
    fi
}

cmd_env_validate() {
    load_env || die ".env 文件缺失"

    echo "校验 .env 配置..."
    local errors=0

    # 必填字段检查
    local required_vars=(
        "PG_PASSWORD:PostgreSQL 密码"
        "REDIS_PASSWORD:Redis 密码"
        "JWT_SECRET:JWT 签名密钥"
    )

    for entry in "${required_vars[@]}"; do
        local var="${entry%%:*}"
        local label="${entry##*:}"
        local val="${!var:-}"
        if [[ -z "${val}" ]]; then
            echo "  ✗ ${var}: ${label} 未设置"
            errors=$((errors + 1))
        elif [[ "${val}" == "changeit"* ]] || [[ "${val}" == "your-"* ]] || [[ "${var}" == "JWT_SECRET" && "${#val}" -lt 32 ]]; then
            echo "  ✗ ${var}: ${label} 为示例值或强度不足"
            errors=$((errors + 1))
        else
            echo "  ✓ ${var}: 已配置"
        fi
    done

    if [[ "${errors}" -gt 0 ]]; then
        echo ""
        die "CONFIG_INVALID: ${errors} 个配置项校验失败，请修正后重试"
    else
        echo ""
        echo -e "${GREEN}✓ 配置校验通过${NC}"
    fi
}

# ── 主入口 ──────────────────────────────────────────────────────────────────

main() {
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
            cmd_build_docker "$@"
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
        doctor)
            cmd_doctor
            ;;
        env-validate)
            cmd_env_validate
            ;;
        *)
            die "未知子命令：${cmd}\n使用 ./deploy/build.sh help 查看可用子命令"
            ;;
    esac
}

main "$@"
