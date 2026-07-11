#!/usr/bin/env bash
# =============================================================================
# CuteGoals 2.0 — 数据库恢复脚本
# 校验备份 → 停止写流量 → 恢复 PostgreSQL → 启动 → 健康检查
# 恢复演练结果可选写入 recovery_drill 表
# 用法：deploy/restore.sh <备份文件> [--drill] [--dry-run]
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# ── 配置 ──────────────────────────────────────────────────────────────────
PG_HOST="${PG_HOST:-mit-modelide-core-postgres}"
PG_PORT="${PG_PORT:-5432}"
PG_DATABASE="${PG_DATABASE:-cutegoals}"
PG_USER="${PG_USER:-cutegoals}"
PG_PASSWORD="${PG_PASSWORD:-}"
PG_SCHEMA="${PG_SCHEMA:-cutegoals}"
BACKUP_DIR="${BACKUP_DIR:-/backup}"
SERVER_URL="${SERVER_URL:-http://mit-modelide-core-server:8080}"
RESTORE_LOG="${BACKUP_DIR}/restore.log"

# ── 常量 ──────────────────────────────────────────────────────────────────
EC_SUCCESS=0
EC_BACKUP_INVALID=1
EC_RESTORE_FAILED=2

# ── 状态 ──────────────────────────────────────────────────────────────────
DRY_RUN=false
DRILL_MODE=false
RESTORE_START_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
RESTORE_TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# ── 解析参数 ──────────────────────────────────────────────────────────────
if [[ $# -eq 0 ]]; then
    echo "用法: $0 <备份文件路径> [--drill] [--dry-run]"
    echo "  备份文件可以是 .tar.gz 或 .sql 格式"
    echo "  --drill    恢复演练模式（记录结果到 recovery_drill 表）"
    echo "  --dry-run  仅校验，不执行实际恢复"
    exit 1
fi

BACKUP_FILE="$1"
shift

while [[ $# -gt 0 ]]; do
    case "$1" in
        --drill) DRILL_MODE=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# ── 辅助函数 ──────────────────────────────────────────────────────────────

log() {
    local level="$1"
    local message="$2"
    echo "[${level}] $(date +%Y%m%d_%H%M%S) ${message}" | tee -a "${RESTORE_LOG}"
}

die() {
    log "ERROR" "$1"
    exit "${EC_RESTORE_FAILED}"
}

load_env() {
    if [[ -f "${ENV_FILE}" ]]; then
        set -a
        source "${ENV_FILE}"
        set +a
    fi
}

# ── 校验备份 ──────────────────────────────────────────────────────────────

validate_backup() {
    log "INFO" "=== 校验备份文件 ==="
    log "INFO" "备份文件: ${BACKUP_FILE}"

    if [[ ! -f "${BACKUP_FILE}" ]]; then
        log "ERROR" "备份文件不存在: ${BACKUP_FILE}"
        return 1
    fi

    local file_ext="${BACKUP_FILE##*.}"

    case "${file_ext}" in
        "gz"|"gzip")
            log "INFO" "检测到 tar.gz 格式归档"

            # 校验 SHA256（如果存在校验文件）
            local sha256_file="${BACKUP_FILE}.sha256"
            if [[ -f "${sha256_file}" ]]; then
                log "INFO" "发现校验文件，验证完整性..."
                if ! (cd "$(dirname "${BACKUP_FILE}")" && sha256sum -c "$(basename "${sha256_file}")" > /dev/null 2>&1); then
                    log "ERROR" "校验和不匹配！备份文件可能已损坏"
                    return 1
                fi
                log "INFO" "校验和验证通过"
            else
                log "WARN" "未找到校验文件，尝试解压验证..."
            fi

            # 验证 tar 内容
            if ! tar -tzf "${BACKUP_FILE}" > /dev/null 2>&1; then
                log "ERROR" "tar 归档格式无效或已损坏"
                return 1
            fi
            log "INFO" "tar 归档验证通过"

            # 检查是否包含 .sql 文件
            if ! tar -tzf "${BACKUP_FILE}" 2>/dev/null | grep -q '\.sql$'; then
                log "ERROR" "归档中未找到 SQL 文件"
                return 1
            fi
            ;;

        "sql")
            log "INFO" "检测到纯 SQL 格式备份"
            # 检查文件头
            if ! head -c 100 "${BACKUP_FILE}" | grep -q "PostgreSQL\|pg_dump\|--"; then
                log "WARN" "文件头未包含 PostgreSQL 标识，可能不是有效备份"
            fi
            ;;

        "custom"|"dump")
            log "INFO" "检测到自定义格式备份"
            if ! head -c 10 "${BACKUP_FILE}" | grep -q "PGDMP"; then
                log "WARN" "文件头未包含 PGDMP 标识，可能不是 pg_dump 自定义格式"
            fi
            ;;

        *)
            log "ERROR" "不受支持的备份格式: ${file_ext}"
            return 1
            ;;
    esac

    log "INFO" "备份文件校验通过"
    return 0
}

