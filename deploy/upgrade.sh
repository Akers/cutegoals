#!/usr/bin/env bash
# =============================================================================
# CuteGoals 2.0 — 升级脚本
# 升级前强制备份 + Flyway 迁移 + 健康检查 + 失败时从备份恢复
# 用法：deploy/upgrade.sh <目标版本> [--dry-run]
# 注意：系统不承诺支持原地降级
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# ── 配置 ──────────────────────────────────────────────────────────────────
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
BACKUP_SCRIPT="${SCRIPT_DIR}/backup/backup.sh"
RESTORE_SCRIPT="${SCRIPT_DIR}/restore.sh"
BACKUP_DIR="${BACKUP_DIR:-/backup}"
SERVER_URL="${SERVER_URL:-http://mit-modelide-core-server:8080}"
UPGRADE_LOG="${BACKUP_DIR}/upgrade.log"

# ── 常量 ──────────────────────────────────────────────────────────────────
EC_SUCCESS=0
EC_PRE_UPGRADE_BACKUP_FAILED=1
EC_MIGRATION_FAILED=2
EC_UPGRADE_HEALTHCHECK_FAILED=3
EC_DOWNGRADE_NOT_SUPPORTED=4

# ── 状态 ──────────────────────────────────────────────────────────────────
DRY_RUN=false
TARGET_VERSION=""
UPGRADE_START_TIME=$(date -u +"%Y%m%d_%H%M%S")
CURRENT_VERSION=""

# ── 参数解析 ──────────────────────────────────────────────────────────────

