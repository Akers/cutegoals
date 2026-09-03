/**
 * 家长端页面共享工具（从旧版 React 控制台 parent/pages/index.tsx 移植）。
 */

/** 任务类型（与 @/types/api.ts 的 TaskType 一致） */
export type ParentTaskType = 'LIMITED' | 'REPEAT' | 'STANDING';

/** Map API status values to Chinese labels（旧版 statusLabel） */
export function statusLabel(s: string): string {
  const map: Record<string, string> = {
    completed: '已完成',
    approved: '已通过',
    rejected: '已驳回',
    cancelled: '已取消',
    active: '启用',
    disabled: '停用',
    pending: '待处理',
    locked: '已锁定',
    success: '成功',
    failed: '失败',
  };
  return map[s?.toLowerCase()] ?? s;
}

/** 手机号脱敏：138****1234（旧版 maskPhone） */
export function maskPhone(phone?: string | null): string {
  if (!phone || phone.length < 7) return phone ?? '';
  return phone.slice(0, 3) + '****' + phone.slice(-4);
}

/**
 * 根据任务类型的快照字段生成显示文本（旧版 repeatTaskLabel）。
 * REPEAT 任务显示「重复任务，每天/每周/每月」；其他任务返回 null（由调用方显示截止日期）。
 */
export function repeatTaskLabel(taskType?: string | null, typeConfig?: unknown): string | null {
  if (taskType === 'REPEAT') {
    const freq = readTypeConfig(typeConfig).frequency;
    if (freq === 'DAILY') return '重复任务，每天';
    if (freq === 'WEEKLY') return '重复任务，每周';
    if (freq === 'MONTHLY') return '重复任务，每月';
    if (freq === 'YEARLY') return '重复任务，每年';
    return '重复任务';
  }
  return null;
}

/**
 * REPEAT 任务进度文本：「提交 {当前}/{上限} · 积分 {已获}/{上限}」。
 * 上限为 null 或 0 时渲染「不限」（旧版 formatRepeatProgress）。
 */
export function formatRepeatProgress(task: {
  approvedSubmissionCount?: number | null;
  earnedPoints?: number | null;
  snapshotTemplateMaxSubmissions: number | null;
  snapshotTemplatePointsCap: number | null;
}): string {
  const current = task.approvedSubmissionCount ?? 0;
  const earned = task.earnedPoints ?? 0;
  const max = task.snapshotTemplateMaxSubmissions;
  const cap = task.snapshotTemplatePointsCap;
  const maxLabel = max === null || max === 0 ? '不限' : String(max);
  const capLabel = cap === null || cap === 0 ? '不限' : String(cap);
  return `提交 ${current}/${maxLabel} · 积分 ${earned}/${capLabel}`;
}

/**
 * typeConfig 在线上是 JSON 字符串（server PrizeService.extractString / 旧控制台 JSON.parse），
 * 但 batch-2 类型声明为 Record —— 此处做兼容解析，字符串/对象均支持。
 */
export function readTypeConfig(typeConfig?: unknown): Record<string, unknown> {
  if (!typeConfig) return {};
  if (typeof typeConfig === 'string') {
    try {
      const parsed = JSON.parse(typeConfig);
      return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : {};
    } catch {
      return {};
    }
  }
  if (typeof typeConfig === 'object') return typeConfig as Record<string, unknown>;
  return {};
}

/** 兑换状态 → 中文标签 + Tag 类型（旧版 EXCHANGE_STATUS_META） */
export const EXCHANGE_STATUS_META: Record<
  string,
  { label: string; tagType: 'warning' | 'success' | 'default' }
> = {
  PENDING_FULFILLMENT: { label: '待核销', tagType: 'warning' },
  FULFILLED: { label: '已核销', tagType: 'success' },
  CANCELLED: { label: '已取消', tagType: 'default' },
};

/** 虚拟奖品分类标签（旧版 labels） */
export const PRIZE_CATEGORY_LABELS: Record<string, string> = {
  TV_TIME: '电视时长卡',
  COMPUTER_TIME: '电脑时长卡',
  PARK_PLAY: '公园游玩卡',
  GENERAL: '通用',
  TRAVEL: '旅游卡',
};
