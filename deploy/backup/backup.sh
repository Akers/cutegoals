#!/usr/bin/env bash
# =============================================================================
# CuteGoals 2.0 — 数据库备份脚本
# 使用 pg_dump 创建 PostgreSQL 一致性备份
# 备份文件打包为 tar.gz，带时间戳、校验和和应用版本信息
# 备份状态写入 backup_run 表
# 用法：backup.sh [--dry-run]
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── 配置 ──────────────────────────────────────────────────────────────────
PG_HOST="${PG_HOST:-mit-modelide-core-postgres}"
PG_PORT="${PG_PORT:-5432}"
PG_DATABASE="${PG_DATABASE:-cutegoals}"
PG_USER="${PG_USER:-cutegoals}"
PG_PASSWORD="${PG_PASSWORD:-}"
PG_SCHEMA="${PG_SCHEMA:-cutegoals}"
BACKUP_DIR="${BACKUP_DIR:-/backup}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
RETENTION_WEEKS="${BACKUP_RETENTION_WEEKS:-4}"
RETENTION_MONTHS="${BACKUP_RETENTION_MONTHS:-3}"
APP_VERSION="${APP_VERSION:-latest}"
SERVER_URL="${SERVER_URL:-http://mit-modelide-core-server:8080}"

# ── 日志 ──────────────────────────────────────────────────────────────────
LOG_FILE="${BACKUP_DIR}/backup.log"
DRY_RUN=false
START_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/cutegoals_backup_${TIMESTAMP}.sql"
BACKUP_ARCHIVE="${BACKUP_DIR}/cutegoals_backup_${TIMESTAMP}.tar.gz"
BACKUP_CHECKSUM="${BACKUP_ARCHIVE}.sha256"

# ── 错误码 ────────────────────────────────────────────────────────────────
EC_SUCCESS=0
EC_BACKUP_FAILED=1
EC_UPLOAD_FAILED=2

# ── 解析参数 ──────────────────────────────────────────────────────────────
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
    local entry="[${level}] ${TIMESTAMP} ${message}"
    echo "${entry}" | tee -a "${LOG_FILE}"
}

write_backup_run() {
    local status="$1"
    local size="$2"
    local checksum="$3"
    local error_msg="$4"

    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] 写入 backup_run: status=${status}, size=${size}, checksum=${checksum:0:16}..."
        return
    fi

    # 通过后端 API 记录备份状态
    local json
    json=$(cat <<EOF
{
    "startedAt": "${START_TIME}",
    "completedAt": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
    "status": "${status}",
    "sizeBytes": ${size},
    "checksum": "${checksum}",
    "appVersion": "${APP_VERSION}",
    "schemaVersion": "flyway-managed"
}
EOF
)
    # Try API - if it fails that's OK (server might not have the endpoint yet)
    curl -sf -X POST "${SERVER_URL}/api/admin/backup/record" \
        -H "Content-Type: application/json" \
        -d "${json}" > /dev/null 2>&1 || log "WARN" "无法通过 API 记录备份状态（服务可能不支持该端点）"
}

cleanup_temp() {
    rm -f "${BACKUP_FILE}" "${BACKUP_CHECKSUM}"
}

# ── 初始化 ────────────────────────────────────────────────────────────────

# 确保备份目录存在（在首次日志输出前创建）
mkdir -p "${BACKUP_DIR}" 2>/dev/null || true

# ── 主流程 ────────────────────────────────────────────────────────────────

log "INFO" "=== 开始数据库备份 ==="
log "INFO" "数据库: ${PG_DATABASE} @ ${PG_HOST}:${PG_PORT}"
log "INFO" "备份目录: ${BACKUP_DIR}"
log "INFO" "应用版本: ${APP_VERSION}"

if [[ "${DRY_RUN}" == "true" ]]; then
    log "INFO" "--- DRY RUN 模式 ---"
fi

# 2. 执行 pg_dump（不包含密码输出）
log "INFO" "执行 pg_dump..."
export PGPASSWORD="${PG_PASSWORD}"

if [[ "${DRY_RUN}" == "true" ]]; then
    log "INFO" "[DRY-RUN] pg_dump -h ${PG_HOST} -p ${PG_PORT} -U ${PG_USER} -d ${PG_DATABASE} --schema=${PG_SCHEMA} --format=custom"
else
    if ! pg_dump \
        -h "${PG_HOST}" \
        -p "${PG_PORT}" \
        -U "${PG_USER}" \
        -d "${PG_DATABASE}" \
        --schema="${PG_SCHEMA}" \
        --format=custom \
        --file="${BACKUP_FILE}" \
        2>> "${LOG_FILE}"; then
        log "ERROR" "pg_dump 失败！"
        write_backup_run "FAILED" 0 "" "pg_dump execution failed"
        cleanup_temp
        exit "${EC_BACKUP_FAILED}"
    fi
fi

# 3. 计算校验和
log "INFO" "计算备份校验和..."
CHECKSUM=""
if [[ "${DRY_RUN}" == "true" ]]; then
    CHECKSUM="dry-run-skip-checksum"
else
    CHECKSUM=$(sha256sum "${BACKUP_FILE}" | cut -d' ' -f1)
fi

# 4. 打包为 tar.gz
log "INFO" "打包备份文件..."
if [[ "${DRY_RUN}" == "true" ]]; then
    log "INFO" "[DRY-RUN] tar -czf ${BACKUP_ARCHIVE} -C ${BACKUP_DIR} $(basename ${BACKUP_FILE})"
else
    # 写入校验和文件
    echo "${CHECKSUM}  $(basename "${BACKUP_FILE}")" > "${BACKUP_CHECKSUM}"

    # 创建归档（包含 SQL 文件和校验和）
    tar -czf "${BACKUP_ARCHIVE}" \
        -C "${BACKUP_DIR}" \
        "$(basename "${BACKUP_FILE}")" \
        "$(basename "${BACKUP_CHECKSUM}")" \
        2>> "${LOG_FILE}"
fi

# 5. 获取备份文件大小
BACKUP_SIZE=0
if [[ "${DRY_RUN}" == "true" ]]; then
    BACKUP_SIZE=0
else
    BACKUP_SIZE=$(stat -c%s "${BACKUP_ARCHIVE}" 2>/dev/null || echo 0)
fi

# 6. 清理临时文件
cleanup_temp

# 7. 记录成功
log "INFO" "备份完成！"
log "INFO" "  文件: ${BACKUP_ARCHIVE}"
log "INFO" "  大小: $(( BACKUP_SIZE / 1024 / 1024 )) MB"
log "INFO" "  校验和: ${CHECKSUM:0:16}..."
log "INFO" "  数据库密码未记录在日志中"

write_backup_run "SUCCESS" "${BACKUP_SIZE}" "${CHECKSUM}" ""

# 8. 执行保留策略清理
log "INFO" "执行备份保留清理..."
if [[ -x "${SCRIPT_DIR}/prune.sh" ]]; then
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] 跳过 prune.sh"
    else
        "${SCRIPT_DIR}/prune.sh" || log "WARN" "保留清理未完全完成"
    fi
else
    log "INFO" "prune.sh 不存在，跳过保留清理"
fi

log "INFO" "=== 备份完成 (状态: SUCCESS) ==="
exit "${EC_SUCCESS}"
