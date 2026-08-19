import { useAuth } from '@shared/auth';

/* ── 孩子端数据类型（与后端契约一致，重构不改变）────────────────────── */

export interface ChildAssignment {
  id: number;
  childId: number;
  templateId: number;
  difficultyId: number;
  status: string;
  deadline: string;
  snapshotTemplateName: string;
  snapshotDifficultyReward: number;
  snapshotTemplateDescription?: string;
  snapshotTemplateTaskType?: string;
  overdue: boolean;
  rejectionReason?: string;
  version?: number;
  canSubmit: boolean;
  cancelled: boolean;
  submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null;
  snapshotTemplateAllowResubmit: boolean | null;
  snapshotTemplateMaxSubmissions: number | null;
  snapshotTemplatePointsCap: number | null;
}

export interface Prize {
  id: number;
  name: string;
  description: string;
  pointsCost: number;
  stock: number;
}

export interface PageResult<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export interface BlindBox {
  id: number;
  name: string;
  cost: number;
  availabilityVersion: string;
}

export interface BlindBoxCandidate {
  prizeId: number;
  prizeName: string;
  probability: number;
}

export interface Exchange {
  id: number;
  childId: number;
  familyId: number;
  type: 'DIRECT' | 'BLIND_BOX';
  status: string;
  costPoints: number;
  idempotencyKey: string;
  prizeId: number | null;
  poolId: number | null;
  resultPrizeId: number | null;
  fulfilledAt: string | null;
  fulfilledBy: number | null;
  cancelledAt: string | null;
  cancelledBy: number | null;
  createdAt: string;
  updatedAt: string;
  targetName: string;
}

export interface BlindBoxResult {
  prizeId: number;
  prizeName: string;
}

/* ── 共享工具（行为契约保持不变）───────────────────────────────────── */

export function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Map API status values to Chinese labels */
export function statusLabel(s: string): string {
  const map: Record<string, string> = {
    completed: '已完成', approved: '已通过', rejected: '已驳回',
    cancelled: '已取消', active: '启用', disabled: '停用',
    pending: '待处理', locked: '已锁定', success: '成功', failed: '失败',
    submitted: '已提交',
  };
  return map[s?.toLowerCase()] ?? s;
}

/** 兑换状态 → 中文标签 + 颜色（与家长端保持一致） */
export const EXCHANGE_STATUS_META: Record<string, { label: string; color: 'lemon' | 'mint' | 'muted' }> = {
  PENDING_FULFILLMENT: { label: '待核销', color: 'lemon' },
  FULFILLED: { label: '已核销', color: 'mint' },
  CANCELLED: { label: '已取消', color: 'muted' },
};

export function useChildId(): number | undefined {
  const { account } = useAuth();
  const id = account?.childId ?? account?.accountId;
  return typeof id === 'number' ? id : undefined;
}