# ── 提取备份 ──────────────────────────────────────────────────────────────

extract_backup() {
    local file_ext="${BACKUP_FILE##*.}"

    if [[ "${file_ext}" == "gz" || "${file_ext}" == "gzip" ]]; then
        log "INFO" "解压 tar.gz 归档..."
        tar -xzf "${BACKUP_FILE}" -C "${BACKUP_DIR}"

        # 查找解压后的 SQL 文件
        local extracted_sql
        extracted_sql=$(find "${BACKUP_DIR}" -name "*.sql" -newer "${BACKUP_FILE}" 2>/dev/null | head -1)
        if [[ -n "${extracted_sql}" ]]; then
            echo "${extracted_sql}"
        else
            # 尝试找同目录的 SQL
            local base_name
            base_name=$(basename "${BACKUP_FILE}" .tar.gz)
            if [[ -f "${BACKUP_DIR}/${base_name}" ]]; then
                echo "${BACKUP_DIR}/${base_name}"
            else
                echo "${BACKUP_FILE}"
            fi
        fi
    else
        echo "${BACKUP_FILE}"
    fi
}

# ── 恢复数据库 ────────────────────────────────────────────────────────────

restore_database() {
    local sql_file="$1"

    log "INFO" "开始恢复 PostgreSQL 数据库..."
    log "INFO" "目标: ${PG_DATABASE} @ ${PG_HOST}:${PG_PORT}"

    export PGPASSWORD="${PG_PASSWORD}"

    # 创建数据库（如果不存在）
    log "INFO" "确保数据库和 schema 存在..."
    psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d postgres \
        -c "CREATE DATABASE ${PG_DATABASE};" 2>/dev/null || true
    psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DATABASE}" \
        -c "CREATE SCHEMA IF NOT EXISTS ${PG_SCHEMA};" 2>/dev/null || true

    # 恢复
    log "INFO" "执行 pg_restore..."
    if ! pg_restore \
        -h "${PG_HOST}" \
        -p "${PG_PORT}" \
        -U "${PG_USER}" \
        -d "${PG_DATABASE}" \
        --schema="${PG_SCHEMA}" \
        --clean \
        --if-exists \
        --no-owner \
        --no-privileges \
        "${sql_file}" \
        2>> "${RESTORE_LOG}"; then
        log "ERROR" "pg_restore 失败！"
        return 1
    fi

    log "INFO" "数据库恢复完成"
    return 0
}

# ── 健康检查 ──────────────────────────────────────────────────────────────

wait_for_health() {
    local retries=30
    local wait=10

    log "INFO" "等待服务健康检查通过..."

    for i in $(seq 1 "${retries}"); do
        if curl -sf "${SERVER_URL}/api/health" > /dev/null 2>&1; then
            log "INFO" "服务健康检查通过"
            return 0
        fi
        log "INFO" "等待中... (${i}/${retries})"
        sleep "${wait}"
    done

    log "ERROR" "服务健康检查超时"
    return 1
}

