#!/usr/bin/env bash
# =============================================================================
# CuteGoals 2.0 — 备份保留清理脚本
# 保留策略：最近 7 天每日备份 + 最近 4 周每周备份
# 在新备份验证成功后才删除超期备份
# 用法：prune.sh [--dry-run]
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-/backup}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
RETENTION_WEEKS="${BACKUP_RETENTION_WEEKS:-4}"
RETENTION_MONTHS="${BACKUP_RETENTION_MONTHS:-3}"
LOG_FILE="${BACKUP_DIR}/backup.log"
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# 确保日志目录存在
mkdir -p "${BACKUP_DIR}" 2>/dev/null || true

log() {
    local level="$1"
    local message="$2"
    echo "[${level}] $(date +%Y%m%d_%H%M%S) ${message}" | tee -a "${LOG_FILE}"
}

log "INFO" "=== 开始备份保留清理 ==="

# 确保备份目录存在
if [[ ! -d "${BACKUP_DIR}" ]]; then
    log "WARN" "备份目录 ${BACKUP_DIR} 不存在，跳过清理"
    exit 0
fi

# 1. 检查是否有有效的最新备份（防止删光所有备份）
LATEST_BACKUP=$(find "${BACKUP_DIR}" -name "cutegoals_backup_*.tar.gz" -type f 2>/dev/null | sort | tail -1)
if [[ -z "${LATEST_BACKUP}" ]]; then
    log "INFO" "没有找到备份文件，跳过清理"
    exit 0
fi

# 验证最新备份的完整性
log "INFO" "验证最新备份: $(basename "${LATEST_BACKUP}")"
SHA256_FILE="${LATEST_BACKUP}.sha256"
if [[ -f "${SHA256_FILE}" ]]; then
    if ! sha256sum -c "${SHA256_FILE}" > /dev/null 2>&1; then
        log "ERROR" "最新备份校验失败，跳过清理以保护数据"
        exit 1
    fi
    log "INFO" "校验通过"
else
    # 如果没有校验文件，也尝试解压验证
    if ! tar -tzf "${LATEST_BACKUP}" > /dev/null 2>&1; then
        log "ERROR" "最新备份损坏，跳过清理"
        exit 1
    fi
    log "INFO" "压缩文件验证通过（无校验文件）"
fi

log "INFO" "最新备份验证成功，开始清理过期备份"

# 2. 列出所有备份文件（按时间排序）
ALL_BACKUPS=$(find "${BACKUP_DIR}" -name "cutegoals_backup_*.tar.gz" -type f | sort)

# 3. 每日保留策略：保留最近 N 天
log "INFO" "每日保留策略：保留最近 ${RETENTION_DAYS} 天"
DAILY_CUTOFF=$(date -d "-${RETENTION_DAYS} days" +%Y%m%d 2>/dev/null || \
               date -j -v-${RETENTION_DAYS}d +%Y%m%d 2>/dev/null || \
               echo "unknown")

if [[ "${DAILY_CUTOFF}" != "unknown" ]]; then
    for backup in ${ALL_BACKUPS}; do
        # 从文件名提取日期 (cutegoals_backup_YYYYMMDD_HHMMSS.tar.gz)
        BASENAME=$(basename "${backup}")
        BACKUP_DATE="${BASENAME#cutegoals_backup_}"
        BACKUP_DATE="${BACKUP_DATE:0:8}"

        if [[ "${BACKUP_DATE}" -lt "${DAILY_CUTOFF}" ]]; then
            # 检查该周的备份是否应保留为"每周备份"
            BACKUP_WEEK=$(date -d "${BACKUP_DATE}" +%V 2>/dev/null || echo "")
            if [[ -n "${BACKUP_WEEK}" ]]; then
                # 如果此备份是该周的最新备份且周数在保留范围内，跳过
                WEEK_START=$(date -d "${BACKUP_DATE}" +%Y%W 2>/dev/null || echo "")
                CURRENT_WEEK=$(date +%Y%W 2>/dev/null || echo "")
                WEEK_DIFF=$(( (CURRENT_WEEK - WEEK_START) ))
                if [[ "${WEEK_DIFF}" -le "${RETENTION_WEEKS}" ]] && [[ "${WEEK_DIFF}" -ge 1 ]]; then
                    # 检查是否是本周的最新备份
                    WEEK_BACKUPS=$(find "${BACKUP_DIR}" -name "cutegoals_backup_${BACKUP_DATE:0:6}*.tar.gz" -type f | sort)
                    LATEST_IN_WEEK=$(echo "${WEEK_BACKUPS}" | tail -1)
                    if [[ "${backup}" == "${LATEST_IN_WEEK}" ]]; then
                        log "INFO" "  保留每周备份: $(basename "${backup}") (第 ${BACKUP_WEEK} 周)"
                        continue
                    fi
                fi
            fi

            if [[ "${DRY_RUN}" == "true" ]]; then
                log "INFO" "  [DRY-RUN] 删除过期备份: $(basename "${backup}")"
            else
                log "INFO" "  删除过期备份: $(basename "${backup}")"
                rm -f "${backup}" "${backup}.sha256"
            fi
        fi
    done
fi

log "INFO" "=== 清理完成 ==="
exit 0