if [[ $# -eq 0 ]]; then
    echo "用法: $0 <目标版本> [--dry-run]"
    echo "  目标版本: Docker 镜像标签（如 2.1.0）"
    echo "  --dry-run: 仅校验，不执行实际升级"
    exit 1
fi

TARGET_VERSION="$1"
shift

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# ── 辅助函数 ──────────────────────────────────────────────────────────────

log() {
    local level="$1"
    local message="$2"
    echo "[${level}] $(date +%Y%m%d_%H%M%S) ${message}" | tee -a "${UPGRADE_LOG}"
}

die() {
    log "ERROR" "$1"
    exit "${EC_MIGRATION_FAILED}"
}

load_env() {
    if [[ -f "${ENV_FILE}" ]]; then
        set -a
        source "${ENV_FILE}"
        set +a
    fi
}

get_current_version() {
    local version
    version=$(curl -sf "${SERVER_URL}/api/admin/health" 2>/dev/null | \
        python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('version','unknown'))" 2>/dev/null || \
        echo "unknown")
    echo "${version}"
}

# ── 主流程 ────────────────────────────────────────────────────────────────

main() {
    load_env
    mkdir -p "${BACKUP_DIR}"

    log "INFO" "========================================"
    log "INFO" "  CuteGoals 版本升级"
    log "INFO" "========================================"
    log "INFO" "目标版本: ${TARGET_VERSION}"
    log "INFO" "Dry-Run: ${DRY_RUN}"

    # 获取当前版本
    CURRENT_VERSION=$(get_current_version)
    log "INFO" "当前版本: ${CURRENT_VERSION}"

    # 检查降级（使用 sort -V 进行语义化版本比较）
    if [[ "${CURRENT_VERSION}" != "unknown" ]]; then
        # 版本相同直接跳过
        if [[ "${CURRENT_VERSION}" == "${TARGET_VERSION}" ]]; then
            log "INFO" "目标版本与当前版本相同，跳过"
            exit "${EC_SUCCESS}"
        fi
        # 使用 sort -V 比较版本（正确处理 10.0.0 > 9.0.0 等情况）
        local lower_version
        lower_version=$(printf '%s\n%s\n' "${CURRENT_VERSION}" "${TARGET_VERSION}" | sort -V | head -1)
        if [[ "${lower_version}" == "${TARGET_VERSION}" ]]; then
            # CURRENT > TARGET → 降级
            log "WARN" "检测到降级请求: ${CURRENT_VERSION} → ${TARGET_VERSION}"
            echo "DOWNGRADE_NOT_SUPPORTED: 系统不支持原地降级。"
            echo "如需回退，请使用 deploy/restore.sh 从升级前备份恢复。"
            echo ""
            echo "当前版本: ${CURRENT_VERSION}"
            echo "目标版本: ${TARGET_VERSION}"
            exit "${EC_DOWNGRADE_NOT_SUPPORTED}"
        fi
    fi

    # ── 步骤 1：升级前备份 ──────────────────────────────────────────────────
    log "INFO" "=== 步骤 1: 升级前强制备份 ==="
    log "INFO" "备份路径: ${BACKUP_DIR}"

    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] 跳过升级前备份"
    else
        if [[ -x "${BACKUP_SCRIPT}" ]]; then
            if ! bash "${BACKUP_SCRIPT}"; then
                log "ERROR" "升级前备份失败！"
                echo "PRE_UPGRADE_BACKUP_FAILED: 无法创建升级前备份，中止升级"
                echo "当前版本和数据保持运行前状态。"
                exit "${EC_PRE_UPGRADE_BACKUP_FAILED}"
            fi
            log "INFO" "升级前备份成功"
        else
            log "ERROR" "备份脚本不存在: ${BACKUP_SCRIPT}"
            echo "PRE_UPGRADE_BACKUP_FAILED: 备份脚本不可用"
            exit "${EC_PRE_UPGRADE_BACKUP_FAILED}"
        fi
    fi

    # ── 步骤 2：获取最新的备份用于回滚 ─────────────────────────────────────
    local rollback_backup=""
    if [[ "${DRY_RUN}" != "true" ]]; then
        rollback_backup=$(find "${BACKUP_DIR}" -name "cutegoals_backup_*.tar.gz" -type f 2>/dev/null | sort | tail -1)
        if [[ -z "${rollback_backup}" ]]; then
            log "WARN" "未找到备份文件，回滚可能不可用"
        else
            log "INFO" "回滚备份: $(basename "${rollback_backup}")"
        fi
    fi

    # ── 步骤 3：拉取新版本镜像 ──────────────────────────────────────────────
    log "INFO" "=== 步骤 2: 拉取新版本镜像 ==="
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] docker pull mit-modelide-core-server:${TARGET_VERSION}"
    else
        log "INFO" "拉取 server 镜像: mit-modelide-core-server:${TARGET_VERSION}..."
        docker pull "mit-modelide-core-server:${TARGET_VERSION}" 2>/dev/null || \
            log "WARN" "无法拉取镜像，尝试本地构建"
    fi

    # ── 步骤 4：更新容器镜像 ────────────────────────────────────────────────
    log "INFO" "=== 步骤 3: 更新服务 ==="
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] 更新 docker compose 服务镜像版本"
    else
        # 更新环境变量中的版本
        export APP_VERSION="${TARGET_VERSION}"

        # 重新创建 server 容器（触发 Flyway 迁移）
        log "INFO" "重新创建 server 容器..."
        docker compose \
            --env-file "${ENV_FILE}" \
            -f "${COMPOSE_FILE}" \
            up -d mit-modelide-core-server \
            --force-recreate 2>&1 | tee -a "${UPGRADE_LOG}"
    fi

    # ── 步骤 5：等待迁移完成 + 健康检查 ─────────────────────────────────────
    log "INFO" "=== 步骤 4: 等待迁移与健康检查 ==="
    local health_retries=30
    local health_wait=10
    local health_ok=false

    for i in $(seq 1 "${health_retries}"); do
        if [[ "${DRY_RUN}" == "true" ]]; then
            log "INFO" "[DRY-RUN] 模拟健康检查通过"
            health_ok=true
            break
        fi

        local http_code
        http_code=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/api/health" 2>/dev/null || echo "000")

        if [[ "${http_code}" == "200" ]]; then
            log "INFO" "健康检查通过 (尝试 ${i}/${health_retries})"
            health_ok=true
            break
        fi

        # 检查响应体中的迁移错误
        local response
        response=$(curl -sf "${SERVER_URL}/api/health" 2>/dev/null || echo "")
        if echo "${response}" | grep -q "MIGRATION_FAILED\|CONFIG_INVALID"; then
            log "ERROR" "迁移或配置错误！"
            health_ok=false
            break
        fi

        log "INFO" "等待服务就绪... (${i}/${health_retries}, HTTP ${http_code})"
        sleep "${health_wait}"
    done

    # ── 步骤 6：检查结果 ────────────────────────────────────────────────────
    if [[ "${health_ok}" != "true" ]]; then
        log "ERROR" "升级后健康检查失败！"

        if [[ -n "${rollback_backup}" ]] && [[ "${DRY_RUN}" != "true" ]]; then
            log "INFO" "启动回滚流程..."
            echo ""
            echo "MIGRATION_FAILED: 迁移或健康检查失败，正在从备份恢复..."

            # 恢复旧版本镜像
            local old_version="${CURRENT_VERSION}"
            if [[ "${old_version}" != "unknown" ]]; then
                export APP_VERSION="${old_version}"
                docker compose \
                    --env-file "${ENV_FILE}" \
                    -f "${COMPOSE_FILE}" \
                    up -d mit-modelide-core-server \
                    --force-recreate 2>&1 | tee -a "${UPGRADE_LOG}" || true
            fi

            # 从备份恢复数据
            bash "${RESTORE_SCRIPT}" "${rollback_backup}" || \
                log "ERROR" "自动回滚失败，需要手动恢复。备份文件: ${rollback_backup}"

            echo "回滚完成。请检查服务状态。"
        else
            echo "MIGRATION_FAILED: 升级失败，请手动恢复。"
            echo "备份文件: ${rollback_backup:-无}"
            echo ""
            echo "恢复命令:"
            echo "  bash deploy/restore.sh ${rollback_backup:-<备份文件>}"
        fi

        exit "${EC_UPGRADE_HEALTHCHECK_FAILED}"
    fi

    # ── 步骤 7：更新其他服务 ────────────────────────────────────────────────
    log "INFO" "=== 步骤 5: 更新其他服务 ==="
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] 跳过其他服务更新"
    else
        log "INFO" "重新创建 nginx 容器..."
        docker compose \
            --env-file "${ENV_FILE}" \
            -f "${COMPOSE_FILE}" \
            up -d mit-modelide-core-nginx \
            --force-recreate 2>&1 | tee -a "${UPGRADE_LOG}" || true
    fi

    # ── 完成 ────────────────────────────────────────────────────────────────
    local new_version
    new_version=$(get_current_version)

    log "INFO" "========================================"
    log "INFO" "  升级完成！"
    log "INFO" "========================================"
    log "INFO" "  起始版本: ${CURRENT_VERSION}"
    log "INFO" "  目标版本: ${TARGET_VERSION}"
    log "INFO" "  当前版本: ${new_version}"
    log "INFO" "========================================"

    exit "${EC_SUCCESS}"
}

main