# ── 写入恢复演练记录 ──────────────────────────────────────────────────────

write_drill_record() {
    local success="$1"
    local rpo_seconds="$2"
    local rto_seconds="$3"
    local details="$4"

    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] 写入 recovery_drill 记录"
        return
    fi

    local json
    json=$(cat <<EOF
{
    "startedAt": "${RESTORE_START_TIME}",
    "completedAt": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
    "success": ${success},
    "rpoSeconds": ${rpo_seconds},
    "rtoSeconds": ${rto_seconds},
    "backupUsed": "$(basename "${BACKUP_FILE}")",
    "details": "${details}"
}
EOF
)

    curl -sf -X POST "${SERVER_URL}/api/admin/recovery-drill/record" \
        -H "Content-Type: application/json" \
        -d "${json}" > /dev/null 2>&1 || log "WARN" "无法记录恢复演练结果"
}

# ── 主流程 ────────────────────────────────────────────────────────────────

main() {
    load_env

    mkdir -p "${BACKUP_DIR}"
    log "INFO" "========================================"
    log "INFO" "  CuteGoals 数据库恢复"
    log "INFO" "========================================"
    log "INFO" "备份文件: ${BACKUP_FILE}"
    log "INFO" "演练模式: ${DRILL_MODE}"
    log "INFO" "Dry-Run: ${DRY_RUN}"

    # 1. 校验备份
    if ! validate_backup; then
        log "ERROR" "备份校验失败，终止恢复"
        echo "RESTORE_BACKUP_INVALID: 备份文件无效或已损坏，不会覆盖现有数据" | tee -a "${RESTORE_LOG}"
        exit "${EC_BACKUP_INVALID}"
    fi

    if [[ "${DRY_RUN}" == "true" ]]; then
        log "INFO" "[DRY-RUN] 备份校验通过，跳过实际恢复"
        log "INFO" "[DRY-RUN] 在不覆盖数据的情况下，按以下步骤操作："
        log "INFO" "[DRY-RUN]   1. 停止写流量到数据库"
        log "INFO" "[DRY-RUN]   2. 执行 pg_restore --clean"
        log "INFO" "[DRY-RUN]   3. 重启服务"
        log "INFO" "[DRY-RUN]   4. 健康检查"
        exit "${EC_SUCCESS}"
    fi

    # 2. 提取备份
    local sql_file
    sql_file=$(extract_backup)

    # 3. 停止写流量（通过停止 server 容器）
    log "INFO" "停止服务（暂停写流量）..."
    docker stop mit-modelide-core-server 2>/dev/null || true

    # 4. 恢复数据库
    if ! restore_database "${sql_file}"; then
        log "ERROR" "数据库恢复失败"
        docker start mit-modelide-core-server 2>/dev/null || true
        die "RESTORE_FAILED: 数据库恢复阶段失败"
    fi

    # 5. 启动服务
    log "INFO" "启动服务..."
    docker start mit-modelide-core-server 2>/dev/null || true

    # 6. 健康检查
    if ! wait_for_health; then
        die "RESTORE_FAILED: 恢复后健康检查失败"
    fi

    # 7. 记录结果
    local rto=0
    local current_epoch
    local start_epoch
    current_epoch=$(date +%s)
    start_epoch=$(date -d "${RESTORE_START_TIME}" +%s 2>/dev/null || date -j -f "%Y-%m-%dT%H:%M:%SZ" "${RESTORE_START_TIME}" +%s 2>/dev/null || echo 0)
    rto=$(( current_epoch - start_epoch ))

    log "INFO" "=== 恢复成功 ==="
    log "INFO" "  实际 RTO: ${rto} 秒"
    log "INFO" "  备份: $(basename "${BACKUP_FILE}")"
    log "INFO" "  时间: $(date)"

    if [[ "${DRILL_MODE}" == "true" ]]; then
        write_drill_record "true" "0" "${rto}" "Restore drill completed successfully"
        log "INFO" "恢复演练结果已记录"
    fi

    exit "${EC_SUCCESS}"
}

main
